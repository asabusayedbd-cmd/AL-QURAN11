package com.example.core.perf

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class ReaderPerfTrace(
    val surahId: Int,
    val tapTimestampNs: Long,
    val readerEnteredMs: Double? = null,
    val localDataReadyMs: Double? = null,
    val firstVisibleContentMs: Double? = null,
    val optionalSyncStartMs: Double? = null,
    val optionalSyncCompleteMs: Double? = null,
    val stateEmissionsBeforeVisible: Int = 0,
    val mushafGroupingSkipped: Boolean = false,
    val ayahCount: Int = 0
) {
    val navToReaderMs: Double?
        get() = readerEnteredMs

    val localLoadToVisibleRenderMs: Double?
        get() = if (localDataReadyMs != null && firstVisibleContentMs != null) {
            (firstVisibleContentMs - localDataReadyMs).coerceAtLeast(0.0)
        } else null
}

object PerfTracker {
    private const val TAG = "NoorPerf"

    @Volatile
    private var appStartNs: Long = System.nanoTime()

    @Volatile
    private var activityCreatedMs: Double? = null

    @Volatile
    private var welcomeDisplayedMs: Double? = null

    @Volatile
    private var firstScreenReadyMs: Double? = null

    @Volatile
    private var currentTrace: ReaderPerfTrace? = null

    private val preVisibleEmissions = AtomicInteger(0)

    private val _lastTrace = MutableStateFlow<ReaderPerfTrace?>(null)
    val lastTrace: StateFlow<ReaderPerfTrace?> = _lastTrace.asStateFlow()

    fun markAppStart() {
        appStartNs = System.nanoTime()
        log("app_start")
    }

    fun markActivityCreated() {
        val elapsedMs = (System.nanoTime() - appStartNs) / 1_000_000.0
        activityCreatedMs = elapsedMs
        log("activity_created elapsedMs=${fmt(elapsedMs)}")
    }

    fun markStartupWelcomeShown() {
        if (welcomeDisplayedMs == null) {
            val elapsedMs = (System.nanoTime() - appStartNs) / 1_000_000.0
            welcomeDisplayedMs = elapsedMs
            log("startup_welcome_shown elapsedMs=${fmt(elapsedMs)}")
        }
    }

    fun markStartupFirstScreenReady() {
        if (firstScreenReadyMs == null) {
            val elapsedMs = (System.nanoTime() - appStartNs) / 1_000_000.0
            firstScreenReadyMs = elapsedMs
            log("startup_first_screen_ready elapsedMs=${fmt(elapsedMs)}")
        }
    }

    fun markSurahTapped(surahId: Int) {
        val now = System.nanoTime()
        preVisibleEmissions.set(0)
        val trace = ReaderPerfTrace(
            surahId = surahId,
            tapTimestampNs = now
        )
        currentTrace = trace
        _lastTrace.value = trace
        log("surah_tapped surahId=$surahId")
    }

    fun markReaderEntered(surahId: Int) {
        val now = System.nanoTime()
        val existing = currentTrace
        val baseTrace = if (existing != null && existing.surahId == surahId) {
            existing
        } else {
            preVisibleEmissions.set(0)
            ReaderPerfTrace(surahId = surahId, tapTimestampNs = now)
        }
        if (baseTrace.readerEnteredMs != null) return
        val elapsedMs = (now - baseTrace.tapTimestampNs) / 1_000_000.0
        val updated = baseTrace.copy(readerEnteredMs = elapsedMs)
        currentTrace = updated
        _lastTrace.value = updated
        log("reader_entered surahId=$surahId navMs=${fmt(elapsedMs)}")
    }

    fun recordReaderStateEmission(surahId: Int, hasAyahs: Boolean) {
        val cur = currentTrace ?: return
        if (cur.surahId != surahId || cur.firstVisibleContentMs != null) return
        val count = preVisibleEmissions.incrementAndGet()
        if (hasAyahs && cur.localDataReadyMs == null) {
            markLocalDataReady(surahId, 0)
        } else {
            val updated = currentTrace?.copy(stateEmissionsBeforeVisible = count)
            if (updated != null) {
                currentTrace = updated
                _lastTrace.value = updated
            }
        }
    }

    fun markLocalDataReady(surahId: Int, ayahCount: Int) {
        val now = System.nanoTime()
        val cur = currentTrace ?: ReaderPerfTrace(surahId = surahId, tapTimestampNs = now)
        if (cur.surahId != surahId) return
        if (cur.localDataReadyMs != null && cur.ayahCount > 0) return
        val elapsedMs = (now - cur.tapTimestampNs) / 1_000_000.0
        val updated = cur.copy(
            localDataReadyMs = cur.localDataReadyMs ?: elapsedMs,
            ayahCount = if (ayahCount > 0) ayahCount else cur.ayahCount,
            stateEmissionsBeforeVisible = preVisibleEmissions.get()
        )
        currentTrace = updated
        _lastTrace.value = updated
        log("local_data_ready surahId=$surahId ayahs=${updated.ayahCount} elapsedMs=${fmt(updated.localDataReadyMs ?: elapsedMs)}")
    }

    fun markFirstVisibleReaderContent(surahId: Int, mushafGroupingSkipped: Boolean) {
        val now = System.nanoTime()
        val cur = currentTrace ?: return
        if (cur.surahId != surahId || cur.firstVisibleContentMs != null) return
        val elapsedMs = (now - cur.tapTimestampNs) / 1_000_000.0
        val updated = cur.copy(
            firstVisibleContentMs = elapsedMs,
            mushafGroupingSkipped = mushafGroupingSkipped,
            stateEmissionsBeforeVisible = preVisibleEmissions.get()
        )
        currentTrace = updated
        _lastTrace.value = updated
        log(
            "first_visible_content surahId=$surahId totalMs=${fmt(elapsedMs)} " +
                "navMs=${fmt(updated.navToReaderMs)} localMs=${fmt(updated.localDataReadyMs)} " +
                "renderDeltaMs=${fmt(updated.localLoadToVisibleRenderMs)} " +
                "emissions=${updated.stateEmissionsBeforeVisible} mushafGroupingSkipped=$mushafGroupingSkipped"
        )
    }

    fun markOptionalSyncStarted(surahId: Int, reason: String) {
        val now = System.nanoTime()
        val cur = currentTrace ?: return
        if (cur.surahId != surahId) return
        val elapsedMs = (now - cur.tapTimestampNs) / 1_000_000.0
        if (cur.optionalSyncStartMs == null) {
            val updated = cur.copy(optionalSyncStartMs = elapsedMs)
            currentTrace = updated
            _lastTrace.value = updated
        }
        log("optional_sync_start surahId=$surahId reason=$reason atMs=${fmt(elapsedMs)}")
    }

    fun markOptionalSyncCompleted(surahId: Int) {
        val now = System.nanoTime()
        val cur = currentTrace ?: return
        if (cur.surahId != surahId) return
        val elapsedMs = (now - cur.tapTimestampNs) / 1_000_000.0
        val updated = cur.copy(optionalSyncCompleteMs = elapsedMs)
        currentTrace = updated
        _lastTrace.value = updated
        log("optional_sync_complete surahId=$surahId atMs=${fmt(elapsedMs)}")
    }

    fun resetForTest() {
        preVisibleEmissions.set(0)
        currentTrace = null
        _lastTrace.value = null
    }

    private fun fmt(value: Double?): String = value?.let { String.format("%.2f", it) } ?: "n/a"

    private fun log(msg: String) {
        runCatching { Log.i(TAG, msg) }
    }
}
