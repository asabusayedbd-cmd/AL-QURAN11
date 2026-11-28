package com.example.feature.explorer

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.AyahEntity
import com.example.core.database.AyahTranslationEntity
import com.example.core.database.DuaEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.QuranDatabaseSeeder
import com.example.core.database.SurahEntity
import com.example.core.localization.BidiUtils
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.UserPreferences
import com.example.core.network.QuranSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ExplorerSearchResult(
    val ayah: AyahEntity,
    val surahName: String,
    val matchedTranslation: AyahTranslationEntity?
)

@Immutable
data class ExplorerUiState(
    val surahs: List<SurahEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val searchQuery: String = "",
    val matchedSurahs: List<SurahEntity> = emptyList(),
    val searchResults: List<ExplorerSearchResult> = emptyList(),
    val matchedDuas: List<DuaEntity> = emptyList(),
    val downloadingSurahId: Int? = null,
    val downloadMessage: String? = null
)

class ExplorerViewModel(
    private val database: NoorDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val syncRepository: QuranSyncRepository,
    private val seeder: QuranDatabaseSeeder? = null
) : ViewModel() {

    private val quranDao = database.quranDao()
    private val duaDao = database.duaDao()

    private val _searchQuery = MutableStateFlow("")
    private val _matchedSurahs = MutableStateFlow<List<SurahEntity>>(emptyList())
    private val _searchResults = MutableStateFlow<List<ExplorerSearchResult>>(emptyList())
    private val _matchedDuas = MutableStateFlow<List<DuaEntity>>(emptyList())
    private val _downloadingSurahId = MutableStateFlow<Int?>(null)
    private val _downloadMessage = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    val uiState: StateFlow<ExplorerUiState> = combine(
        quranDao.observeAllSurahs(),
        preferencesRepository.preferencesFlow,
        _searchQuery,
        _matchedSurahs,
        _searchResults,
        _matchedDuas,
        _downloadingSurahId,
        _downloadMessage
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val surahs = array[0] as List<SurahEntity>
        val prefs = array[1] as UserPreferences
        val query = array[2] as String
        @Suppress("UNCHECKED_CAST")
        val surahMatches = array[3] as List<SurahEntity>
        @Suppress("UNCHECKED_CAST")
        val results = array[4] as List<ExplorerSearchResult>
        @Suppress("UNCHECKED_CAST")
        val duaMatches = array[5] as List<DuaEntity>
        val downloadingId = array[6] as Int?
        val dlMessage = array[7] as String?

        ExplorerUiState(
            surahs = surahs,
            preferences = prefs,
            searchQuery = query,
            matchedSurahs = surahMatches,
            searchResults = results,
            matchedDuas = duaMatches,
            downloadingSurahId = downloadingId,
            downloadMessage = dlMessage
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExplorerUiState()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _matchedSurahs.value = emptyList()
            _searchResults.value = emptyList()
            _matchedDuas.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(75L)
            val allSurahs = uiState.value.surahs
            val asciiDigitsQuery = BidiUtils.normalizeDigitsToAscii(trimmed)
            val normalizedArabic = BidiUtils.normalizeArabicForSearch(trimmed)
            val hasArabicScript = trimmed.any { it in '\u0600'..'\u06FF' }

            // Multi-form verse key parsing: "2:255", "2 255", "২:২৫৫", "40-3", "surah 40", "সূরা ৪০"
            val verseKeyMatch = VERSE_KEY_REGEX.matchEntire(asciiDigitsQuery)
            val parsedVerseKey = verseKeyMatch?.let { m ->
                val s = m.groupValues[1].toIntOrNull()
                val a = m.groupValues[2].toIntOrNull()
                if (s != null && s in 1..114 && a != null && a >= 1) "$s:$a" else null
            }

            val bareNumber = SURAH_NUM_EXTRACT_REGEX.find(asciiDigitsQuery)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: asciiDigitsQuery.toIntOrNull()

            val surahMatches = allSurahs.filter { surah ->
                (bareNumber != null && surah.id == bareNumber) ||
                    (verseKeyMatch != null && surah.id == verseKeyMatch.groupValues[1].toIntOrNull()) ||
                    surah.nameTransliteration.contains(trimmed, ignoreCase = true) ||
                    surah.nameArabic.contains(trimmed) ||
                    (normalizedArabic.isNotBlank() && BidiUtils.normalizeArabicForSearch(surah.nameArabic).contains(normalizedArabic)) ||
                    surah.localizedNamesJson.contains(trimmed, ignoreCase = true)
            }.take(8)
            _matchedSurahs.value = surahMatches

            // Ensure target Surah from verseKey is seeded offline before lookup
            if (parsedVerseKey != null) {
                val sid = parsedVerseKey.substringBefore(':').toIntOrNull()
                if (sid != null && quranDao.countAyahsInSurah(sid) == 0) {
                    seeder?.ensureSurahLoadedFromAssets(sid)
                }
            }

            // Fast FTS4 virtual table queries first, with indexed LIKE fallback for infixes / short tokens
            val arabicFtsQuery = buildFtsPrefixQuery(normalizedArabic)
            val rawFtsQuery = buildFtsPrefixQuery(trimmed)
            val combinedAyahs = coroutineScope {
                val exactDef = async {
                    parsedVerseKey?.let { quranDao.getAyahByKey(it) }
                }
                val arabicNormDef = async {
                    if (hasArabicScript && normalizedArabic.isNotBlank()) {
                        val ftsHits = arabicFtsQuery?.let { q ->
                            runCatching { quranDao.searchAyahsByFts(q) }.getOrDefault(emptyList())
                        }.orEmpty()
                        if (ftsHits.size >= 8) ftsHits else {
                            (ftsHits + quranDao.searchAyahsByNormalizedArabic(normalizedArabic)).distinctBy { it.id }
                        }
                    } else emptyList()
                }
                val arabicRawDef = async {
                    if (hasArabicScript) {
                        quranDao.searchAyahsByUthmani(trimmed)
                    } else emptyList()
                }
                val trIdsDef = async {
                    if (trimmed.length >= 2 && parsedVerseKey == null) {
                        val ftsIds = rawFtsQuery?.let { q ->
                            runCatching { quranDao.searchAyahIdsByTranslationFts(q) }.getOrDefault(emptyList())
                        }.orEmpty()
                        if (ftsIds.size >= 8) ftsIds else {
                            (ftsIds + quranDao.searchAyahIdsByTranslation(trimmed)).distinct()
                        }
                    } else emptyList()
                }
                val wordIdsDef = async {
                    if (trimmed.length >= 2 && parsedVerseKey == null) {
                        quranDao.searchAyahIdsByWord(trimmed, normalizedArabic.ifBlank { trimmed })
                    } else emptyList()
                }
                val duaDef = async {
                    if (trimmed.length >= 2 && parsedVerseKey == null) {
                        duaDao.searchDuas(trimmed)
                    } else emptyList()
                }

                _matchedDuas.value = duaDef.await()

                val ordered = LinkedHashMap<Int, AyahEntity>()
                exactDef.await()?.let { ordered[it.id] = it }
                arabicNormDef.await().forEach { ordered.putIfAbsent(it.id, it) }
                arabicRawDef.await().forEach { ordered.putIfAbsent(it.id, it) }

                val extraIds = (wordIdsDef.await() + trIdsDef.await())
                    .distinct()
                    .filter { it !in ordered }
                    .take(60)
                if (extraIds.isNotEmpty()) {
                    extraIds.chunked(200)
                        .flatMap { quranDao.getAyahsByIds(it) }
                        .forEach { ordered.putIfAbsent(it.id, it) }
                }
                ordered.values.take(60)
            }

            mapAndEmitResults(combinedAyahs)
        }
    }

    fun downloadSurah(surahId: Int) {
        viewModelScope.launch {
            _downloadingSurahId.value = surahId
            _downloadMessage.value = null
            val prefs = uiState.value.preferences
            val restoredLocal = seeder?.ensureSurahLoadedFromAssets(surahId) == true
            val res = syncRepository.downloadSurahForOffline(
                surahId = surahId,
                wbwLanguages = listOf(prefs.wbwLanguageCode),
                reciterId = prefs.activeReciterId
            )
            if (res.isSuccess) {
                _downloadMessage.value = "Downloaded Surah $surahId (${res.getOrNull()} Ayahs)"
            } else if (restoredLocal) {
                _downloadMessage.value = "Surah $surahId ready offline"
            } else {
                _downloadMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Download failed"
            }
            _downloadingSurahId.value = null
        }
    }

    fun deleteSurah(surahId: Int) {
        viewModelScope.launch {
            _downloadingSurahId.value = surahId
            _downloadMessage.value = null
            val res = syncRepository.deleteSurahOffline(surahId)
            res.onSuccess {
                _downloadMessage.value = "Cleared audio cache for Surah $surahId — Mushaf text is protected"
            }.onFailure { err ->
                _downloadMessage.value = err.localizedMessage ?: "Audio cleanup failed"
            }
            _downloadingSurahId.value = null
        }
    }

    suspend fun resolvePageStartSurah(page: Int): Int {
        return quranDao.getFirstAyahOnPage(page)?.surahId
            ?: uiState.value.surahs.lastOrNull { it.startPage <= page }?.id
            ?: when {
                page == 1 -> 1
                page <= 49 -> 2
                page >= 604 -> 114
                else -> ((page * 114) / 604).coerceIn(1, 114)
            }
    }

    fun clearMessage() {
        _downloadMessage.value = null
    }

    private suspend fun mapAndEmitResults(ayahs: List<AyahEntity>) {
        if (ayahs.isEmpty()) {
            _searchResults.update { emptyList() }
            return
        }
        val prefs = uiState.value.preferences
        val surahsMap = uiState.value.surahs.associateBy { it.id }
        val trsByAyahId = ayahs.map { it.id }
            .chunked(500)
            .flatMap { quranDao.getTranslationsForAyahs(it) }
            .groupBy { it.ayahId }

        val mapped = ayahs.map { ayah ->
            val surah = surahsMap[ayah.surahId]
            val surahName = surah?.let {
                BidiUtils.formatSurahTitle(it.nameTransliteration, it.localizedNamesJson, prefs.uiLanguageCode)
            } ?: "Surah ${ayah.surahId}"
            val trs = trsByAyahId[ayah.id].orEmpty()
            val bestTr = trs.firstOrNull { it.languageCode == prefs.uiLanguageCode }
                ?: trs.firstOrNull { it.languageCode == "en" }
                ?: trs.firstOrNull { it.languageCode == "bn" }
                ?: trs.firstOrNull()
            ExplorerSearchResult(ayah, surahName, bestTr)
        }
        _searchResults.update { mapped }
    }

    private fun buildFtsPrefixQuery(raw: String): String? {
        if (raw.isBlank()) return null
        val sanitizedTokens = raw.split(FTS_SPLIT_REGEX)
            .map { token -> token.replace(FTS_UNSAFE_CHARS_REGEX, "").trim() }
            .filter { it.length >= 2 }
            .take(6)
        if (sanitizedTokens.isEmpty()) return null
        return sanitizedTokens.joinToString(" ") { "$it*" }
    }

    companion object {
        private val VERSE_KEY_REGEX = Regex("^(\\d{1,3})\\s*[:\\-/\\s]\\s*(\\d{1,3})$")
        private val SURAH_NUM_EXTRACT_REGEX = Regex("^(?:surah|sura|সূরা|سورة)\\s*(\\d{1,3})$", RegexOption.IGNORE_CASE)
        private val FTS_SPLIT_REGEX = Regex("\\s+")
        private val FTS_UNSAFE_CHARS_REGEX = Regex("[\"'*:()\\-+^~]")
    }
}
