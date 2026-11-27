package com.example.feature.dua

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.DuaEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.localization.LineTranslator
import com.example.core.localization.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class DuaAudioUiState(
    val downloadedIds: Set<Int> = emptySet(),
    val downloadingIds: Set<Int> = emptySet(),
    val activePlayingId: Int? = null,
    val isTtsSpeakingId: Int? = null,
    val statusMessage: String? = null
)

data class TranslatedDuaFields(
    val title: String,
    val translation: String,
    val virtueOrContext: String
)

data class DuaTranslationUiState(
    val activeLanguage: String? = null,
    val translatedById: Map<Int, Pair<String, TranslatedDuaFields>> = emptyMap(),
    val translatingIds: Set<Int> = emptySet(),
    val isBatchTranslating: Boolean = false,
    val errorMessage: String? = null
)

class DuaViewModel(
    context: Context,
    private val database: NoorDatabase,
    private val okHttpClient: OkHttpClient,
    private val seeder: QuranDatabaseSeeder? = null,
    private val lineTranslator: LineTranslator? = null,
    private val preferencesRepository: PreferencesRepository? = null
) : ViewModel() {
    private val appContext = context.applicationContext
    private val duaDao = database.duaDao()
    private val audioDir = File(appContext.filesDir, "audio/duas").apply { mkdirs() }

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _audioState = MutableStateFlow(DuaAudioUiState())
    private val _translationState = MutableStateFlow(DuaTranslationUiState())
    private var batchTranslateJob: kotlinx.coroutines.Job? = null
    private var autoTranslateJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var manualOverrideLang: String? = null

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    val categories = flowOf(
        listOf(
            "All",
            "Bookmarked",
            "Morning & Evening",
            "Sleeping & Waking",
            "Ruqyah & Shifa",
            "Salah & Worship",
            "Anxiety & Hardship",
            "Forgiveness & Tawbah",
            "Daily Life & Travel",
            "Family & Offspring",
            "Protection & Safety",
            "Provision & Debt",
            "Gratitude & Praise",
            "Rabbana Quranic Duas"
        )
    )

    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val audioState: StateFlow<DuaAudioUiState> = _audioState.asStateFlow()
    val translationState: StateFlow<DuaTranslationUiState> = _translationState.asStateFlow()

    val duas: StateFlow<List<DuaEntity>> = combine(
        duaDao.observeAllDuas(),
        _selectedCategory,
        _searchQuery
    ) { allDuas, cat, query ->
        allDuas.filter { dua ->
            val matchesCategory = when {
                cat.isNullOrBlank() || cat == "All" -> true
                cat == "Bookmarked" -> dua.isBookmarked
                else -> dua.category.equals(cat, ignoreCase = true)
            }
            val matchesQuery = query.isBlank() ||
                dua.title.contains(query, ignoreCase = true) ||
                dua.translation.contains(query, ignoreCase = true) ||
                dua.transliteration.contains(query, ignoreCase = true) ||
                dua.arabicText.contains(query, ignoreCase = true) ||
                dua.reference.contains(query, ignoreCase = true) ||
                dua.category.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        refreshDownloadedAudioIds()
        viewModelScope.launch {
            seeder?.ensureDuasSeededNow()
        }
        val prefsRepo = preferencesRepository
        val translator = lineTranslator
        if (prefsRepo != null && translator != null) {
            viewModelScope.launch {
                var lastUiLang: String? = null
                combine(
                    prefsRepo.preferencesFlow.map { it.uiLanguageCode.lowercase().trim() }.distinctUntilChanged(),
                    duas
                ) { uiLang, visibleDuas ->
                    uiLang to visibleDuas
                }.collectLatest { (uiLang, visibleDuas) ->
                    if (lastUiLang != null && lastUiLang != uiLang) {
                        manualOverrideLang = null
                    }
                    lastUiLang = uiLang
                    val effectiveLang = manualOverrideLang ?: uiLang
                    if (effectiveLang == "en") {
                        autoTranslateJob?.cancel()
                        if (manualOverrideLang == null && _translationState.value.activeLanguage != null) {
                            _translationState.update { DuaTranslationUiState() }
                        }
                        return@collectLatest
                    }
                    if (visibleDuas.isEmpty()) return@collectLatest
                    val pending = visibleDuas.filter { dua ->
                        _translationState.value.translatedById[dua.id]?.first != effectiveLang
                    }
                    if (pending.isEmpty()) {
                        _translationState.update { it.copy(activeLanguage = effectiveLang, isBatchTranslating = false) }
                        return@collectLatest
                    }
                    autoTranslateJob?.cancel()
                    autoTranslateJob = launch {
                        translateChunkedList(pending, effectiveLang, translator, reportError = false)
                    }
                }
            }
        }
    }

    fun translateDua(dua: DuaEntity, targetLang: String) {
        val translator = lineTranslator ?: return
        _translationState.update {
            it.copy(
                translatingIds = it.translatingIds + dua.id,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val res = translator.translateLinesJoined(
                listOf(dua.title, dua.translation, dua.virtueOrContext),
                targetLang
            )
            _translationState.update { state ->
                if (res.isSuccess) {
                    val parts = res.getOrThrow()
                    val entry = TranslatedDuaFields(
                        title = parts.getOrNull(0).orEmpty().ifBlank { dua.title },
                        translation = parts.getOrNull(1).orEmpty().ifBlank { dua.translation },
                        virtueOrContext = parts.getOrNull(2).orEmpty().ifBlank { dua.virtueOrContext }
                    )
                    state.copy(
                        translatedById = state.translatedById + (dua.id to (targetLang to entry)),
                        translatingIds = state.translatingIds - dua.id
                    )
                } else {
                    state.copy(
                        translatingIds = state.translatingIds - dua.id,
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                    )
                }
            }
        }
    }

    fun translateVisibleDuas(targetLang: String) {
        val translator = lineTranslator ?: return
        val visible = duas.value
        if (visible.isEmpty()) return
        manualOverrideLang = targetLang
        batchTranslateJob?.cancel()
        autoTranslateJob?.cancel()
        batchTranslateJob = viewModelScope.launch {
            translateChunkedList(visible, targetLang, translator, reportError = true)
        }
    }

    private suspend fun translateChunkedList(
        targets: List<DuaEntity>,
        targetLang: String,
        translator: LineTranslator,
        reportError: Boolean
    ) {
        _translationState.update {
            it.copy(
                activeLanguage = targetLang,
                isBatchTranslating = true,
                errorMessage = null
            )
        }
        try {
            for (chunk in targets.chunked(6)) {
                val inputs = chunk.flatMap { listOf(it.title, it.translation, it.virtueOrContext) }
                val res = translator.translateLinesJoined(inputs, targetLang)
                if (res.isSuccess) {
                    val parts = res.getOrThrow()
                    val updates = chunk.mapIndexed { idx, dua ->
                        val base = idx * 3
                        dua.id to (targetLang to TranslatedDuaFields(
                            title = parts.getOrNull(base).orEmpty().ifBlank { dua.title },
                            translation = parts.getOrNull(base + 1).orEmpty().ifBlank { dua.translation },
                            virtueOrContext = parts.getOrNull(base + 2).orEmpty().ifBlank { dua.virtueOrContext }
                        ))
                    }.toMap()
                    _translationState.update { state ->
                        state.copy(translatedById = state.translatedById + updates)
                    }
                } else {
                    _translationState.update {
                        it.copy(
                            isBatchTranslating = false,
                            errorMessage = if (reportError) res.exceptionOrNull()?.localizedMessage else it.errorMessage
                        )
                    }
                    return
                }
            }
            _translationState.update { it.copy(isBatchTranslating = false) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _translationState.update { it.copy(isBatchTranslating = false) }
            throw e
        }
    }

    fun clearDuaTranslation(duaId: Int) {
        _translationState.update { it.copy(translatedById = it.translatedById - duaId) }
    }

    fun clearAllDuaTranslations() {
        manualOverrideLang = "en"
        batchTranslateJob?.cancel()
        autoTranslateJob?.cancel()
        _translationState.update {
            DuaTranslationUiState()
        }
    }

    private fun duaAudioFile(duaId: Int): File = File(audioDir, "dua_${duaId}.mp3")

    private fun refreshDownloadedAudioIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = audioDir.listFiles()
                ?.mapNotNull { file ->
                    if (file.isFile && file.length() > 0L &&
                        file.name.startsWith("dua_") && file.name.endsWith(".mp3")
                    ) {
                        file.name.removePrefix("dua_").removeSuffix(".mp3").toIntOrNull()
                    } else {
                        null
                    }
                }
                ?.toSet()
                ?: emptySet()
            _audioState.update { it.copy(downloadedIds = ids) }
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearStatusMessage() {
        _audioState.update { it.copy(statusMessage = null) }
    }

    fun toggleBookmark(duaId: Int, currentStatus: Boolean) {
        viewModelScope.launch {
            duaDao.setDuaBookmarked(duaId, !currentStatus)
        }
    }

    fun downloadDuaAudio(dua: DuaEntity) {
        if (dua.audioUrl.isBlank()) return
        if (dua.id in _audioState.value.downloadingIds) return
        _audioState.update {
            it.copy(
                downloadingIds = it.downloadingIds + dua.id,
                statusMessage = null
            )
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(dua.audioUrl).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}")
                        }
                        val body = response.body ?: error("Empty response body")
                        val target = duaAudioFile(dua.id)
                        val tmp = File(audioDir, "dua_${dua.id}.mp3.tmp")
                        body.byteStream().use { input ->
                            FileOutputStream(tmp).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
            }
            _audioState.update { state ->
                val updatedIds = if (result.isSuccess) state.downloadedIds + dua.id else state.downloadedIds
                val msg = if (result.isFailure) {
                    "Audio download failed: ${result.exceptionOrNull()?.localizedMessage ?: "Check connection"}"
                } else {
                    null
                }
                state.copy(
                    downloadedIds = updatedIds,
                    downloadingIds = state.downloadingIds - dua.id,
                    statusMessage = msg
                )
            }
        }
    }

    fun deleteDuaAudio(duaId: Int) {
        if (_audioState.value.activePlayingId == duaId) {
            stopAudioPlayback()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = duaAudioFile(duaId)
            if (file.exists()) {
                file.delete()
            }
            _audioState.update { it.copy(downloadedIds = it.downloadedIds - duaId) }
        }
    }

    fun togglePlayDuaAudio(dua: DuaEntity) {
        val currentState = _audioState.value
        if (currentState.activePlayingId == dua.id || currentState.isTtsSpeakingId == dua.id) {
            stopAudioPlayback()
            return
        }

        stopAudioPlayback()

        if (dua.audioUrl.isNotBlank()) {
            val file = duaAudioFile(dua.id)
            if (file.exists() && file.length() > 0L) {
                playLocalMp3(dua.id, file)
            } else {
                downloadDuaAudio(dua)
            }
        } else {
            speakArabicTts(dua)
        }
    }

    private fun playLocalMp3(duaId: Int, file: File) {
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopAudioPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    stopAudioPlayback()
                    _audioState.update { it.copy(statusMessage = "Unable to play audio file.") }
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _audioState.update { it.copy(activePlayingId = duaId, isTtsSpeakingId = null, statusMessage = null) }
        } catch (e: Exception) {
            stopAudioPlayback()
            _audioState.update { it.copy(statusMessage = "Playback failed: ${e.localizedMessage}") }
        }
    }

    private fun speakArabicTts(dua: DuaEntity) {
        val engine = tts
        if (engine != null && ttsReady) {
            triggerTtsUtterance(engine, dua)
            return
        }
        _audioState.update { it.copy(isTtsSpeakingId = dua.id, statusMessage = null) }
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val instance = tts ?: return@TextToSpeech
                val langRes = instance.setLanguage(Locale.forLanguageTag("ar"))
                if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _audioState.update {
                        it.copy(
                            isTtsSpeakingId = null,
                            statusMessage = "Arabic device voice is unavailable on this device."
                        )
                    }
                } else {
                    ttsReady = true
                    com.example.core.audio.TtsVoiceSelector.configureMaleArabicTts(instance)
                    instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            _audioState.update { it.copy(isTtsSpeakingId = null) }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _audioState.update { it.copy(isTtsSpeakingId = null) }
                        }
                    })
                    triggerTtsUtterance(instance, dua)
                }
            } else {
                _audioState.update {
                    it.copy(
                        isTtsSpeakingId = null,
                        statusMessage = "Voice synthesis could not start."
                    )
                }
            }
        }
    }

    private fun triggerTtsUtterance(engine: TextToSpeech, dua: DuaEntity) {
        _audioState.update { it.copy(isTtsSpeakingId = dua.id, activePlayingId = null, statusMessage = null) }
        com.example.core.audio.TtsVoiceSelector.configureMaleArabicTts(engine)
        engine.speak(dua.arabicText, TextToSpeech.QUEUE_FLUSH, null, "dua_${dua.id}")
    }

    fun stopAudioPlayback() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching {
            tts?.stop()
        }
        _audioState.update { it.copy(activePlayingId = null, isTtsSpeakingId = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
