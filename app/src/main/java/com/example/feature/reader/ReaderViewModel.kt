package com.example.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioPlaybackState
import com.example.core.audio.HifzLoopConfig
import com.example.core.audio.QuranAudioController
import com.example.core.database.AudioReciterEntity
import com.example.core.database.AyahEntity
import com.example.core.database.AyahTafsirEntity
import com.example.core.database.AyahTranslationEntity
import com.example.core.database.HabitStreakEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.database.ReadingProgressEntity
import com.example.core.database.SurahEntity
import com.example.core.database.TafsirSourceEntity
import com.example.core.database.UserBookmarkEntity
import com.example.core.database.UserNoteEntity
import com.example.core.database.WordEntity
import com.example.core.database.WordOccurrenceProjection
import com.example.core.ibadah.StreakCalculator
import com.example.core.localization.BidiUtils
import com.example.core.localization.DetailedWordAnalysis
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.QuranScriptType
import com.example.core.localization.ReaderViewMode
import com.example.core.localization.UserPreferences
import com.example.core.localization.WordMorphologyAnalyzer
import com.example.core.network.QuranSyncRepository
import com.example.ui.components.WordDisplayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AyahDisplayBundle(
    val ayah: AyahEntity,
    val words: List<WordDisplayItem>,
    val translations: List<AyahTranslationEntity>,
    val isBookmarked: Boolean,
    val hasNote: Boolean
)

data class WordInspectorState(
    val word: WordEntity,
    val localizedMeaning: String,
    val ayahVerseKey: String,
    val surahName: String,
    val rootOccurrencesCount: Int,
    val rootOccurrences: List<WordOccurrenceProjection>,
    val analysis: DetailedWordAnalysis
)

data class ActivePlaybackCursor(
    val surahId: Int = 0,
    val ayahNumber: Int = 0,
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false
)

data class ReaderUiState(
    val surah: SurahEntity? = null,
    val allSurahs: List<SurahEntity> = emptyList(),
    val localizedSurahName: String = "",
    val ayahs: List<AyahDisplayBundle> = emptyList(),
    val isSurahIncomplete: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val playbackState: AudioPlaybackState = AudioPlaybackState(),
    val reciters: List<AudioReciterEntity> = emptyList(),
    val tafsirSources: List<TafsirSourceEntity> = emptyList(),
    val selectedTafsirAyah: AyahEntity? = null,
    val selectedAyahTafsirs: List<AyahTafsirEntity> = emptyList(),
    val selectedAyahNoteText: String = "",
    val isFetchingTafsir: Boolean = false,
    val tafsirError: String? = null,
    val isDownloadingSurah: Boolean = false,
    val downloadError: String? = null,
    val inspectedWord: WordInspectorState? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    initialSurahId: Int,
    private val database: NoorDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val syncRepository: QuranSyncRepository,
    private val audioController: QuranAudioController,
    private val seeder: QuranDatabaseSeeder? = null
) : ViewModel() {

    private val quranDao = database.quranDao()
    private val userLibraryDao = database.userLibraryDao()

    private val _surahId = MutableStateFlow(initialSurahId.coerceIn(1, 114))
    private val _selectedTafsirAyah = MutableStateFlow<AyahEntity?>(null)
    private val _isFetchingTafsir = MutableStateFlow(false)
    private val _tafsirError = MutableStateFlow<String?>(null)
    private val _isDownloadingSurah = MutableStateFlow(false)
    private val _downloadError = MutableStateFlow<String?>(null)
    private val _inspectedWord = MutableStateFlow<WordInspectorState?>(null)
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var assetLoadJob: kotlinx.coroutines.Job? = null

    val playbackState: StateFlow<AudioPlaybackState> = audioController.state
    val playbackCursor: StateFlow<ActivePlaybackCursor> = audioController.state
        .map {
            ActivePlaybackCursor(
                surahId = it.surahId,
                ayahNumber = it.ayahNumber,
                isVisible = it.isVisible,
                isPlaying = it.isPlaying
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivePlaybackCursor())

    private val bundlesFlow = _surahId.flatMapLatest { sid ->
        combine(
            quranDao.observeAyahsBySurah(sid),
            quranDao.observeWordsBySurah(sid),
            quranDao.observeWordTranslationsBySurah(sid),
            quranDao.observeAyahTranslationsBySurah(sid),
            combine(
                preferencesRepository.preferencesFlow,
                quranDao.observeEnabledTranslationSources(),
                userLibraryDao.observeBookmarks(),
                userLibraryDao.observeNotes()
            ) { prefs, enabledSources, bookmarks, notes ->
                Pair(prefs to enabledSources, bookmarks to notes)
            }
        ) { ayahs, allWords, wordTrs, allTranslations, meta ->
            if (ayahs.isEmpty()) {
                emptyList()
            } else {
                val (prefs, enabledSources) = meta.first
                val (bookmarks, notes) = meta.second
                val enabledIds = enabledSources.map { it.id }.toSet()
                val wordsByAyah = allWords.groupBy { it.ayahId }
                val trsByAyah = allTranslations.groupBy { it.ayahId }
                val wordTrById = wordTrs.groupBy { it.wordId }
                val bookmarkedKeys = bookmarks.map { it.verseKey }.toSet()
                val noteKeys = notes.map { it.verseKey }.toSet()

                ayahs.map { ayah ->
                    val wList = wordsByAyah[ayah.id].orEmpty().map { w ->
                        val wTrs = wordTrById[w.id].orEmpty()
                        val meaning = wTrs.firstOrNull { it.languageCode == prefs.wbwLanguageCode }?.meaningText
                            ?: wTrs.firstOrNull { it.languageCode == "en" }?.meaningText
                            ?: wTrs.firstOrNull()?.meaningText
                            ?: ""
                        WordDisplayItem(w, meaning)
                    }
                    val ayahTrs = trsByAyah[ayah.id].orEmpty()
                        .filter { enabledIds.isEmpty() || it.sourceId in enabledIds }
                        .ifEmpty { trsByAyah[ayah.id].orEmpty() }
                    AyahDisplayBundle(
                        ayah = ayah,
                        words = wList,
                        translations = ayahTrs,
                        isBookmarked = ayah.verseKey in bookmarkedKeys,
                        hasNote = ayah.verseKey in noteKeys
                    )
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    private val selectedTafsirsFlow = _selectedTafsirAyah.flatMapLatest { ayah ->
        if (ayah == null) flowOf(emptyList()) else quranDao.observeTafsirsForAyah(ayah.id)
    }

    private val selectedNoteFlow = _selectedTafsirAyah.flatMapLatest { ayah ->
        if (ayah == null) flowOf(null) else userLibraryDao.observeNoteForVerse(ayah.verseKey)
    }

    private val catalogFlow = combine(
        quranDao.observeAllSurahs(),
        quranDao.observeReciters(),
        quranDao.observeTafsirSources()
    ) { surahs, reciters, tafsirs ->
        Triple(surahs, reciters, tafsirs)
    }

    private val overlayFlow = combine(
        _selectedTafsirAyah,
        selectedTafsirsFlow,
        selectedNoteFlow,
        _isFetchingTafsir,
        _tafsirError,
        _isDownloadingSurah,
        _downloadError,
        _inspectedWord
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        Triple(
            arr[0] as AyahEntity?,
            arr[1] as List<AyahTafsirEntity>,
            arr[2] as UserNoteEntity?
        ) to Triple(
            (arr[3] as Boolean) to (arr[4] as String?),
            (arr[5] as Boolean) to (arr[6] as String?),
            arr[7] as WordInspectorState?
        )
    }

    private val readerCoreFlow = combine(
        _surahId.flatMapLatest { quranDao.observeSurah(it) },
        bundlesFlow,
        preferencesRepository.preferencesFlow
    ) { surah, bundles, prefs ->
        Triple(surah, bundles, prefs)
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        readerCoreFlow,
        catalogFlow,
        overlayFlow
    ) { core, catalog, overlay ->
        val (surah, bundles, prefs) = core
        val (allSurahs, reciters, tafsirSources) = catalog
        val (selectedTriple, flags) = overlay
        val (selectedAyah, selectedTafsirs, selectedNote) = selectedTriple
        val (tafsirStatus, downloadStatus, inspected) = flags
        val (fetchingTafsir, tafErr) = tafsirStatus
        val (downloadingSurah, dlErr) = downloadStatus

        val localizedName = surah?.let {
            BidiUtils.extractLocalizedSurahName(it.localizedNamesJson, prefs.uiLanguageCode)
                .ifBlank { it.nameTransliteration }
        } ?: "Surah ${_surahId.value}"

        val isIncomplete = surah != null && bundles.size < surah.ayahCount

        ReaderUiState(
            surah = surah,
            allSurahs = allSurahs,
            localizedSurahName = localizedName,
            ayahs = bundles,
            isSurahIncomplete = isIncomplete,
            preferences = prefs,
            playbackState = AudioPlaybackState(),
            reciters = reciters,
            tafsirSources = tafsirSources,
            selectedTafsirAyah = selectedAyah,
            selectedAyahTafsirs = selectedTafsirs,
            selectedAyahNoteText = selectedNote?.noteText.orEmpty(),
            isFetchingTafsir = fetchingTafsir,
            tafsirError = tafErr,
            isDownloadingSurah = downloadingSurah,
            downloadError = dlErr,
            inspectedWord = inspected
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderUiState()
        )

    fun loadSurah(surahId: Int) {
        val targetId = surahId.coerceIn(1, 114)
        _surahId.value = targetId
        _downloadError.value = null
        ensureLocalSurahLoaded(targetId)
    }

    private fun ensureLocalSurahLoaded(surahId: Int) {
        assetLoadJob?.cancel()
        assetLoadJob = viewModelScope.launch {
            val surah = quranDao.getSurah(surahId)
            val existingCount = quranDao.countAyahsInSurah(surahId)
            val isMissingLocally = surah == null || (surah.isDownloaded && existingCount < surah.ayahCount)
            if (isMissingLocally) {
                seeder?.ensureSurahLoadedFromAssets(surahId, surah?.ayahCount)
            }
        }
    }

    fun recordAyahRead(ayah: AyahEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existingProgress = userLibraryDao.getReadingProgress() ?: ReadingProgressEntity()
            val updatedProgress = existingProgress.copy(
                lastSurahId = ayah.surahId,
                lastAyahNumber = ayah.ayahNumber,
                lastVerseKey = ayah.verseKey,
                lastPageNumber = ayah.pageNumber,
                lastJuzNumber = ayah.juzNumber,
                khatamCompletedAyahs = (existingProgress.khatamCompletedAyahs + 1).coerceAtMost(6236),
                updatedAtEpochMs = now
            )
            userLibraryDao.upsertReadingProgress(updatedProgress)

            val todayKey = StreakCalculator.todayDateKey(now)
            val existingStreak = userLibraryDao.getStreakByDate(todayKey) ?: HabitStreakEntity(dateKey = todayKey)
            val updatedStreak = existingStreak.copy(
                ayahsReadCount = existingStreak.ayahsReadCount + 1,
                updatedAtEpochMs = now
            )
            userLibraryDao.upsertStreak(updatedStreak)
        }
    }

    fun downloadCurrentSurah() {
        val sid = _surahId.value
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadSurahById(sid)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _isDownloadingSurah.value = false
    }

    private suspend fun downloadSurahById(sid: Int): Boolean {
        _isDownloadingSurah.value = true
        _downloadError.value = null
        return try {
            val prefs = preferencesRepository.preferencesFlow.first()
            val surah = quranDao.getSurah(sid)
            val needsBaseText = surah == null || quranDao.countAyahsInSurah(sid) < surah.ayahCount
            if (needsBaseText && seeder?.ensureSurahLoadedFromAssets(sid, surah?.ayahCount) == true) {
                // Restored complete offline Arabic + English WBW + Saheeh International instantly.
                // Also try syncing extra user-selected translations/WBW language silently if online.
                val enabledIds = quranDao.getAllTranslationSources()
                    .filter { it.isEnabled && it.id != 20 }
                    .map { it.id }
                if (enabledIds.isNotEmpty() || prefs.wbwLanguageCode != "en") {
                    syncRepository.downloadSurahForOffline(
                        surahId = sid,
                        translationIds = (enabledIds + 20).distinct(),
                        wbwLanguages = listOf(prefs.wbwLanguageCode),
                        reciterId = prefs.activeReciterId
                    )
                }
                return true
            }

            val enabledIds = quranDao.getAllTranslationSources()
                .filter { it.isEnabled }
                .map { it.id }
                .ifEmpty { listOf(20, 161) }
            val res = syncRepository.downloadSurahForOffline(
                surahId = sid,
                translationIds = enabledIds,
                wbwLanguages = listOf(prefs.wbwLanguageCode),
                reciterId = prefs.activeReciterId
            )
            res.onFailure { err ->
                _downloadError.value = err.localizedMessage ?: "Failed to fetch Surah from Quran.com API"
            }
            res.isSuccess
        } finally {
            _isDownloadingSurah.value = false
        }
    }

    fun playAyah(ayah: AyahEntity) {
        val s = uiState.value
        val reciter = s.reciters.firstOrNull { it.id == s.preferences.activeReciterId }
            ?: AudioReciterEntity(7, "Mishary Rashid Alafasy", "مشاري العفاسي", "Murattal", "", true)
        audioController.playAyah(
            surahId = ayah.surahId,
            ayahNumber = ayah.ayahNumber,
            surahName = s.localizedSurahName,
            maxAyahInSurah = s.surah?.ayahCount ?: s.ayahs.size.coerceAtLeast(1),
            reciterId = reciter.id,
            reciterName = reciter.name
        )
        recordAyahRead(ayah)
    }

    fun inspectWord(item: WordDisplayItem, verseKey: String) {
        viewModelScope.launch {
            val allTranslations = quranDao.getWordTranslations(item.word.id)
            val analysis = WordMorphologyAnalyzer.analyze(item.word, allTranslations)
            val occurrences = if (item.word.rootArabic.isNotBlank()) {
                quranDao.getRootOccurrences(item.word.rootArabic)
            } else {
                emptyList()
            }
            val totalOccurrencesCount = if (item.word.rootArabic.isNotBlank()) {
                quranDao.countWordsByRoot(item.word.rootArabic).coerceAtLeast(occurrences.size)
            } else {
                1
            }
            _inspectedWord.value = WordInspectorState(
                word = item.word,
                localizedMeaning = item.localizedMeaning,
                ayahVerseKey = verseKey,
                surahName = uiState.value.localizedSurahName,
                rootOccurrencesCount = totalOccurrencesCount,
                rootOccurrences = occurrences,
                analysis = analysis
            )
            if (preferencesRepository.preferencesFlow.first().autoPlayWordOnInspect) {
                playInspectedWord(uiState.value.preferences.audioPlaybackSpeed)
            }
        }
    }

    fun dismissWordInspector() {
        _inspectedWord.value = null
    }

    fun playInspectedWord(speed: Float = 1.0f) {
        val inspected = _inspectedWord.value ?: return
        val parts = inspected.ayahVerseKey.split(":")
        val sId = parts.getOrNull(0)?.toIntOrNull() ?: _surahId.value
        val aNum = parts.getOrNull(1)?.toIntOrNull() ?: 1
        audioController.playSingleWord(
            wordAudioUrl = inspected.word.audioUrl,
            fallbackSurahId = sId,
            fallbackAyah = aNum,
            wordPosition = inspected.word.position,
            speed = speed
        )
    }

    fun toggleBookmark(ayah: AyahEntity) {
        viewModelScope.launch {
            val existing = userLibraryDao.getBookmarkByVerseKey(ayah.verseKey)
            if (existing != null) {
                userLibraryDao.deleteBookmark(existing.id)
            } else {
                val now = System.currentTimeMillis()
                val bookmark = UserBookmarkEntity(
                    id = "bm_${ayah.verseKey.replace(':', '_')}",
                    verseKey = ayah.verseKey,
                    surahId = ayah.surahId,
                    ayahNumber = ayah.ayahNumber,
                    collectionId = "favorites",
                    label = "${uiState.value.localizedSurahName} ${ayah.verseKey}",
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                userLibraryDao.insertBookmark(bookmark)
            }
        }
    }

    fun openTafsir(ayah: AyahEntity) {
        _tafsirError.value = null
        _selectedTafsirAyah.value = ayah
    }

    fun closeTafsir() {
        _selectedTafsirAyah.value = null
        _tafsirError.value = null
    }

    fun selectTafsirSource(source: TafsirSourceEntity) {
        val ayah = _selectedTafsirAyah.value ?: return
        viewModelScope.launch {
            preferencesRepository.setActiveTafsirSourceId(source.id)
            _isFetchingTafsir.value = true
            _tafsirError.value = null
            val res = syncRepository.fetchAyahTafsirIfMissing(
                ayahId = ayah.id,
                verseKey = ayah.verseKey,
                tafsirId = source.id,
                languageCode = source.languageCode,
                tafsirName = source.tafsirName
            )
            res.onFailure { err ->
                _tafsirError.value = err.localizedMessage ?: "Could not fetch Tafsir offline"
            }
            _isFetchingTafsir.value = false
        }
    }

    fun saveNoteForSelectedAyah(noteText: String) {
        val ayah = _selectedTafsirAyah.value ?: return
        val trimmed = noteText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val note = UserNoteEntity(
                id = "note_${ayah.verseKey.replace(':', '_')}",
                verseKey = ayah.verseKey,
                surahId = ayah.surahId,
                ayahNumber = ayah.ayahNumber,
                noteText = trimmed,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            userLibraryDao.insertNote(note)
        }
    }

    fun toggleViewMode(mode: ReaderViewMode) = viewModelScope.launch {
        preferencesRepository.setReaderViewMode(mode)
    }

    fun setScriptType(script: QuranScriptType) = viewModelScope.launch {
        preferencesRepository.setScriptType(script)
    }

    fun setTajweedEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setTajweedEnabled(enabled)
    }

    fun setShowTransliteration(show: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowTransliteration(show)
    }

    fun setArabicFontSize(sizeSp: Float) = viewModelScope.launch {
        preferencesRepository.setArabicFontSize(sizeSp)
    }

    fun setWbwFontSize(sizeSp: Float) = viewModelScope.launch {
        preferencesRepository.setWbwFontSize(sizeSp)
    }

    fun setTranslationFontSize(sizeSp: Float) = viewModelScope.launch {
        preferencesRepository.setTranslationFontSize(sizeSp)
    }

    fun togglePlayPause() = audioController.togglePlayPause()
    fun previousAyah() = audioController.skipToPreviousAyah()
    fun nextAyah() = audioController.skipToNextAyah()
    fun seekAudioTo(positionMs: Long) = audioController.seekTo(positionMs)
    fun dismissAudioBar() = audioController.stopAndDismiss()
    fun updateHifzConfig(transform: (HifzLoopConfig) -> HifzLoopConfig) = audioController.updateHifzConfig(transform)
    fun setSleepTimerMinutes(minutes: Int) = audioController.setSleepTimerMinutes(minutes)
    fun downloadSurahAudio(surahId: Int, reciterId: Int) = audioController.downloadSurahAudioForQari(surahId, reciterId)
    fun deleteSurahAudio(surahId: Int) = audioController.deleteSurahAudio(surahId)
    fun clearAudioCache() = audioController.clearOfflineAudioCache()
    fun deleteCurrentSurah() {
        val sid = _surahId.value
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _isDownloadingSurah.value = true
            syncRepository.deleteSurahOffline(sid)
            _isDownloadingSurah.value = false
        }
    }
    fun setAutoPlayWordOnInspect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoPlayWordOnInspect(enabled) }
    }
    fun selectReciter(reciter: AudioReciterEntity) {
        viewModelScope.launch { preferencesRepository.setActiveReciterId(reciter.id) }
        audioController.updateReciter(reciter.id, reciter.name)
    }
}
