package com.example.feature.home

import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class VerseOfTheDayItem(
    val ayah: AyahEntity,
    val surahName: String,
    val translation: AyahTranslationEntity?,
    val isBookmarked: Boolean = false
)

@Immutable
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
    val distanceToKaabaKm: Int = PrayerTimeCalculator.calculateDistanceToKaabaKm(23.8103, 90.4125),
    val gregorianDate: String = "",
    val hijriDate: String = "",
    val tasbeehPhraseIndex: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val database: NoorDatabase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val quranDao = database.quranDao()
    private val userLibraryDao = database.userLibraryDao()

    private val _tasbeehPhraseIdx = MutableStateFlow(0)

    private val chosenVotdKey: String = run {
        val keys = listOf("2:255", "1:5", "2:201", "3:8", "103:3", "112:1")
        val dayIdx = ((System.currentTimeMillis() / 86_400_000L) % keys.size).toInt()
        keys[dayIdx]
    }

    private val verseOfTheDayFlow = combine(
        quranDao.observeAyahByKey(chosenVotdKey),
        quranDao.observeAyahByKey("1:1")
    ) { primary, fallback ->
        primary ?: fallback
    }.flatMapLatest { ayah ->
        if (ayah == null) {
            flowOf(null)
        } else {
            combine(
                quranDao.observeSurah(ayah.surahId),
                quranDao.observeAyahTranslations(listOf(ayah.id))
            ) { surah, translations ->
                Triple(ayah, surah, translations)
            }
        }
    }

    private val lastSurahFlow = userLibraryDao.observeReadingProgress()
        .map { it?.lastSurahId ?: 1 }
        .distinctUntilChanged()
        .flatMapLatest { quranDao.observeSurah(it) }

    private val homePrefsFlow = preferencesRepository.preferencesFlow
        .map { p ->
            p.copy(
                lastRecitedSurahId = 0,
                lastRecitedAyahNumber = 0,
                lastRecitedReciterId = 7,
                lastRecitedPositionMs = 0L,
                arabicFontSizeSp = 28f,
                wbwFontSizeSp = 13f,
                translationFontSizeSp = 16f,
                audioPlaybackSpeed = 1.0f,
                audioRepeatPerAyah = 1,
                audioPauseSeconds = 0
            )
        }
        .distinctUntilChanged()

    private val coreHomeFlow = combine(
        homePrefsFlow,
        userLibraryDao.observeReadingProgress().distinctUntilChanged(),
        userLibraryDao.observeRecentStreaks().distinctUntilChanged(),
        lastSurahFlow.distinctUntilChanged(),
        userLibraryDao.observeBookmarks().distinctUntilChanged()
    ) { prefs, progress, streaks, lastSurah, bookmarks ->
        Triple(prefs, progress, Triple(streaks, lastSurah, bookmarks))
    }

    val uiState: StateFlow<HomeUiState> = combine(
        coreHomeFlow,
        verseOfTheDayFlow,
        _tasbeehPhraseIdx
    ) { core, votdTriple, phraseIdx ->
        val prefs = core.first
        val progress = core.second ?: ReadingProgressEntity()
        val (streaks, lastSurah, bookmarks) = core.third

        val todayKey = StreakCalculator.todayDateKey()
        val todayStreak = streaks.firstOrNull { it.dateKey == todayKey }
        val streakDays = StreakCalculator.calculateCurrentStreak(streaks, todayKey)

        val lastSurahName = lastSurah?.let {
            BidiUtils.formatSurahTitle(it.nameTransliteration, it.localizedNamesJson, prefs.uiLanguageCode)
        } ?: "Al-Fatihah"

        val bookmarkedKeys = bookmarks.map { it.verseKey }.toSet()
        val votdItem = votdTriple?.let { (ayah, surah, translations) ->
            val sName = surah?.let {
                BidiUtils.formatSurahTitle(it.nameTransliteration, it.localizedNamesJson, prefs.uiLanguageCode)
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

        val resolvedUtcOffset = PrayerTimeCalculator.resolveUtcOffsetForLocation(
            locationName = prefs.locationName,
            latitude = prefs.latitude,
            longitude = prefs.longitude
        )

        HomeUiState(
            preferences = prefs,
            progress = progress,
            lastSurahName = lastSurahName,
            streaks = streaks,
            currentStreakDays = streakDays,
            todayAyahsRead = todayStreak?.ayahsReadCount ?: 0,
            todayTasbeehCount = todayStreak?.tasbeehCount ?: 0,
            verseOfTheDay = votdItem,
            prayerTimes = PrayerTimeCalculator.calculateDailyTimes(
                latitude = prefs.latitude,
                longitude = prefs.longitude,
                hanafiAsr = prefs.hanafiAsr,
                timeZoneOffsetHours = resolvedUtcOffset
            ),
            qiblaBearingDegrees = PrayerTimeCalculator.calculateQiblaBearing(prefs.latitude, prefs.longitude),
            distanceToKaabaKm = PrayerTimeCalculator.calculateDistanceToKaabaKm(prefs.latitude, prefs.longitude),
            gregorianDate = gregorian,
            hijriDate = hijri,
            tasbeehPhraseIndex = phraseIdx
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
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

    fun setHanafiAsr(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHanafiAsr(enabled)
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
