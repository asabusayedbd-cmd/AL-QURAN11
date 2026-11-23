package com.example.core.ibadah

import com.example.core.database.HabitStreakEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class KhatamPlanEstimate(
    val totalAyahsInQuran: Int = 6236,
    val completedAyahs: Int,
    val remainingAyahs: Int,
    val requiredAyahsPerDay: Int,
    val completionPercentage: Float
)

object StreakCalculator {

    private const val TOTAL_QURAN_AYAHS = 6236

    /**
     * Calculates consecutive active days ending today (or yesterday if today has 0 ayahs read so far).
     */
    fun calculateCurrentStreak(
        streaks: List<HabitStreakEntity>,
        todayDateKey: String = todayDateKey()
    ): Int {
        if (streaks.isEmpty()) return 0
        val activeDates = streaks.filter { it.ayahsReadCount > 0 }.map { it.dateKey }.toHashSet()
        if (activeDates.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = sdf.parse(todayDateKey) ?: Date()

        // If today has not been logged yet, start checking from yesterday so active streaks don't vanish midday
        if (todayDateKey !in activeDates) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayKey = sdf.format(cal.time)
            if (yesterdayKey !in activeDates) return 0
        }

        var count = 0
        while (true) {
            val key = sdf.format(cal.time)
            if (key in activeDates) {
                count++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return count
    }

    fun calculateKhatamPlan(completedAyahs: Int, targetDays: Int): KhatamPlanEstimate {
        val safeCompleted = completedAyahs.coerceIn(0, TOTAL_QURAN_AYAHS)
        val safeDays = targetDays.coerceIn(1, 3650)
        val remaining = TOTAL_QURAN_AYAHS - safeCompleted
        val perDay = if (remaining == 0) 0 else ((remaining + safeDays - 1) / safeDays).coerceAtLeast(1)
        val pct = (safeCompleted.toFloat() / TOTAL_QURAN_AYAHS.toFloat()).coerceIn(0f, 1f)
        return KhatamPlanEstimate(
            totalAyahsInQuran = TOTAL_QURAN_AYAHS,
            completedAyahs = safeCompleted,
            remainingAyahs = remaining,
            requiredAyahsPerDay = perDay,
            completionPercentage = pct
        )
    }

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
    }
}
