package com.example.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.database.HabitStreakEntity
import com.example.core.database.NoorDatabase
import com.example.core.database.ReadingProgressEntity
import com.example.core.database.UserBookmarkEntity
import com.example.core.database.UserCollectionEntity
import com.example.core.database.UserNoteEntity
import com.example.core.ibadah.KhatamPlanEstimate
import com.example.core.ibadah.StreakCalculator
import com.example.core.localization.PreferencesRepository
import com.example.core.localization.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val collections: List<UserCollectionEntity> = emptyList(),
    val bookmarks: List<UserBookmarkEntity> = emptyList(),
    val notes: List<UserNoteEntity> = emptyList(),
    val progress: ReadingProgressEntity = ReadingProgressEntity(),
    val khatamEstimate: KhatamPlanEstimate = StreakCalculator.calculateKhatamPlan(0, 30),
    val currentStreakDays: Int = 0,
    val todayAyahsRead: Int = 0,
    val preferences: UserPreferences = UserPreferences()
)

class LibraryViewModel(
    database: NoorDatabase,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val userLibraryDao = database.userLibraryDao()

    val uiState: StateFlow<LibraryUiState> = combine(
        userLibraryDao.observeCollections(),
        userLibraryDao.observeBookmarks(),
        userLibraryDao.observeNotes(),
        userLibraryDao.observeReadingProgress(),
        userLibraryDao.observeRecentStreaks(),
        preferencesRepository.preferencesFlow
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val collections = array[0] as List<UserCollectionEntity>
        @Suppress("UNCHECKED_CAST")
        val bookmarks = array[1] as List<UserBookmarkEntity>
        @Suppress("UNCHECKED_CAST")
        val notes = array[2] as List<UserNoteEntity>
        val progress = (array[3] as ReadingProgressEntity?) ?: ReadingProgressEntity()
        @Suppress("UNCHECKED_CAST")
        val streaks = array[4] as List<HabitStreakEntity>
        val prefs = array[5] as UserPreferences

        val todayKey = StreakCalculator.todayDateKey()
        val todayAyahs = streaks.firstOrNull { it.dateKey == todayKey }?.ayahsReadCount ?: 0

        LibraryUiState(
            collections = collections,
            bookmarks = bookmarks,
            notes = notes,
            progress = progress,
            khatamEstimate = StreakCalculator.calculateKhatamPlan(
                completedAyahs = progress.khatamCompletedAyahs,
                targetDays = progress.khatamTargetDays
            ),
            currentStreakDays = StreakCalculator.calculateCurrentStreak(streaks, todayKey),
            todayAyahsRead = todayAyahs,
            preferences = prefs
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState()
        )

    fun createCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val col = UserCollectionEntity(
                id = "col_$now",
                name = trimmed,
                colorHex = "#0F5C4D",
                createdAtEpochMs = now
            )
            userLibraryDao.insertCollection(col)
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            userLibraryDao.deleteBookmark(bookmarkId)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            userLibraryDao.deleteNote(noteId)
        }
    }

    fun updateKhatamTargetDays(days: Int) {
        viewModelScope.launch {
            val current = userLibraryDao.getReadingProgress() ?: ReadingProgressEntity()
            val updated = current.copy(
                khatamTargetDays = days.coerceIn(7, 365),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            userLibraryDao.upsertReadingProgress(updated)
        }
    }

    fun updateDailyGoal(ayahGoal: Int) {
        viewModelScope.launch {
            val current = userLibraryDao.getReadingProgress() ?: ReadingProgressEntity()
            val updated = current.copy(
                dailyAyahGoal = ayahGoal.coerceIn(5, 300),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            userLibraryDao.upsertReadingProgress(updated)
        }
    }
}
