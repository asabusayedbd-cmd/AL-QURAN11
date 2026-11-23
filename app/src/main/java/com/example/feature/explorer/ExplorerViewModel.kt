package com.example.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.AyahEntity
import com.example.core.database.AyahTranslationEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.SurahEntity
import com.example.core.localization.BidiUtils
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.UserPreferences
import com.example.core.network.QuranSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorerSearchResult(
    val ayah: AyahEntity,
    val surahName: String,
    val matchedTranslation: AyahTranslationEntity?
)

data class ExplorerUiState(
    val surahs: List<SurahEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val searchQuery: String = "",
    val matchedSurahs: List<SurahEntity> = emptyList(),
    val searchResults: List<ExplorerSearchResult> = emptyList(),
    val downloadingSurahId: Int? = null,
    val downloadMessage: String? = null
)

class ExplorerViewModel(
    private val database: NoorDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val syncRepository: QuranSyncRepository
) : ViewModel() {

    private val quranDao = database.quranDao()

    private val _searchQuery = MutableStateFlow("")
    private val _matchedSurahs = MutableStateFlow<List<SurahEntity>>(emptyList())
    private val _searchResults = MutableStateFlow<List<ExplorerSearchResult>>(emptyList())
    private val _downloadingSurahId = MutableStateFlow<Int?>(null)
    private val _downloadMessage = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    val uiState: StateFlow<ExplorerUiState> = combine(
        quranDao.observeAllSurahs(),
        preferencesRepository.preferencesFlow,
        _searchQuery,
        _matchedSurahs,
        _searchResults,
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
        val downloadingId = array[5] as Int?
        val dlMessage = array[6] as String?

        ExplorerUiState(
            surahs = surahs,
            preferences = prefs,
            searchQuery = query,
            matchedSurahs = surahMatches,
            searchResults = results,
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
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(90L)
            val allSurahs = uiState.value.surahs
            val numQuery = trimmed.toIntOrNull()
            val normalized = BidiUtils.normalizeArabicForSearch(trimmed)
            val surahMatches = allSurahs.filter { surah ->
                (numQuery != null && surah.id == numQuery) ||
                    surah.nameTransliteration.contains(trimmed, ignoreCase = true) ||
                    surah.nameArabic.contains(trimmed) ||
                    BidiUtils.normalizeArabicForSearch(surah.nameArabic).contains(normalized.ifBlank { trimmed }) ||
                    surah.localizedNamesJson.contains(trimmed, ignoreCase = true)
            }.take(8)
            _matchedSurahs.value = surahMatches

            val matchedAyahs = quranDao.searchAyahs(
                exactQuery = trimmed,
                normalizedQuery = normalized.ifBlank { trimmed },
                rawQuery = trimmed
            )
            mapAndEmitResults(matchedAyahs)
        }
    }

    fun downloadSurah(surahId: Int) {
        viewModelScope.launch {
            _downloadingSurahId.value = surahId
            _downloadMessage.value = null
            val prefs = uiState.value.preferences
            val res = syncRepository.downloadSurahForOffline(
                surahId = surahId,
                wbwLanguages = listOf(prefs.wbwLanguageCode),
                reciterId = prefs.activeReciterId
            )
            res.onSuccess { count ->
                _downloadMessage.value = "Downloaded Surah $surahId ($count Ayahs)"
            }.onFailure { err ->
                _downloadMessage.value = err.localizedMessage ?: "Download failed"
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
                _downloadMessage.value = "Deleted Surah $surahId offline cache"
            }.onFailure { err ->
                _downloadMessage.value = err.localizedMessage ?: "Delete failed"
            }
            _downloadingSurahId.value = null
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
                BidiUtils.extractLocalizedSurahName(it.localizedNamesJson, prefs.uiLanguageCode)
                    .ifBlank { it.nameTransliteration }
            } ?: "Surah ${ayah.surahId}"
            val trs = trsByAyahId[ayah.id].orEmpty()
            val bestTr = trs.firstOrNull { it.languageCode == prefs.uiLanguageCode }
                ?: trs.firstOrNull { it.languageCode == "bn" }
                ?: trs.firstOrNull()
            ExplorerSearchResult(ayah, surahName, bestTr)
        }
        _searchResults.update { mapped }
    }
}
