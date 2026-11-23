package com.example.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.AyahEntity
import com.example.core.database.AyahTranslationEntity
import com.example.core.database.HabitStreakEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.ReadingProgressEntity
import com.example.core.database.SurahEntity
import com.example.core.database.UserBookmarkEntity
import com.example.core.ibadah.PrayerTimeCalculator
import com.example.core.ibadah.PrayerTimesSummary
import com.example.core.ibadah.StreakCalculator
import com.example.core.localization.BidiUtils
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerseOfTheDayItem(
    val ayah: AyahEntity,
    val surahName: String,
    val translation: AyahTranslationEntity?,
    val isBookmarked: Boolean = false
)

data class HomeUiState(
    val preferences: UserPreferences = UserPreferences(),
    val progress: ReadingProgressEntity = ReadingProgressEntity(),
    val lastSurahName: String = "Al-Fatihah",
    val streaks: List<HabitStreakEntity> = emptyList(),
    val currentStreakDays: Int = 0,
    val todayAyahsRead: Int = 0,
    val todayTasbeehCount: Int = 0,
    val verseOfTheDay: VerseOfTheDayItem? = null,
    val prayerTimes: PrayerTimesSummary = PrayerTimeCalculator.calculateDailyTimes(23.8103, 90.4125),
    val qiblaBearingDegrees: Float = PrayerTimeCalculator.calculateQiblaBearing(23.8103, 90.4125),
    val gregorianDate: String = "",
    val hijriDate: String = "",
    val tasbeehPhraseIndex: Int = 0
)

class HomeViewModel(
    private val database: NoorDatabase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val quranDao = database.quranDao()
    private val userLibraryDao = database.userLibraryDao()

    private val _tasbeehPhraseIdx = MutableStateFlow(0)

    private val verseOfTheDayFlow = flow {
        // Verified curated rotating Ayahs bundled locally in starter dataset
        val keys = listOf("2:255", "1:5", "2:201", "3:8", "103:3", "112:1")
        val dayIdx = ((System.currentTimeMillis() / 86_400_000L) % keys.size).toInt()
        val chosenKey = keys[dayIdx]
        val ayah = quranDao.getAyahByKey(chosenKey) ?: quranDao.getAyahByKey("1:1")
        if (ayah != null) {
            val surah = quranDao.getSurah(ayah.surahId)
            val translations = quranDao.getTranslationsForAyah(ayah.id)
            emit(Triple(ayah, surah, translations))
        } else {
            emit(null)
        }
    }

    private val coreHomeFlow = combine(
        preferencesRepository.preferencesFlow,
        userLibraryDao.observeReadingProgress(),
        userLibraryDao.observeRecentStreaks(),
        quranDao.observeAllSurahs(),
        userLibraryDao.observeBookmarks()
    ) { prefs, progress, streaks, surahs, bookmarks ->
        Triple(prefs, progress, Triple(streaks, surahs, bookmarks))
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreHomeFlow,
        verseOfTheDayFlow,
        _tasbeehPhraseIdx
    ) { core, votdTriple, phraseIdx ->
        val prefs = core.first
        val progress = core.second ?: ReadingProgressEntity()
        val (streaks, surahs, bookmarks) = core.third

        val todayKey = StreakCalculator.todayDateKey()
        val todayStreak = streaks.firstOrNull { it.dateKey == todayKey }
        val streakDays = StreakCalculator.calculateCurrentStreak(streaks, todayKey)

        val lastSurah = surahs.firstOrNull { it.id == progress.lastSurahId }
        val lastSurahName = lastSurah?.let {
            BidiUtils.extractLocalizedSurahName(it.localizedNamesJson, prefs.uiLanguageCode)
                .ifBlank { it.nameTransliteration }
        } ?: "Al-Fatihah"

        val bookmarkedKeys = bookmarks.map { it.verseKey }.toSet()
        val votdItem = votdTriple?.let { (ayah, surah, translations) ->
            val sName = surah?.let {
                BidiUtils.extractLocalizedSurahName(it.localizedNamesJson, prefs.uiLanguageCode)
                    .ifBlank { it.nameTransliteration }
            } ?: "Surah ${ayah.surahId}"
            val preferredTr = translations.firstOrNull { it.languageCode == prefs.uiLanguageCode }
                ?: translations.firstOrNull { it.languageCode == "bn" }
                ?: translations.firstOrNull()
            VerseOfTheDayItem(
                ayah = ayah,
                surahName = sName,
                translation = preferredTr,
                isBookmarked = ayah.verseKey in bookmarkedKeys
            )
        }

        val (gregorian, hijri) = BidiUtils.formatGregorianAndHijriDate(prefs.uiLanguageCode)

        HomeUiState(
            preferences = prefs,
            progress = progress,
            lastSurahName = lastSurahName,
            streaks = streaks,
            currentStreakDays = streakDays,
            todayAyahsRead = todayStreak?.ayahsReadCount ?: 0,
            todayTasbeehCount = todayStreak?.tasbeehCount ?: 0,
            verseOfTheDay = votdItem,
            prayerTimes = PrayerTimeCalculator.calculateDailyTimes(prefs.latitude, prefs.longitude),
            qiblaBearingDegrees = PrayerTimeCalculator.calculateQiblaBearing(prefs.latitude, prefs.longitude),
            gregorianDate = gregorian,
            hijriDate = hijri,
            tasbeehPhraseIndex = phraseIdx
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun incrementTasbeeh() {
        viewModelScope.launch {
            val todayKey = StreakCalculator.todayDateKey()
            val existing = userLibraryDao.getStreakByDate(todayKey) ?: HabitStreakEntity(dateKey = todayKey)
            val updated = existing.copy(
                tasbeehCount = existing.tasbeehCount + 1,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            userLibraryDao.upsertStreak(updated)
        }
    }

    fun resetTodayTasbeeh() {
        viewModelScope.launch {
            val todayKey = StreakCalculator.todayDateKey()
            val existing = userLibraryDao.getStreakByDate(todayKey) ?: return@launch
            val updated = existing.copy(tasbeehCount = 0, updatedAtEpochMs = System.currentTimeMillis())
            userLibraryDao.upsertStreak(updated)
        }
    }

    fun cycleTasbeehPhrase() {
        _tasbeehPhraseIdx.update { (it + 1) % 4 }
    }

    fun updateLocation(name: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            preferencesRepository.setLocation(name, latitude, longitude)
        }
    }

    fun toggleVerseOfDayBookmark(item: VerseOfTheDayItem) {
        viewModelScope.launch {
            val existing = userLibraryDao.getBookmarkByVerseKey(item.ayah.verseKey)
            if (existing != null) {
                userLibraryDao.deleteBookmark(existing.id)
            } else {
                val now = System.currentTimeMillis()
                val bookmark = UserBookmarkEntity(
                    id = "bm_${item.ayah.verseKey.replace(':', '_')}",
                    verseKey = item.ayah.verseKey,
                    surahId = item.ayah.surahId,
                    ayahNumber = item.ayah.ayahNumber,
                    collectionId = "favorites",
                    label = "${item.surahName} ${item.ayah.verseKey}",
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
                userLibraryDao.insertBookmark(bookmark)
            }
        }
    }
}
