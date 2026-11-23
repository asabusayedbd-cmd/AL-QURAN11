package com.example.core.audio

import org.json.JSONArray
import java.util.Locale

data class WordAudioSegment(
    val position: Int,
    val startMs: Long,
    val endMs: Long
)

data class HifzLoopConfig(
    val startAyah: Int = 1,
    val endAyah: Int = 7,
    val repeatPerAyah: Int = 1, // 1, 2, 3, 5, 10, or 0 for infinite
    val pauseSecondsAfterAyah: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val loopRangeEnabled: Boolean = false,
    val wordByWordMode: Boolean = false,
    val autoPlayNextSurah: Boolean = true
) {
    fun normalizedRange(maxAyahInSurah: Int): IntRange {
        val safeMax = maxAyahInSurah.coerceAtLeast(1)
        val lo = minOf(startAyah, endAyah).coerceIn(1, safeMax)
        val hi = maxOf(startAyah, endAyah).coerceIn(1, safeMax)
        return lo..hi
    }
}

sealed interface LoopAdvanceResult {
    data class PlayAyah(val ayahNumber: Int, val iterationOfAyah: Int, val delayMsBeforePlay: Long) : LoopAdvanceResult
    data object Completed : LoopAdvanceResult
}

object HifzLoopEngine {

    /**
     * Computes the next Ayah and repeat iteration when the current Ayah finishes playing.
     */
    fun nextStep(
        currentAyah: Int,
        currentIteration: Int,
        config: HifzLoopConfig,
        maxAyahInSurah: Int
    ): LoopAdvanceResult {
        val range = config.normalizedRange(maxAyahInSurah)
        val safeCurrent = currentAyah.coerceIn(1, maxAyahInSurah.coerceAtLeast(1))
        val pauseMs = (config.pauseSecondsAfterAyah.coerceIn(0, 15)) * 1000L

        // Infinite repeat on current Ayah
        if (config.repeatPerAyah == 0) {
            return LoopAdvanceResult.PlayAyah(
                ayahNumber = safeCurrent,
                iterationOfAyah = currentIteration + 1,
                delayMsBeforePlay = pauseMs
            )
        }

        // More repeats remaining for the current Ayah
        if (currentIteration < config.repeatPerAyah) {
            return LoopAdvanceResult.PlayAyah(
                ayahNumber = safeCurrent,
                iterationOfAyah = currentIteration + 1,
                delayMsBeforePlay = pauseMs
            )
        }

        // Advance to next Ayah inside loop range (if enabled) or up to maxAyahInSurah
        val upperBound = if (config.loopRangeEnabled) range.last else maxAyahInSurah.coerceAtLeast(1)
        val lowerBound = if (config.loopRangeEnabled) range.first else 1

        return if (safeCurrent < upperBound) {
            LoopAdvanceResult.PlayAyah(
                ayahNumber = safeCurrent + 1,
                iterationOfAyah = 1,
                delayMsBeforePlay = pauseMs
            )
        } else if (config.loopRangeEnabled) {
            LoopAdvanceResult.PlayAyah(
                ayahNumber = lowerBound,
                iterationOfAyah = 1,
                delayMsBeforePlay = pauseMs
            )
        } else {
            LoopAdvanceResult.Completed
        }
    }

    fun parseWordSegments(json: String): List<WordAudioSegment> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        WordAudioSegment(
                            position = obj.getInt("position"),
                            startMs = obj.optLong("startMs", 0L),
                            endMs = obj.optLong("endMs", 0L)
                        )
                    )
                }
            }.sortedBy { it.position }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findActiveWordPosition(segments: List<WordAudioSegment>, positionMs: Long): Int? {
        if (segments.isEmpty() || positionMs < 0L) return null
        return segments.firstOrNull { positionMs in it.startMs..it.endMs }?.position
            ?: segments.lastOrNull { positionMs >= it.startMs }?.position
    }

    fun estimateWordPositionByProgress(wordCount: Int, positionMs: Long, durationMs: Long): Int? {
        if (wordCount <= 0 || durationMs <= 0L || positionMs < 0L) return null
        val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.999f)
        return ((fraction * wordCount).toInt() + 1).coerceIn(1, wordCount)
    }

    fun formatDurationMmSs(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val mins = totalSec / 60
        val secs = totalSec % 60
        return "%d:%02d".format(Locale.US, mins, secs)
    }
}
