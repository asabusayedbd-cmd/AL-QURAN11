package com.example.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.QuranAudioController
import com.example.core.database.AudioReciterEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.database.SurahEntity
import com.example.core.database.TafsirSourceEntity
import com.example.core.database.TranslationSourceEntity
import com.example.core.localization.BidiUtils
import com.example.core.localization.LineTranslator
import com.example.core.localization.NoorThemeMode
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.QuranScriptType
import com.example.core.localization.ReaderCardStyle
import com.example.core.localization.UserPreferences
import com.example.core.network.QuranSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BatchDownloadProgress(
    val current: Int,
    val total: Int,
    val surahName: String
)

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val offlineCacheSizeBytes: Long = 0L,
    val sleepRemainingSec: Int? = null,
    val translationSources: List<TranslationSourceEntity> = emptyList(),
    val tafsirSources: List<TafsirSourceEntity> = emptyList(),
    val reciters: List<AudioReciterEntity> = emptyList(),
    val surahs: List<SurahEntity> = emptyList(),
    val statusMessage: String? = null,
    val busySourceId: Int? = null,
    val busySurahId: Int? = null,
    val isRefreshingTafsirs: Boolean = false,
    val batchDownloadProgress: BatchDownloadProgress? = null,
    val downloadedAudioSurahIds: Set<Int> = emptySet()
)

class SettingsViewModel(
    private val database: NoorDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val syncRepository: QuranSyncRepository,
    private val audioController: QuranAudioController? = null,
    private val seeder: QuranDatabaseSeeder? = null,
    private val lineTranslator: LineTranslator? = null,
    private val onSyncLanguageContent: ((String) -> Unit)? = null
) : ViewModel() {

    private val quranDao = database.quranDao()
    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _busySourceId = MutableStateFlow<Int?>(null)
    private val _busySurahId = MutableStateFlow<Int?>(null)
    private val _isRefreshingTafsirs = MutableStateFlow(false)
    private val _batchDownloadProgress = MutableStateFlow<BatchDownloadProgress?>(null)
    private val _downloadedAudioSurahIds = MutableStateFlow<Set<Int>>(emptySet())
    private var batchDownloadJob: Job? = null
    private var wbwSyncJob: Job? = null

    private val fallbackAudioSummaryFlow = MutableStateFlow(0L to (null as Int?))
    private val audioSummaryFlow = (
        audioController?.state
            ?.map { it.offlineCacheSizeBytes to it.sleepRemainingSec }
            ?.distinctUntilChanged()
            ?: fallbackAudioSummaryFlow
        )

    private val catalogsFlow = combine(
        quranDao.observeTranslationSources(),
        quranDao.observeTafsirSources(),
        quranDao.observeReciters(),
        quranDao.observeAllSurahs()
    ) { tr, taf, rec, sur ->
        Pair(tr to taf, rec to sur)
    }

    private val statusFlow = combine(
        _statusMessage,
        _busySourceId,
        _busySurahId,
        _isRefreshingTafsirs,
        _batchDownloadProgress
    ) { msg, busySrc, busySurah, refreshingTaf, batchProgress ->
        Triple(msg to busySrc, busySurah to refreshingTaf, batchProgress)
    }

    init {
        refreshAudioSurahIds()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.preferencesFlow,
        audioSummaryFlow,
        catalogsFlow,
        statusFlow,
        _downloadedAudioSurahIds
    ) { prefs, audioSummary, catalogs, statuses, audioSurahs ->
        val (trSources, tafSources) = catalogs.first
        val (reciters, surahs) = catalogs.second
        val (msg, busySrc) = statuses.first
        val (busySurah, refreshingTaf) = statuses.second
        val batchProgress = statuses.third
        val (cacheBytes, sleepRem) = audioSummary

        SettingsUiState(
            preferences = prefs,
            offlineCacheSizeBytes = cacheBytes,
            sleepRemainingSec = sleepRem,
            translationSources = trSources,
            tafsirSources = tafSources,
            reciters = reciters,
            surahs = surahs,
            statusMessage = msg,
            busySourceId = busySrc,
            busySurahId = busySurah,
            isRefreshingTafsirs = refreshingTaf,
            batchDownloadProgress = batchProgress,
            downloadedAudioSurahIds = audioSurahs
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun refreshAudioSurahIds() {
        viewModelScope.launch(Dispatchers.IO) {
            val audioSet = audioController?.getDownloadedAudioSurahIds() ?: emptySet()
            _downloadedAudioSurahIds.value = audioSet
        }
    }

    fun setUiLanguage(languageCode: String) {
        val normLang = languageCode.lowercase().trim()
        val primaryTrId = when (normLang) {
            "bn" -> 161
            "ur" -> 97
            "id" -> 33
            "tr" -> 77
            "fr" -> 31
            "es" -> 83
            "de" -> 27
            "ru" -> 45
            "hi" -> 122
            "fa" -> 29
            else -> 20
        }
        viewModelScope.launch {
            preferencesRepository.setUiLanguage(normLang)
            quranDao.setTranslationEnabled(primaryTrId, true)
            _statusMessage.value = "Language set to ${LineTranslator.languageLabel(normLang)}. Translation, WBW & Duas updated."
            if (onSyncLanguageContent != null) {
                onSyncLanguageContent.invoke(normLang)
            } else if (primaryTrId != 20 && quranDao.countTranslationsForSource(primaryTrId) < 6000) {
                _busySourceId.value = primaryTrId
                syncRepository.downloadTranslationPack(primaryTrId, lineTranslator)
                _busySourceId.value = null
            }
        }
        if (onSyncLanguageContent == null && !normLang.equals("en", ignoreCase = true)) {
            wbwSyncJob?.cancel()
            wbwSyncJob = viewModelScope.launch(Dispatchers.IO) {
                val lastSurah = database.userLibraryDao().getReadingProgress()?.lastSurahId ?: 1
                val priority = listOf(lastSurah, 1, 112, 113, 114).distinct()
                for (sid in priority) {
                    syncRepository.ensureWbwForSurah(sid, normLang, lineTranslator)
                }
            }
        }
    }

    fun setWbwLanguage(languageCode: String) {
        viewModelScope.launch {
            preferencesRepository.setWbwLanguage(languageCode)
            _statusMessage.value = "Word-by-word language set to ${LineTranslator.languageLabel(languageCode)}"
        }
        if (!languageCode.equals("en", ignoreCase = true)) {
            wbwSyncJob?.cancel()
            wbwSyncJob = viewModelScope.launch(Dispatchers.IO) {
                val lastSurah = database.userLibraryDao().getReadingProgress()?.lastSurahId ?: 1
                val priority = listOf(lastSurah, 1, 112, 113, 114).distinct()
                for (sid in priority) {
                    syncRepository.ensureWbwForSurah(sid, languageCode, lineTranslator)
                }
            }
        }
    }

    fun setThemeMode(mode: NoorThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setReaderCardStyle(style: ReaderCardStyle) {
        viewModelScope.launch {
            preferencesRepository.setReaderCardStyle(style)
        }
    }

    fun setScriptType(script: QuranScriptType) {
        viewModelScope.launch {
            preferencesRepository.setScriptType(script)
        }
    }

    fun toggleTranslationSource(source: TranslationSourceEntity, enabled: Boolean) {
        viewModelScope.launch {
            quranDao.setTranslationEnabled(source.id, enabled)
            if (enabled && source.id != 20) {
                val count = quranDao.countTranslationsForSource(source.id)
                if (!source.isDownloaded || count < 6000) {
                    downloadTranslationPack(source.id)
                } else {
                    _statusMessage.value = "${source.translatorName} enabled"
                }
            } else if (enabled) {
                _statusMessage.value = "${source.translatorName} enabled"
            }
        }
    }

    fun downloadTranslationPack(sourceId: Int) {
        viewModelScope.launch {
            _busySourceId.value = sourceId
            val res = syncRepository.downloadTranslationPack(sourceId, lineTranslator)
            _statusMessage.value = res.fold(
                onSuccess = { "Translation pack downloaded and ready offline" },
                onFailure = { "Download error: ${it.localizedMessage}" }
            )
            _busySourceId.value = null
        }
    }

    fun deleteTranslationPack(sourceId: Int) {
        viewModelScope.launch {
            _busySourceId.value = sourceId
            val res = syncRepository.deleteTranslationPack(sourceId)
            _statusMessage.value = res.fold(
                onSuccess = { "Deleted translation offline cache" },
                onFailure = { "Delete error: ${it.localizedMessage}" }
            )
            _busySourceId.value = null
        }
    }

    fun downloadSurahOffline(surahId: Int) {
        viewModelScope.launch {
            _busySurahId.value = surahId
            val prefs = uiState.value.preferences
            val restoredLocal = seeder?.ensureSurahLoadedFromAssets(surahId) == true
            val res = syncRepository.downloadSurahForOffline(
                surahId = surahId,
                wbwLanguages = listOf(prefs.wbwLanguageCode),
                reciterId = prefs.activeReciterId
            )
            _statusMessage.value = if (res.isSuccess) {
                "Surah $surahId ready offline (${res.getOrNull()} Ayahs)"
            } else if (restoredLocal) {
                "Surah $surahId Arabic & English WBW restored offline"
            } else {
                "Failed: ${res.exceptionOrNull()?.localizedMessage}"
            }
            refreshAudioSurahIds()
            _busySurahId.value = null
        }
    }

    fun deleteSurahOffline(surahId: Int) {
        viewModelScope.launch {
            _busySurahId.value = surahId
            val res = syncRepository.deleteSurahOffline(surahId)
            _statusMessage.value = res.fold(
                onSuccess = { "Cleared Surah $surahId audio cache — Mushaf text is protected" },
                onFailure = { "Failed: ${it.localizedMessage}" }
            )
            refreshAudioSurahIds()
            _busySurahId.value = null
        }
    }

    fun downloadAllSurahs() {
        batchDownloadJob?.cancel()
        batchDownloadJob = viewModelScope.launch {
            val allSurahs = quranDao.observeAllSurahs().first()
            val toDownload = allSurahs.filter { !it.isDownloaded }
            if (toDownload.isEmpty()) {
                _statusMessage.value = "All 114 Surahs are already downloaded!"
                return@launch
            }
            val total = toDownload.size
            var completed = 0
            val prefs = uiState.value.preferences
            for (surah in toDownload) {
                _batchDownloadProgress.value = BatchDownloadProgress(
                    current = completed + 1,
                    total = total,
                    surahName = BidiUtils.formatSurahTitle(
                        surah.nameTransliteration,
                        surah.localizedNamesJson,
                        prefs.uiLanguageCode
                    )
                )
                _busySurahId.value = surah.id
                seeder?.ensureSurahLoadedFromAssets(surah.id, surah.ayahCount)
                if (prefs.wbwLanguageCode != "en") {
                    syncRepository.downloadSurahForOffline(
                        surahId = surah.id,
                        wbwLanguages = listOf(prefs.wbwLanguageCode),
                        reciterId = prefs.activeReciterId
                    )
                }
                completed++
            }
            _busySurahId.value = null
            _batchDownloadProgress.value = null
            _statusMessage.value = "Downloaded $completed Surahs. Complete Quran is ready offline!"
            refreshAudioSurahIds()
        }
    }

    fun cancelBatchDownload() {
        batchDownloadJob?.cancel()
        batchDownloadJob = null
        _batchDownloadProgress.value = null
        _busySurahId.value = null
        _statusMessage.value = "Batch download cancelled"
    }

    fun deleteAllDownloadedSurahs() {
        viewModelScope.launch {
            val res = syncRepository.deleteAllDownloadedSurahs()
            _statusMessage.value = res.fold(
                onSuccess = { "Cleared offline audio cache — all 114 Mushaf Surahs remain protected" },
                onFailure = { "Failed: ${it.localizedMessage}" }
            )
            refreshAudioSurahIds()
        }
    }

    fun downloadSurahAudio(surahId: Int, reciterId: Int) {
        audioController?.downloadSurahAudioForQari(surahId, reciterId)
        _statusMessage.value = "Downloading audio for Surah $surahId..."
    }

    fun deleteSurahAudio(surahId: Int, reciterId: Int? = null) {
        audioController?.deleteSurahAudio(surahId, reciterId)
        _statusMessage.value = "Deleted audio for Surah $surahId"
        refreshAudioSurahIds()
    }

    fun deleteReciterAudio(reciterId: Int) {
        audioController?.deleteReciterAudio(reciterId)
        _statusMessage.value = "Deleted reciter audio cache"
        refreshAudioSurahIds()
    }

    fun setActiveReciter(reciter: AudioReciterEntity) {
        viewModelScope.launch {
            preferencesRepository.setActiveReciterId(reciter.id)
            audioController?.updateReciter(reciter.id, reciter.name)
            _statusMessage.value = "Default Qari set to ${reciter.name}"
            refreshAudioSurahIds()
        }
    }

    fun setAudioWordByWordMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAudioWordByWordMode(enabled)
            audioController?.updateHifzConfig { it.copy(wordByWordMode = enabled) }
        }
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.setAudioPlaybackSpeed(speed)
            audioController?.updateHifzConfig { it.copy(playbackSpeed = speed) }
        }
    }

    fun setAudioRepeatPerAyah(repeatCount: Int) {
        viewModelScope.launch {
            preferencesRepository.setAudioRepeatPerAyah(repeatCount)
            audioController?.updateHifzConfig { it.copy(repeatPerAyah = repeatCount) }
        }
    }

    fun setAudioPauseSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setAudioPauseSeconds(seconds)
            audioController?.updateHifzConfig { it.copy(pauseSecondsAfterAyah = seconds) }
        }
    }

    fun setAutoPlayNextSurah(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoPlayNextSurah(enabled)
            audioController?.updateHifzConfig { it.copy(autoPlayNextSurah = enabled) }
        }
    }

    fun setAutoPlayWordOnInspect(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoPlayWordOnInspect(enabled)
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        audioController?.setSleepTimerMinutes(minutes)
        _statusMessage.value = if (minutes > 0) "Sleep timer set for $minutes min" else "Sleep timer turned off"
    }

    fun clearOfflineAudioCache() {
        audioController?.clearOfflineAudioCache()
        _statusMessage.value = "Offline audio cache cleared"
        refreshAudioSurahIds()
    }

    fun selectDefaultTafsirSource(source: TafsirSourceEntity) {
        viewModelScope.launch {
            preferencesRepository.setActiveTafsirSourceId(source.id)
            _statusMessage.value = "Default Tafsir set to ${source.tafsirName}"
        }
    }

    fun downloadTafsirPack(source: TafsirSourceEntity) {
        viewModelScope.launch {
            _busySourceId.value = source.id
            val targetSurahs = quranDao.getDownloadedSurahIds().ifEmpty { listOf(1, 103, 108, 112, 113, 114) }
            for (sid in targetSurahs) {
                val ayahs = quranDao.getAyahsBySurah(sid)
                for (a in ayahs.take(15)) {
                    syncRepository.fetchAyahTafsirIfMissing(a.id, a.verseKey, source.id, source.languageCode, source.tafsirName)
                }
            }
            quranDao.updateTafsirSourceStatus(source.id, downloaded = true, enabled = true)
            _busySourceId.value = null
            _statusMessage.value = "Tafsir ${source.tafsirName} cached offline"
        }
    }

    fun deleteTafsirPack(sourceId: Int) {
        viewModelScope.launch {
            _busySourceId.value = sourceId
            val res = syncRepository.deleteTafsirPack(sourceId)
            _statusMessage.value = res.fold(
                onSuccess = { "Deleted Tafsir offline cache" },
                onFailure = { "Delete error: ${it.localizedMessage}" }
            )
            _busySourceId.value = null
        }
    }

    fun syncRemoteTafsirCatalog() {
        viewModelScope.launch {
            _isRefreshingTafsirs.value = true
            val res = syncRepository.refreshRemoteTafsirCatalog()
            _statusMessage.value = res.fold(
                onSuccess = { count -> "Synced $count Tafsirs from Quran.com catalog" },
                onFailure = { err -> "Could not refresh catalog: ${err.localizedMessage}" }
            )
            _isRefreshingTafsirs.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
