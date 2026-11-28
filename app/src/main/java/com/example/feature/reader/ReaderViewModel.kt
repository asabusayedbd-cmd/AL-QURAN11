package com.example.feature.reader

import androidx.compose.runtime.Immutable
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
import com.example.core.perf.PerfTracker
import com.example.ui.components.WordDisplayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
data class AyahDisplayBundle(
    val ayah: AyahEntity,
    val words: List<WordDisplayItem>,
    val translations: List<AyahTranslationEntity>,
    val isBookmarked: Boolean,
    val hasNote: Boolean,
    val inlineTafsir: AyahTafsirEntity? = null
)

@Immutable
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

@Immutable
data class ActivePlaybackCursor(
    val surahId: Int = 0,
    val ayahNumber: Int = 0,
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false
)

@Immutable
data class LineTranslationUiState(
    val ayahLines: Map<Int, Pair<String, String>> = emptyMap(), // ayahId -> (langCode, text)
    val translatingAyahIds: Set<Int> = emptySet(),
    val activeBatchLanguage: String? = null,
    val isBatchTranslating: Boolean = false,
    val translatedTafsir: Pair<String, String>? = null, // (langCode, text)
    val isTranslatingTafsir: Boolean = false,
    val errorMessage: String? = null
)

@Immutable
data class ReaderUiState(
    val surah: SurahEntity? = null,
    val allSurahs: List<SurahEntity> = emptyList(),
    val localizedSurahName: String = "",
    val ayahs: List<AyahDisplayBundle> = emptyList(),
    val isLoadingSurah: Boolean = true,
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

    private data class BundleQueryKey(
        val surahId: Int,
        val maxWindow: Int,
        val wbwLanguageCode: String,
        val needWordBundles: Boolean
    )

    private data class BundlesSnapshot(
        val surahId: Int,
        val surah: SurahEntity?,
        val bundles: List<AyahDisplayBundle>,
        val maxWindow: Int
    )

    private val _surahId = MutableStateFlow(initialSurahId.coerceIn(1, 114))
    private val _maxAyahWindow = MutableStateFlow(INITIAL_AYAH_WINDOW)
    private val _isLoadingSurah = MutableStateFlow(true)
    private val _screenAttached = MutableStateFlow(false)
    private val _firstVisibleSurahId = MutableStateFlow(0)
    private val _deliveredSurahId = MutableStateFlow(0)
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
    private var batchTranslateJob: Job? = null
    private var windowExpandJob: Job? = null

    companion object {
        private const val INITIAL_AYAH_WINDOW = 24
    }

    private fun computeInitialWindow(targetAyah: Int, targetPage: Int): Int {
        return when {
            targetPage > 0 -> Int.MAX_VALUE
            targetAyah > 16 -> targetAyah + 16
            else -> INITIAL_AYAH_WINDOW
        }
    }

    init {
        PerfTracker.markReaderEntered(initialSurahId.coerceIn(1, 114))

        // Critical Path: Load local Quran text immediately without waiting for network or optional sync
        viewModelScope.launch(Dispatchers.IO) {
            _surahId.collectLatest { sid ->
                _isLoadingSurah.value = true
                try {
                    val surah = quranDao.getSurah(sid)
                    val existingCount = quranDao.countAyahsInSurah(sid)
                    val loaded = if (surah == null || existingCount < surah.ayahCount) {
                        seeder?.ensureSurahLoadedFromAssets(sid, surah?.ayahCount) ?: false
                    } else {
                        true
                    }
                    // Wait until the active uiState pipeline delivers non-empty bundles for sid before
                    // clearing the loading flag, preventing a 1-frame empty-state "Download Surah" flash.
                    if (loaded && _screenAttached.value) {
                        withTimeoutOrNull(1_500L) {
                            _deliveredSurahId.first { it == sid }
                        }
                    }
                } finally {
                    _isLoadingSurah.value = false
                }
            }
        }

        // Expand windowed Surah loading after first visible frame (or short fallback in headless mode)
        viewModelScope.launch(Dispatchers.Default) {
            _surahId.collectLatest { sid ->
                if (_maxAyahWindow.value < Int.MAX_VALUE) {
                    val waitMs = if (_screenAttached.value) 3_000L else 220L
                    withTimeoutOrNull(waitMs) {
                        _firstVisibleSurahId.first { it == sid }
                    }
                    delay(45L)
                    if (_surahId.value == sid) {
                        _maxAyahWindow.value = Int.MAX_VALUE
                    }
                }
            }
        }

        // Deferred Optional Content: WBW non-English meanings & extra translations arrive AFTER first visible render
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                _surahId,
                preferencesRepository.preferencesFlow.map { it.wbwLanguageCode }.distinctUntilChanged(),
                quranDao.observeEnabledTranslationSources()
            ) { sid, wbwLang, enabledSources ->
                Triple(sid, wbwLang, enabledSources)
            }.collectLatest { (sid, wbwLang, enabledSources) ->
                val needsWbw = !wbwLang.equals("en", ignoreCase = true)
                val needsExtraTranslations = enabledSources.any { it.id != 20 }
                if (!needsWbw && !needsExtraTranslations) return@collectLatest

                awaitFirstVisibleGate(sid)
                PerfTracker.markOptionalSyncStarted(sid, "wbw=$wbwLang,tr=${enabledSources.size}")
                try {
                    if (needsWbw) {
                        syncRepository.ensureWbwForSurah(sid, wbwLang, lineTranslator)
                    }
                    if (needsExtraTranslations) {
                        syncRepository.ensureTranslationsForSurah(sid, enabledSources, lineTranslator)
                    }
                } finally {
                    PerfTracker.markOptionalSyncCompleted(sid)
                }
            }
        }

        // Deferred Optional Content: Inline Tafsir sync runs strictly AFTER initial reader content is visible
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
                awaitFirstVisibleGate(sid)
                val source = sources.firstOrNull { it.id == tafsirId }
                    ?: TafsirSourceEntity(tafsirId, "en", "English", "Tafsir", false, true, 0)
                val ayahs = quranDao.getAyahsBySurah(sid)
                if (ayahs.isEmpty()) return@collectLatest
                PerfTracker.markOptionalSyncStarted(sid, "inline_tafsir=$tafsirId")
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
                    PerfTracker.markOptionalSyncCompleted(sid)
                }
            }
        }
    }

    private suspend fun awaitFirstVisibleGate(sid: Int) {
        // Ensure local Ayahs exist in Room first so optional writes never lock or race initial asset load
        withTimeoutOrNull(4_000L) {
            quranDao.observeAyahsBySurahUpTo(sid, 1).first { it.isNotEmpty() }
        }
        // When UI screen is attached, wait until first visible reader content lays out before starting optional sync
        val waitBudgetMs = if (_screenAttached.value) 5_000L else 250L
        withTimeoutOrNull(waitBudgetMs) {
            _firstVisibleSurahId.first { it == sid }
        }
        // Yield briefly so the first Compose frame commits smoothly before Room/network sync starts
        delay(120L)
    }

    fun onScreenAttached(surahId: Int, targetAyah: Int = 1, targetPage: Int = 0) {
        _screenAttached.value = true
        val initialWin = computeInitialWindow(targetAyah, targetPage)
        if (initialWin > _maxAyahWindow.value) {
            _maxAyahWindow.value = initialWin
        }
        PerfTracker.markReaderEntered(surahId.coerceIn(1, 114))
    }

    fun notifyFirstContentVisible(surahId: Int, mushafGroupingSkipped: Boolean) {
        if (_firstVisibleSurahId.value != surahId) {
            _firstVisibleSurahId.value = surahId
        }
        PerfTracker.markFirstVisibleReaderContent(surahId, mushafGroupingSkipped)
        if (_maxAyahWindow.value < Int.MAX_VALUE && windowExpandJob?.isActive != true) {
            windowExpandJob = viewModelScope.launch(Dispatchers.Default) {
                delay(40L)
                if (_surahId.value == surahId) {
                    _maxAyahWindow.value = Int.MAX_VALUE
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

    private val uiPreferencesFlow = preferencesRepository.preferencesFlow
        .map { p ->
            p.copy(
                lastRecitedSurahId = 0,
                lastRecitedAyahNumber = 0,
                lastRecitedReciterId = 7,
                lastRecitedPositionMs = 0L
            )
        }
        .distinctUntilChanged()

    private val bundleQueryFlow = combine(
        _surahId,
        _maxAyahWindow,
        preferencesRepository.preferencesFlow
            .map { Triple(it.wbwLanguageCode, it.readerViewMode, it.showWordByWord) }
            .distinctUntilChanged()
    ) { sid, maxWindow, (wbwLang, viewMode, showWbw) ->
        val effectiveWindow = if (viewMode == ReaderViewMode.MUSHAF) Int.MAX_VALUE else maxWindow
        val needWordBundles = viewMode == ReaderViewMode.WORD_BY_WORD && showWbw
        BundleQueryKey(sid, effectiveWindow, wbwLang, needWordBundles)
    }.distinctUntilChanged()

    private val surahAndBundlesFlow = bundleQueryFlow.flatMapLatest { qk ->
        val wordsFlow = if (qk.needWordBundles) {
            quranDao.observeWordsBySurahUpTo(qk.surahId, qk.maxWindow)
        } else {
            flowOf(emptyList())
        }
        val wordTrFlow = if (qk.needWordBundles) {
            quranDao.observeWordTranslationsBySurahFiltered(qk.surahId, qk.maxWindow, qk.wbwLanguageCode)
        } else {
            flowOf(emptyList())
        }
        val enabledSourcesFlow = quranDao.observeEnabledTranslationSources().distinctUntilChanged()
        val ayahTrFlow = enabledSourcesFlow.flatMapLatest { enabled ->
            if (enabled.isNotEmpty()) {
                quranDao.observeEnabledAyahTranslationsBySurahUpTo(qk.surahId, qk.maxWindow)
            } else {
                quranDao.observeAyahTranslationsBySurahUpTo(qk.surahId, qk.maxWindow)
            }
        }
        combine(
            quranDao.observeSurah(qk.surahId),
            quranDao.observeAyahsBySurahUpTo(qk.surahId, qk.maxWindow),
            wordsFlow,
            wordTrFlow,
            ayahTrFlow,
            enabledSourcesFlow,
            userLibraryDao.observeBookmarkedKeysForSurah(qk.surahId),
            userLibraryDao.observeNoteKeysForSurah(qk.surahId)
        ) { arr ->
            val surah = arr[0] as SurahEntity?
            @Suppress("UNCHECKED_CAST")
            val ayahs = arr[1] as List<AyahEntity>
            @Suppress("UNCHECKED_CAST")
            val allWords = arr[2] as List<WordEntity>
            @Suppress("UNCHECKED_CAST")
            val wordTrs = arr[3] as List<WordTranslationEntity>
            @Suppress("UNCHECKED_CAST")
            val ayahTranslations = arr[4] as List<AyahTranslationEntity>
            @Suppress("UNCHECKED_CAST")
            val enabledSources = arr[5] as List<TranslationSourceEntity>
            @Suppress("UNCHECKED_CAST")
            val bookmarkedKeys = (arr[6] as List<String>).toHashSet()
            @Suppress("UNCHECKED_CAST")
            val noteKeys = (arr[7] as List<String>).toHashSet()

            val bundles = if (ayahs.isEmpty()) {
                emptyList()
            } else {
                val sourceTitleMap = enabledSources.associate { it.id to it.translatorName }
                val wordsByAyah = if (qk.needWordBundles) allWords.groupBy { it.ayahId } else emptyMap()
                val trsByAyah = ayahTranslations.groupBy { it.ayahId }
                val wordTrById = if (qk.needWordBundles) wordTrs.groupBy { it.wordId } else emptyMap()

                ayahs.map { ayah ->
                    val wList = if (qk.needWordBundles) {
                        wordsByAyah[ayah.id].orEmpty().map { w ->
                            val wTrs = wordTrById[w.id].orEmpty()
                            val meaning = wTrs.firstOrNull { it.languageCode == qk.wbwLanguageCode }?.meaningText
                                ?: wTrs.firstOrNull { it.languageCode == "en" }?.meaningText
                                ?: wTrs.firstOrNull()?.meaningText
                                ?: ""
                            WordDisplayItem(w, meaning)
                        }
                    } else {
                        emptyList()
                    }
                    val ayahTrs = trsByAyah[ayah.id].orEmpty().map { tr ->
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
            if (bundles.isNotEmpty()) {
                PerfTracker.markLocalDataReady(qk.surahId, bundles.size)
                _deliveredSurahId.value = qk.surahId
            }
            BundlesSnapshot(qk.surahId, surah, bundles, qk.maxWindow)
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
        _isLoadingInlineTafsirs,
        _isLoadingSurah
    ) { busyTrId, refreshingTaf, loadingInlineTaf, isLoadingSurah ->
        listOf(busyTrId, refreshingTaf, loadingInlineTaf, isLoadingSurah)
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
        uiPreferencesFlow,
        catalogFlow,
        overlayFlow,
        inlineTafsirsForSurahFlow,
        sidebarFlagsFlow
    ) { arr ->
        val core = arr[0] as BundlesSnapshot
        val prefs = arr[1] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val catalog = arr[2] as List<Any>
        @Suppress("UNCHECKED_CAST")
        val overlay = arr[3] as Pair<Triple<AyahEntity?, List<AyahTafsirEntity>, UserNoteEntity?>, Triple<Pair<Boolean, String?>, Pair<Boolean, String?>, WordInspectorState?>>
        @Suppress("UNCHECKED_CAST")
        val inlineTafsirs = arr[4] as List<AyahTafsirEntity>
        @Suppress("UNCHECKED_CAST")
        val sidebarFlags = arr[5] as List<Any?>

        val requestedSurahId = _surahId.value
        val snapshotMatches = core.surahId == requestedSurahId
        val surah = if (snapshotMatches) core.surah else null
        val baseBundles = if (snapshotMatches) core.bundles else emptyList()
        val activeWindow = if (snapshotMatches) core.maxWindow else INITIAL_AYAH_WINDOW
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
        val busyTrId = sidebarFlags[0] as Int?
        val refreshingTaf = sidebarFlags[1] as Boolean
        val loadingInlineTaf = sidebarFlags[2] as Boolean
        val isLoadingSurahFlag = sidebarFlags[3] as Boolean

        val bundles = if (prefs.showInlineTafsir && inlineTafsirs.isNotEmpty()) {
            val inlineByAyah = inlineTafsirs.associateBy { it.ayahId }
            baseBundles.map { b -> b.copy(inlineTafsir = inlineByAyah[b.ayah.id]) }
        } else {
            baseBundles
        }

        val localizedName = surah?.let {
            BidiUtils.formatSurahTitle(it.nameTransliteration, it.localizedNamesJson, prefs.uiLanguageCode)
        } ?: "Surah $requestedSurahId"

        val stillLoading = (isLoadingSurahFlag || !snapshotMatches) && bundles.isEmpty()

        val isIncomplete = surah != null &&
            snapshotMatches &&
            activeWindow == Int.MAX_VALUE &&
            !stillLoading &&
            bundles.size < surah.ayahCount

        PerfTracker.recordReaderStateEmission(requestedSurahId, bundles.isNotEmpty())

        ReaderUiState(
            surah = surah,
            allSurahs = allSurahs,
            localizedSurahName = localizedName,
            ayahs = bundles,
            isLoadingSurah = stillLoading,
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

    fun loadSurah(surahId: Int, targetAyah: Int = 1, targetPage: Int = 0) {
        val targetId = surahId.coerceIn(1, 114)
        val initialWin = computeInitialWindow(targetAyah, targetPage)
        if (_surahId.value != targetId) {
            PerfTracker.markReaderEntered(targetId)
            batchTranslateJob?.cancel()
            windowExpandJob?.cancel()
            _lineTranslationState.value = LineTranslationUiState()
            _firstVisibleSurahId.value = 0
            _deliveredSurahId.value = 0
            _isLoadingSurah.value = true
            _maxAyahWindow.value = initialWin
            _surahId.value = targetId
        } else if (initialWin > _maxAyahWindow.value) {
            _maxAyahWindow.value = initialWin
        }
        _downloadError.value = null
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

    fun setMushafStyle(style: com.example.core.localization.MushafEditionStyle) = viewModelScope.launch {
        preferencesRepository.setMushafStyle(style)
    }

    fun resumeLastRecitation() = audioController.resumeLastRecitation()

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
