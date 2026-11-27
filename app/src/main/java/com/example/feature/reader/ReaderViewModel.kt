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
import com.example.core.database.TranslationSourceEntity
import com.example.core.database.UserBookmarkEntity
import com.example.core.database.UserNoteEntity
import com.example.core.database.WordEntity
import com.example.core.database.WordFormStatProjection
import com.example.core.database.WordOccurrenceProjection
import com.example.core.database.WordTranslationEntity
import com.example.core.ibadah.StreakCalculator
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.BidiUtils
import com.example.core.localization.DetailedWordAnalysis
import com.example.core.localization.LineTranslator
import com.example.core.localization.NoorThemeMode
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.QuranScriptType
import com.example.core.localization.ReaderCardStyle
import com.example.core.localization.ReaderViewMode
import com.example.core.localization.UserPreferences
import com.example.core.localization.WordMorphologyAnalyzer
import com.example.core.network.QuranSyncRepository
import com.example.ui.components.WordDisplayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AyahDisplayBundle(
    val ayah: AyahEntity,
    val words: List<WordDisplayItem>,
    val translations: List<AyahTranslationEntity>,
    val isBookmarked: Boolean,
    val hasNote: Boolean,
    val inlineTafsir: AyahTafsirEntity? = null
)

data class WordInspectorState(
    val word: WordEntity,
    val localizedMeaning: String,
    val ayahVerseKey: String,
    val surahName: String,
    val exactOccurrencesCount: Int = 1,
    val rootOccurrencesCount: Int,
    val otherForms: List<WordFormStatProjection> = emptyList(),
    val rootOccurrences: List<WordOccurrenceProjection>,
    val analysis: DetailedWordAnalysis,
    val translatedTargetLang: String? = null,
    val translatedMeaning: String? = null,
    val translatedExplanation: String? = null,
    val isTranslatingWord: Boolean = false,
    val wordTranslationError: String? = null
)

data class ActivePlaybackCursor(
    val surahId: Int = 0,
    val ayahNumber: Int = 0,
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false
)

data class LineTranslationUiState(
    val ayahLines: Map<Int, Pair<String, String>> = emptyMap(), // ayahId -> (langCode, text)
    val translatingAyahIds: Set<Int> = emptySet(),
    val activeBatchLanguage: String? = null,
    val isBatchTranslating: Boolean = false,
    val translatedTafsir: Pair<String, String>? = null, // (langCode, text)
    val isTranslatingTafsir: Boolean = false,
    val errorMessage: String? = null
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
    val translationSources: List<TranslationSourceEntity> = emptyList(),
    val busyTranslationSourceId: Int? = null,
    val tafsirSources: List<TafsirSourceEntity> = emptyList(),
    val isRefreshingTafsirs: Boolean = false,
    val isLoadingInlineTafsirs: Boolean = false,
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
    private val seeder: QuranDatabaseSeeder? = null,
    private val lineTranslator: LineTranslator? = null
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
    private val _busyTranslationSourceId = MutableStateFlow<Int?>(null)
    private val _isRefreshingTafsirs = MutableStateFlow(false)
    private val _isLoadingInlineTafsirs = MutableStateFlow(false)
    private val _lineTranslationState = MutableStateFlow(LineTranslationUiState())
    val lineTranslationState: StateFlow<LineTranslationUiState> = _lineTranslationState
    private var downloadJob: Job? = null
    private var assetLoadJob: Job? = null
    private var batchTranslateJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                _surahId,
                preferencesRepository.preferencesFlow.map { it.wbwLanguageCode }.distinctUntilChanged(),
                quranDao.observeEnabledTranslationSources()
            ) { sid, wbwLang, enabledSources ->
                Triple(sid, wbwLang, enabledSources)
            }.collectLatest { (sid, wbwLang, enabledSources) ->
                val surah = quranDao.getSurah(sid)
                val existingCount = quranDao.countAyahsInSurah(sid)
                if (surah == null || existingCount < surah.ayahCount) {
                    seeder?.ensureSurahLoadedFromAssets(sid, surah?.ayahCount)
                }
                if (!wbwLang.equals("en", ignoreCase = true)) {
                    syncRepository.ensureWbwForSurah(sid, wbwLang, lineTranslator)
                }
                if (enabledSources.any { it.id != 20 }) {
                    syncRepository.ensureTranslationsForSurah(sid, enabledSources, lineTranslator)
                }
            }
        }

        // Automatically fetch missing Tafsir entries for the active Surah whenever Inline Tafsir is enabled
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                _surahId,
                preferencesRepository.preferencesFlow.map { it.showInlineTafsir to it.activeTafsirSourceId }.distinctUntilChanged(),
                quranDao.observeTafsirSources()
            ) { sid, (showInline, tafsirId), sources ->
                Triple(sid, showInline to tafsirId, sources)
            }.collectLatest { (sid, inlinePair, sources) ->
                val (showInline, tafsirId) = inlinePair
                if (!showInline) {
                    _isLoadingInlineTafsirs.value = false
                    return@collectLatest
                }
                val source = sources.firstOrNull { it.id == tafsirId }
                    ?: TafsirSourceEntity(tafsirId, "en", "English", "Tafsir", false, true, 0)
                val ayahs = quranDao.getAyahsBySurah(sid)
                if (ayahs.isEmpty()) return@collectLatest
                _isLoadingInlineTafsirs.value = true
                try {
                    for (chunk in ayahs.chunked(4)) {
                        coroutineScope {
                            chunk.map { ayah ->
                                async {
                                    syncRepository.fetchAyahTafsirIfMissing(
                                        ayahId = ayah.id,
                                        verseKey = ayah.verseKey,
                                        tafsirId = source.id,
                                        languageCode = source.languageCode,
                                        tafsirName = source.tafsirName
                                    )
                                }
                            }.awaitAll()
                        }
                    }
                } finally {
                    _isLoadingInlineTafsirs.value = false
                }
            }
        }
    }

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

    private val surahAndBundlesFlow = _surahId.flatMapLatest { sid ->
        combine(
            quranDao.observeSurah(sid),
            quranDao.observeAyahsBySurah(sid),
            quranDao.observeWordsBySurah(sid),
            quranDao.observeWordTranslationsBySurah(sid),
            quranDao.observeAyahTranslationsBySurah(sid),
            combine(
                preferencesRepository.preferencesFlow,
                quranDao.observeEnabledTranslationSources(),
                userLibraryDao.observeBookmarkedKeysForSurah(sid),
                userLibraryDao.observeNoteKeysForSurah(sid)
            ) { prefs, enabledSources, bookmarkedKeys, noteKeys ->
                Pair(prefs to enabledSources, bookmarkedKeys.toHashSet() to noteKeys.toHashSet())
            }
        ) { arr ->
            val surah = arr[0] as SurahEntity?
            @Suppress("UNCHECKED_CAST")
            val ayahs = arr[1] as List<AyahEntity>
            @Suppress("UNCHECKED_CAST")
            val allWords = arr[2] as List<WordEntity>
            @Suppress("UNCHECKED_CAST")
            val wordTrs = arr[3] as List<WordTranslationEntity>
            @Suppress("UNCHECKED_CAST")
            val allTranslations = arr[4] as List<AyahTranslationEntity>
            @Suppress("UNCHECKED_CAST")
            val meta = arr[5] as Pair<Pair<UserPreferences, List<com.example.core.database.TranslationSourceEntity>>, Pair<HashSet<String>, HashSet<String>>>

            val (prefs, enabledSources) = meta.first
            val (bookmarkedKeys, noteKeys) = meta.second
            val bundles = if (ayahs.isEmpty()) {
                emptyList()
            } else {
                val enabledIds = enabledSources.map { it.id }.toSet()
                val sourceTitleMap = enabledSources.associate { it.id to it.translatorName }
                val wordsByAyah = allWords.groupBy { it.ayahId }
                val trsByAyah = allTranslations.groupBy { it.ayahId }
                val wordTrById = wordTrs.groupBy { it.wordId }

                ayahs.map { ayah ->
                    val wList = wordsByAyah[ayah.id].orEmpty().map { w ->
                        val wTrs = wordTrById[w.id].orEmpty()
                        val meaning = wTrs.firstOrNull { it.languageCode == prefs.wbwLanguageCode }?.meaningText
                            ?: wTrs.firstOrNull { it.languageCode == "en" }?.meaningText
                            ?: wTrs.firstOrNull()?.meaningText
                            ?: ""
                        WordDisplayItem(w, meaning)
                    }
                    val rawTrs = trsByAyah[ayah.id].orEmpty()
                    val ayahTrs = rawTrs
                        .filter { enabledIds.isEmpty() || it.sourceId in enabledIds }
                        .ifEmpty { rawTrs }
                        .map { tr ->
                            val canonicalTitle = sourceTitleMap[tr.sourceId]
                            if (!canonicalTitle.isNullOrBlank() && canonicalTitle != tr.translatorName) {
                                tr.copy(translatorName = canonicalTitle)
                            } else {
                                tr
                            }
                        }
                    AyahDisplayBundle(
                        ayah = ayah,
                        words = wList,
                        translations = ayahTrs,
                        isBookmarked = ayah.verseKey in bookmarkedKeys,
                        hasNote = ayah.verseKey in noteKeys
                    )
                }
            }
            Triple(surah, bundles, prefs)
        }
    }.flowOn(Dispatchers.Default)

    private val selectedTafsirsFlow = _selectedTafsirAyah.flatMapLatest { ayah ->
        if (ayah == null) flowOf(emptyList()) else quranDao.observeTafsirsForAyah(ayah.id)
    }

    private val selectedNoteFlow = _selectedTafsirAyah.flatMapLatest { ayah ->
        if (ayah == null) flowOf(null) else userLibraryDao.observeNoteForVerse(ayah.verseKey)
    }

    private val inlineTafsirsForSurahFlow = combine(
        _surahId,
        preferencesRepository.preferencesFlow.map { it.showInlineTafsir to it.activeTafsirSourceId }.distinctUntilChanged()
    ) { sid, (showInline, tafsirId) ->
        Triple(sid, showInline, tafsirId)
    }.flatMapLatest { (sid, showInline, tafsirId) ->
        if (!showInline) flowOf(emptyList())
        else quranDao.observeTafsirsForSurahBySource(sid, tafsirId)
    }

    private val catalogFlow = combine(
        quranDao.observeAllSurahs(),
        quranDao.observeReciters(),
        quranDao.observeTafsirSources(),
        quranDao.observeTranslationSources()
    ) { surahs, reciters, tafsirs, translationSources ->
        listOf(surahs, reciters, tafsirs, translationSources)
    }

    private val sidebarFlagsFlow = combine(
        _busyTranslationSourceId,
        _isRefreshingTafsirs,
        _isLoadingInlineTafsirs
    ) { busyTrId, refreshingTaf, loadingInlineTaf ->
        Triple(busyTrId, refreshingTaf, loadingInlineTaf)
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

    val uiState: StateFlow<ReaderUiState> = combine(
        surahAndBundlesFlow,
        catalogFlow,
        overlayFlow,
        inlineTafsirsForSurahFlow,
        sidebarFlagsFlow
    ) { core, catalog, overlay, inlineTafsirs, sidebarFlags ->
        val (surah, baseBundles, prefs) = core
        @Suppress("UNCHECKED_CAST")
        val allSurahs = catalog[0] as List<SurahEntity>
        @Suppress("UNCHECKED_CAST")
        val reciters = catalog[1] as List<AudioReciterEntity>
        @Suppress("UNCHECKED_CAST")
        val tafsirSources = catalog[2] as List<TafsirSourceEntity>
        @Suppress("UNCHECKED_CAST")
        val translationSources = catalog[3] as List<TranslationSourceEntity>

        val (selectedTriple, flags) = overlay
        val (selectedAyah, selectedTafsirs, selectedNote) = selectedTriple
        val (tafsirStatus, downloadStatus, inspected) = flags
        val (fetchingTafsir, tafErr) = tafsirStatus
        val (downloadingSurah, dlErr) = downloadStatus
        val (busyTrId, refreshingTaf, loadingInlineTaf) = sidebarFlags

        val bundles = if (prefs.showInlineTafsir && inlineTafsirs.isNotEmpty()) {
            val inlineByAyah = inlineTafsirs.associateBy { it.ayahId }
            baseBundles.map { b -> b.copy(inlineTafsir = inlineByAyah[b.ayah.id]) }
        } else {
            baseBundles
        }

        val localizedName = surah?.let {
            BidiUtils.formatSurahTitle(it.nameTransliteration, it.localizedNamesJson, prefs.uiLanguageCode)
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
            translationSources = translationSources,
            busyTranslationSourceId = busyTrId,
            tafsirSources = tafsirSources,
            isRefreshingTafsirs = refreshingTaf,
            isLoadingInlineTafsirs = loadingInlineTaf,
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
        if (_surahId.value != targetId) {
            batchTranslateJob?.cancel()
            _lineTranslationState.value = LineTranslationUiState()
        }
        _surahId.value = targetId
        _downloadError.value = null
        ensureLocalSurahLoaded(targetId)
    }

    private fun ensureLocalSurahLoaded(surahId: Int) {
        assetLoadJob?.cancel()
        assetLoadJob = viewModelScope.launch {
            val surah = quranDao.getSurah(surahId)
            val existingCount = quranDao.countAyahsInSurah(surahId)
            val isMissingLocally = surah == null || existingCount < surah.ayahCount
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
            val hasRoot = item.word.rootArabic.isNotBlank()
            val normalizedWord = item.word.normalizedText.ifBlank {
                BidiUtils.normalizeArabicForSearch(item.word.textUthmani)
            }
            val exactCount = quranDao.countWordsByArabic(item.word.textUthmani).coerceAtLeast(1)
            val normalizedCount = if (normalizedWord.isNotBlank()) {
                quranDao.countWordsByNormalized(normalizedWord).coerceAtLeast(exactCount)
            } else {
                exactCount
            }

            val occurrences = when {
                hasRoot -> quranDao.getRootOccurrences(item.word.rootArabic)
                normalizedWord.isNotBlank() -> quranDao.getWordOccurrencesByNormalized(normalizedWord)
                    .ifEmpty { quranDao.getWordOccurrencesByArabic(item.word.textUthmani) }
                else -> quranDao.getWordOccurrencesByArabic(item.word.textUthmani)
            }

            val otherForms = when {
                hasRoot -> quranDao.getDistinctFormsByRoot(item.word.rootArabic)
                analysis.normalizedStem.length >= 2 && normalizedWord.isNotBlank() ->
                    quranDao.getDistinctFormsByNormalizedStem(normalizedWord, analysis.normalizedStem)
                else -> emptyList()
            }

            val totalOccurrencesCount = if (hasRoot) {
                quranDao.countWordsByRoot(item.word.rootArabic).coerceAtLeast(occurrences.size).coerceAtLeast(exactCount)
            } else {
                val formSum = otherForms.sumOf { it.occurrenceCount }
                maxOf(normalizedCount, formSum, occurrences.size, exactCount)
            }

            _inspectedWord.value = WordInspectorState(
                word = item.word,
                localizedMeaning = item.localizedMeaning,
                ayahVerseKey = verseKey,
                surahName = uiState.value.localizedSurahName,
                exactOccurrencesCount = exactCount,
                rootOccurrencesCount = totalOccurrencesCount,
                otherForms = otherForms,
                rootOccurrences = occurrences,
                analysis = analysis
            )
            if (preferencesRepository.preferencesFlow.first().autoPlayWordOnInspect) {
                playInspectedWord(uiState.value.preferences.audioPlaybackSpeed)
            }
        }
    }

    fun translateInspectedWord(targetLang: String) {
        val current = _inspectedWord.value ?: return
        val translator = lineTranslator ?: return
        val wordId = current.word.id
        val normalizedTarget = targetLang.trim().lowercase()
        _inspectedWord.update {
            if (it?.word?.id == wordId) {
                it.copy(isTranslatingWord = true, wordTranslationError = null)
            } else it
        }
        viewModelScope.launch {
            val englishMeaning = current.analysis.allMeanings
                .firstOrNull { it.first.contains("English", ignoreCase = true) }
                ?.second
                ?.takeIf { it.isNotBlank() }
            val srcMeaning = englishMeaning
                ?: current.localizedMeaning.ifBlank { current.word.transliteration }

            val existingByLang = current.analysis.allMeanings.firstOrNull { (label, _) ->
                when (normalizedTarget) {
                    "bn" -> label.contains("Bangla", ignoreCase = true) || label.contains("বাংলা")
                    "en" -> label.contains("English", ignoreCase = true)
                    "ur" -> label.contains("Urdu", ignoreCase = true) || label.contains("اردو")
                    "id" -> label.contains("Indonesian", ignoreCase = true)
                    "tr" -> label.contains("Turkish", ignoreCase = true) || label.contains("Türkçe")
                    "ar" -> label.contains("Arabic", ignoreCase = true) || label.contains("العربية")
                    else -> label.equals(normalizedTarget, ignoreCase = true)
                }
            }?.second?.takeIf { it.isNotBlank() }

            val hasNativeGrammarExplain = normalizedTarget in setOf("en", "bn", "ar")
            val nativeExplanation = if (hasNativeGrammarExplain) {
                current.analysis.localizedExplanation(normalizedTarget)
            } else {
                current.analysis.explanationEn
            }

            if (existingByLang != null && hasNativeGrammarExplain) {
                _inspectedWord.update {
                    if (it?.word?.id == wordId) {
                        it.copy(
                            translatedTargetLang = normalizedTarget,
                            translatedMeaning = existingByLang,
                            translatedExplanation = nativeExplanation,
                            isTranslatingWord = false,
                            wordTranslationError = null
                        )
                    } else it
                }
                return@launch
            }

            val payload = when {
                existingByLang == null && !hasNativeGrammarExplain -> listOf(srcMeaning, nativeExplanation)
                existingByLang == null -> listOf(srcMeaning)
                else -> listOf(nativeExplanation)
            }

            val result = translator.translateLinesJoined(payload, targetLang)
            result.onSuccess { parts ->
                val trMeaning = existingByLang ?: parts.getOrNull(0).orEmpty().ifBlank { srcMeaning }
                val trExplain = when {
                    hasNativeGrammarExplain -> nativeExplanation
                    existingByLang == null -> parts.getOrNull(1).orEmpty().ifBlank { nativeExplanation }
                    else -> parts.getOrNull(0).orEmpty().ifBlank { nativeExplanation }
                }

                val updatedAnalysis = if (existingByLang == null && trMeaning.isNotBlank()) {
                    quranDao.insertWordTranslations(
                        listOf(WordTranslationEntity(wordId = wordId, languageCode = normalizedTarget, meaningText = trMeaning))
                    )
                    val refreshed = quranDao.getWordTranslations(wordId)
                    WordMorphologyAnalyzer.analyze(current.word, refreshed)
                } else {
                    current.analysis
                }

                val finalExplain = if (hasNativeGrammarExplain) {
                    updatedAnalysis.localizedExplanation(normalizedTarget)
                } else {
                    trExplain
                }

                _inspectedWord.update {
                    if (it?.word?.id == wordId) {
                        it.copy(
                            analysis = updatedAnalysis,
                            translatedTargetLang = targetLang,
                            translatedMeaning = trMeaning,
                            translatedExplanation = finalExplain,
                            isTranslatingWord = false,
                            wordTranslationError = null
                        )
                    } else it
                }
            }.onFailure { err ->
                _inspectedWord.update {
                    if (it?.word?.id == wordId) {
                        it.copy(
                            isTranslatingWord = false,
                            wordTranslationError = err.localizedMessage ?: "Translation failed"
                        )
                    } else it
                }
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
        _lineTranslationState.update { it.copy(translatedTafsir = null, isTranslatingTafsir = false) }
        _selectedTafsirAyah.value = ayah
    }

    fun closeTafsir() {
        _selectedTafsirAyah.value = null
        _tafsirError.value = null
        _lineTranslationState.update { it.copy(translatedTafsir = null, isTranslatingTafsir = false) }
    }

    fun selectTafsirSource(source: TafsirSourceEntity) {
        val ayah = _selectedTafsirAyah.value ?: return
        _lineTranslationState.update { it.copy(translatedTafsir = null, isTranslatingTafsir = false) }
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

    fun translateAyahLine(bundle: AyahDisplayBundle, targetLang: String) {
        val translator = lineTranslator ?: return
        val ayahId = bundle.ayah.id
        val existingLocal = bundle.translations.firstOrNull {
            it.languageCode.equals(targetLang, ignoreCase = true)
        }?.translatedText
        if (!existingLocal.isNullOrBlank()) {
            _lineTranslationState.update {
                it.copy(
                    ayahLines = it.ayahLines + (ayahId to (targetLang to existingLocal)),
                    errorMessage = null
                )
            }
            return
        }

        val sourceText = bundle.translations.firstOrNull {
            !it.languageCode.equals(targetLang, ignoreCase = true)
        }?.translatedText
            ?: bundle.translations.firstOrNull()?.translatedText
            ?: bundle.ayah.textUthmani

        _lineTranslationState.update {
            it.copy(
                translatingAyahIds = it.translatingAyahIds + ayahId,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val res = translator.translateLine(sourceText, targetLang)
            _lineTranslationState.update { state ->
                val updatedMap = if (res.isSuccess) {
                    state.ayahLines + (ayahId to (targetLang to res.getOrThrow()))
                } else {
                    state.ayahLines
                }
                state.copy(
                    ayahLines = updatedMap,
                    translatingAyahIds = state.translatingAyahIds - ayahId,
                    errorMessage = res.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun translateSurahLines(targetLang: String) {
        val translator = lineTranslator ?: return
        val ayahs = uiState.value.ayahs
        if (ayahs.isEmpty()) return

        batchTranslateJob?.cancel()
        _lineTranslationState.update {
            it.copy(
                activeBatchLanguage = targetLang,
                isBatchTranslating = true,
                errorMessage = null
            )
        }
        batchTranslateJob = viewModelScope.launch {
            try {
                for (chunk in ayahs.chunked(12)) {
                    val alreadyLocal = mutableMapOf<Int, String>()
                    val toTranslate = mutableListOf<Pair<Int, String>>()

                    for (bundle in chunk) {
                        val existing = bundle.translations.firstOrNull {
                            it.languageCode.equals(targetLang, ignoreCase = true)
                        }?.translatedText
                        if (!existing.isNullOrBlank()) {
                            alreadyLocal[bundle.ayah.id] = existing
                        } else {
                            val src = bundle.translations.firstOrNull {
                                !it.languageCode.equals(targetLang, ignoreCase = true)
                            }?.translatedText
                                ?: bundle.translations.firstOrNull()?.translatedText
                                ?: bundle.ayah.textUthmani
                            toTranslate.add(bundle.ayah.id to src)
                        }
                    }

                    if (alreadyLocal.isNotEmpty()) {
                        _lineTranslationState.update { state ->
                            val additions = alreadyLocal.mapValues { (_, text) -> targetLang to text }
                            state.copy(ayahLines = state.ayahLines + additions)
                        }
                    }

                    if (toTranslate.isNotEmpty()) {
                        val res = translator.translateLinesJoined(toTranslate.map { it.second }, targetLang)
                        if (res.isSuccess) {
                            val translatedList = res.getOrThrow()
                            val mapped = toTranslate.mapIndexed { idx, (ayahId, _) ->
                                ayahId to (targetLang to translatedList.getOrElse(idx) { "" })
                            }.toMap()
                            _lineTranslationState.update { state ->
                                state.copy(ayahLines = state.ayahLines + mapped)
                            }
                        } else {
                            _lineTranslationState.update {
                                it.copy(
                                    isBatchTranslating = false,
                                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Translation failed"
                                )
                            }
                            return@launch
                        }
                    }
                }
                _lineTranslationState.update { it.copy(isBatchTranslating = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _lineTranslationState.update { it.copy(isBatchTranslating = false) }
                throw e
            }
        }
    }

    fun clearAyahLineTranslation(ayahId: Int) {
        _lineTranslationState.update { it.copy(ayahLines = it.ayahLines - ayahId) }
    }

    fun clearAllLineTranslations() {
        batchTranslateJob?.cancel()
        _lineTranslationState.update {
            it.copy(
                ayahLines = emptyMap(),
                translatingAyahIds = emptySet(),
                activeBatchLanguage = null,
                isBatchTranslating = false,
                errorMessage = null
            )
        }
    }

    fun translateActiveTafsir(cleanBody: String, targetLang: String) {
        val translator = lineTranslator ?: return
        if (cleanBody.isBlank()) return
        _lineTranslationState.update { it.copy(isTranslatingTafsir = true, errorMessage = null) }
        viewModelScope.launch {
            val res = translator.translateLine(cleanBody, targetLang)
            _lineTranslationState.update { state ->
                if (res.isSuccess) {
                    state.copy(
                        translatedTafsir = targetLang to res.getOrThrow(),
                        isTranslatingTafsir = false
                    )
                } else {
                    state.copy(
                        isTranslatingTafsir = false,
                        errorMessage = res.exceptionOrNull()?.localizedMessage
                    )
                }
            }
        }
    }

    fun clearTranslatedTafsir() {
        _lineTranslationState.update { it.copy(translatedTafsir = null, isTranslatingTafsir = false) }
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

    fun setArabicFontFamily(option: ArabicFontFamilyOption) = viewModelScope.launch {
        preferencesRepository.setArabicFontFamily(option)
    }

    fun setShowWordByWord(show: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowWordByWord(show)
    }

    fun setShowTranslation(show: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowTranslation(show)
    }

    fun setShowInlineTafsir(show: Boolean) = viewModelScope.launch {
        preferencesRepository.setShowInlineTafsir(show)
    }

    fun setWbwLanguage(languageCode: String) = viewModelScope.launch {
        preferencesRepository.setWbwLanguage(languageCode)
    }

    fun setThemeMode(mode: NoorThemeMode) = viewModelScope.launch {
        preferencesRepository.setThemeMode(mode)
    }

    fun toggleTranslationSource(source: TranslationSourceEntity, enabled: Boolean) {
        viewModelScope.launch {
            quranDao.setTranslationEnabled(source.id, enabled)
            if (enabled && source.id != 20) {
                val sid = _surahId.value
                val countForSurah = quranDao.countTranslationsForSurahBySource(sid, source.id)
                if (countForSurah == 0) {
                    _busyTranslationSourceId.value = source.id
                    syncRepository.ensureTranslationsForSurah(sid, listOf(source), lineTranslator)
                    _busyTranslationSourceId.value = null
                }
            }
        }
    }

    fun selectActiveTafsirSource(source: TafsirSourceEntity) = viewModelScope.launch {
        preferencesRepository.setActiveTafsirSourceId(source.id)
    }

    fun syncRemoteTafsirCatalog() = viewModelScope.launch {
        _isRefreshingTafsirs.value = true
        syncRepository.refreshRemoteTafsirCatalog()
        _isRefreshingTafsirs.value = false
    }

    fun fetchInlineTafsirForAyah(ayah: AyahEntity) = viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        val activeId = state.preferences.activeTafsirSourceId
        val src = state.tafsirSources.firstOrNull { it.id == activeId }
            ?: TafsirSourceEntity(activeId, "en", "English", "Tafsir", false, true, 0)
        syncRepository.fetchAyahTafsirIfMissing(
            ayahId = ayah.id,
            verseKey = ayah.verseKey,
            tafsirId = src.id,
            languageCode = src.languageCode,
            tafsirName = src.tafsirName
        )
    }

    fun setReaderCardStyle(style: ReaderCardStyle) = viewModelScope.launch {
        preferencesRepository.setReaderCardStyle(style)
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
        audioController.deleteSurahAudio(sid)
    }
    fun setAutoPlayWordOnInspect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoPlayWordOnInspect(enabled) }
    }
    fun selectReciter(reciter: AudioReciterEntity) {
        viewModelScope.launch { preferencesRepository.setActiveReciterId(reciter.id) }
        audioController.updateReciter(reciter.id, reciter.name)
    }
}
