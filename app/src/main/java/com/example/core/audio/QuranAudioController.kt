package com.example.core.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core.database.NoorDatabase
import com.example.core.database.WordEntity
import com.example.core.localization.BidiUtils
import com.example.core.localization.PreferencesRepository
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class AudioDownloadProgress(
    val surahId: Int,
    val reciterId: Int,
    val downloadedCount: Int,
    val totalCount: Int
)

data class AudioPlaybackState(
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val surahId: Int = 1,
    val surahName: String = "Al-Fatihah",
    val ayahNumber: Int = 1,
    val maxAyahInSurah: Int = 7,
    val activeWordPosition: Int? = null,
    val totalWordsInAyah: Int = 0,
    val reciterId: Int = 7,
    val reciterName: String = "Mishary Rashid Alafasy",
    val currentIteration: Int = 1,
    val hifzConfig: HifzLoopConfig = HifzLoopConfig(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val pauseRemainingSec: Int = 0,
    val sleepRemainingSec: Int? = null,
    val isPlayingFromOfflineCache: Boolean = false,
    val audioDownloadProgress: AudioDownloadProgress? = null,
    val offlineCacheSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val lastRecitedSurahId: Int = 1,
    val lastRecitedAyahNumber: Int = 1,
    val lastRecitedPositionMs: Long = 0L,
    val hasSavedRecitation: Boolean = false
)

class QuranAudioController(
    private val context: Context,
    private val database: NoorDatabase,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
    private val preferencesRepository: PreferencesRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val quranDao = database.quranDao()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var wordPollingJob: Job? = null
    private var pendingPauseJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var audioDownloadJob: Job? = null

    private var currentSegments: List<WordAudioSegment> = emptyList()
    private var currentAyahWords: List<WordEntity> = emptyList()
    private var wbwWordIndex: Int = 0
    private var isPlayingSingleWord: Boolean = false
    private var lastLoadedAyahId: Int = -1
    private var prefsInitialized: Boolean = false
    private var pendingResumeSeekMs: Long = 0L

    private val _state = MutableStateFlow(AudioPlaybackState())
    val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    init {
        scope.launch {
            preferencesRepository?.preferencesFlow?.first()?.let { prefs ->
                if (!prefsInitialized) {
                    prefsInitialized = true
                    val safeReciterId = if (prefs.activeReciterId == 75) 7 else prefs.activeReciterId
                    val savedSurahId = prefs.lastRecitedSurahId
                    val savedAyah = prefs.lastRecitedAyahNumber
                    val hasSaved = savedSurahId in 1..114 && savedAyah > 0
                    val savedSurah = if (hasSaved) quranDao.getSurah(savedSurahId) else null
                    val savedReciter = quranDao.getReciter(safeReciterId)
                    val lang = prefs.uiLanguageCode
                    val resolvedSurahName = savedSurah?.let {
                        BidiUtils.extractLocalizedSurahName(it.localizedNamesJson, lang).ifBlank { it.nameTransliteration }
                    }
                    val resolvedReciterName = savedReciter?.name ?: "Mishary Rashid Alafasy"
                    _state.update { current ->
                        current.copy(
                            surahId = if (hasSaved) savedSurahId else current.surahId,
                            surahName = resolvedSurahName ?: current.surahName,
                            ayahNumber = if (hasSaved) savedAyah else current.ayahNumber,
                            maxAyahInSurah = savedSurah?.ayahCount ?: current.maxAyahInSurah,
                            reciterId = safeReciterId,
                            reciterName = resolvedReciterName,
                            lastRecitedSurahId = if (hasSaved) savedSurahId else 1,
                            lastRecitedAyahNumber = if (hasSaved) savedAyah else 1,
                            lastRecitedPositionMs = prefs.lastRecitedPositionMs,
                            hasSavedRecitation = hasSaved,
                            hifzConfig = current.hifzConfig.copy(
                                playbackSpeed = prefs.audioPlaybackSpeed,
                                repeatPerAyah = prefs.audioRepeatPerAyah,
                                pauseSecondsAfterAyah = prefs.audioPauseSeconds,
                                wordByWordMode = prefs.audioWordByWordMode,
                                autoPlayNextSurah = prefs.autoPlayNextSurah
                            )
                        )
                    }
                }
            }
            refreshCacheSize()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying, isBuffering = false) }
            if (isPlaying) {
                startWordPositionPolling()
            } else {
                wordPollingJob?.cancel()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                Player.STATE_READY -> {
                    val controller = mediaController
                    val seekTarget = pendingResumeSeekMs
                    val duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
                    if (seekTarget > 1200L && controller != null && (duration <= 0L || seekTarget < duration - 300L)) {
                        pendingResumeSeekMs = 0L
                        controller.seekTo(seekTarget)
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                positionMs = seekTarget,
                                durationMs = duration,
                                errorMessage = null
                            )
                        }
                    } else {
                        pendingResumeSeekMs = 0L
                        _state.update {
                            it.copy(
                                isBuffering = false,
                                durationMs = duration,
                                errorMessage = null
                            )
                        }
                    }
                }
                Player.STATE_ENDED -> handleMediaEnded()
                Player.STATE_IDLE -> _state.update { it.copy(isBuffering = false) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            pendingResumeSeekMs = 0L
            Log.e("QuranAudio", "Playback error", error)
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = error.localizedMessage ?: "Audio stream unavailable offline"
                )
            }
        }
    }

    private fun ensureControllerConnected(onConnected: (MediaController) -> Unit) {
        val existing = mediaController
        if (existing != null) {
            onConnected(existing)
            return
        }
        val future = controllerFuture ?: run {
            val token = SessionToken(context, ComponentName(context, QuranPlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().also { controllerFuture = it }
        }
        future.addListener({
            try {
                val controller = future.get()
                if (mediaController !== controller) {
                    mediaController = controller
                    controller.addListener(playerListener)
                }
                onConnected(controller)
            } catch (e: Exception) {
                Log.e("QuranAudio", "Failed connecting MediaController", e)
                _state.update { it.copy(errorMessage = "Audio service unavailable") }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun persistLastRecited(surahId: Int, ayahNumber: Int, reciterId: Int, positionMs: Long) {
        if (surahId !in 1..114 || ayahNumber <= 0) return
        val safeReciter = if (reciterId == 75) 7 else reciterId
        val safePos = positionMs.coerceAtLeast(0L)
        _state.update {
            it.copy(
                lastRecitedSurahId = surahId,
                lastRecitedAyahNumber = ayahNumber,
                lastRecitedPositionMs = safePos,
                hasSavedRecitation = true
            )
        }
        scope.launch {
            preferencesRepository?.setLastRecited(surahId, ayahNumber, safeReciter, safePos)
        }
    }

    fun resumeLastRecitation() {
        scope.launch {
            val prefs = preferencesRepository?.preferencesFlow?.first()
            val cur = _state.value
            val targetSurahId = prefs?.lastRecitedSurahId?.takeIf { it in 1..114 }
                ?: cur.lastRecitedSurahId.coerceIn(1, 114)
            val targetAyah = prefs?.lastRecitedAyahNumber?.takeIf { it > 0 }
                ?: cur.lastRecitedAyahNumber.coerceAtLeast(1)
            val rawReciter = prefs?.lastRecitedReciterId ?: cur.reciterId
            val safeReciterId = if (rawReciter == 75) 7 else rawReciter
            val savedPosMs = prefs?.lastRecitedPositionMs ?: cur.lastRecitedPositionMs
            val surah = quranDao.getSurah(targetSurahId)
            val reciter = quranDao.getReciter(safeReciterId)
            val lang = prefs?.uiLanguageCode ?: "en"
            val surahName = surah?.let {
                BidiUtils.extractLocalizedSurahName(it.localizedNamesJson, lang).ifBlank { it.nameTransliteration }
            } ?: cur.surahName
            val maxAyah = surah?.ayahCount ?: cur.maxAyahInSurah
            val reciterName = reciter?.name ?: cur.reciterName

            playAyah(
                surahId = targetSurahId,
                ayahNumber = targetAyah.coerceIn(1, maxAyah.coerceAtLeast(1)),
                surahName = surahName,
                maxAyahInSurah = maxAyah,
                reciterId = safeReciterId,
                reciterName = reciterName,
                iteration = 1,
                initialPositionMs = savedPosMs
            )
        }
    }

    fun playAyah(
        surahId: Int,
        ayahNumber: Int,
        surahName: String,
        maxAyahInSurah: Int,
        reciterId: Int,
        reciterName: String,
        iteration: Int = 1,
        initialPositionMs: Long = 0L
    ) {
        pendingPauseJob?.cancel()
        isPlayingSingleWord = false
        wbwWordIndex = 0
        val safeReciterId = if (reciterId == 75) 7 else reciterId
        pendingResumeSeekMs = initialPositionMs.coerceAtLeast(0L)

        scope.launch {
            val verseKey = "$surahId:$ayahNumber"
            val ayah = quranDao.getAyahByKey(verseKey)
            val reciterSegment = if (ayah != null) quranDao.getAudioSegment(safeReciterId, ayah.id) else null
            val timingSegment = reciterSegment ?: if (ayah != null) quranDao.getAudioSegment(7, ayah.id) else null
            currentSegments = timingSegment?.wordTimestampSegmentsJson?.let { HifzLoopEngine.parseWordSegments(it) }
                ?: emptyList()
            currentAyahWords = if (ayah != null) quranDao.getWordsForAyah(ayah.id) else emptyList()
            lastLoadedAyahId = ayah?.id ?: -1

            val reciter = quranDao.getReciter(safeReciterId)
            val resolvedReciterName = reciter?.name ?: reciterName
            persistLastRecited(surahId, ayahNumber, safeReciterId, pendingResumeSeekMs)
            val wbwMode = _state.value.hifzConfig.wordByWordMode && currentAyahWords.isNotEmpty()

            if (wbwMode) {
                pendingResumeSeekMs = 0L
                val firstWord = currentAyahWords.first()
                val wordUri = buildWordAudioUri(firstWord, surahId, ayahNumber)
                _state.update {
                    it.copy(
                        isVisible = true,
                        isBuffering = true,
                        surahId = surahId,
                        surahName = surahName,
                        ayahNumber = ayahNumber,
                        maxAyahInSurah = maxAyahInSurah,
                        reciterId = safeReciterId,
                        reciterName = resolvedReciterName,
                        currentIteration = iteration,
                        activeWordPosition = firstWord.position,
                        totalWordsInAyah = currentAyahWords.size,
                        positionMs = 0L,
                        durationMs = 0L,
                        pauseRemainingSec = 0,
                        isPlayingFromOfflineCache = false,
                        errorMessage = null
                    )
                }
                playUriInController(
                    uri = wordUri,
                    title = "$surahName ($verseKey • Word ${firstWord.position}/${currentAyahWords.size})",
                    artist = "Word-by-Word Recitation",
                    speed = _state.value.hifzConfig.playbackSpeed
                )
            } else {
                val cachedFile = localAyahCacheFile(safeReciterId, surahId, ayahNumber)
                val useCached = cachedFile.exists() && cachedFile.length() > 1024L
                val uri = if (useCached) {
                    Uri.fromFile(cachedFile).toString()
                } else {
                    reciterSegment?.localFilePath?.takeIf { File(it).exists() }?.let { Uri.fromFile(File(it)).toString() }
                        ?: reciterSegment?.audioUrl?.takeIf { it.isNotBlank() }
                        ?: buildReciterAudioUrl(reciter?.baseUrl, safeReciterId, surahId, ayahNumber)
                }

                _state.update {
                    it.copy(
                        isVisible = true,
                        isBuffering = true,
                        surahId = surahId,
                        surahName = surahName,
                        ayahNumber = ayahNumber,
                        maxAyahInSurah = maxAyahInSurah,
                        reciterId = safeReciterId,
                        reciterName = resolvedReciterName,
                        currentIteration = iteration,
                        activeWordPosition = null,
                        totalWordsInAyah = currentAyahWords.size,
                        positionMs = pendingResumeSeekMs,
                        durationMs = 0L,
                        pauseRemainingSec = 0,
                        isPlayingFromOfflineCache = useCached,
                        errorMessage = null
                    )
                }

                playUriInController(
                    uri = uri,
                    title = "$surahName ($verseKey)",
                    artist = resolvedReciterName,
                    speed = _state.value.hifzConfig.playbackSpeed
                )
            }
        }
    }

    private fun playUriInController(
        uri: String,
        title: String,
        artist: String,
        speed: Float
    ) {
        ensureControllerConnected { controller ->
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle("Al-Quran")
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()
            controller.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f))
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun playSingleWord(
        wordAudioUrl: String?,
        fallbackSurahId: Int,
        fallbackAyah: Int,
        wordPosition: Int,
        speed: Float = 1.0f
    ) {
        pendingPauseJob?.cancel()
        val url = wordAudioUrl?.takeIf { it.isNotBlank() } ?: run {
            val s = fallbackSurahId.toString().padStart(3, '0')
            val a = fallbackAyah.toString().padStart(3, '0')
            val p = wordPosition.toString().padStart(3, '0')
            "https://audio.qurancdn.com/wbw/${s}_${a}_${p}.mp3"
        }
        isPlayingSingleWord = true
        lastLoadedAyahId = -1
        currentSegments = emptyList()
        _state.update { it.copy(activeWordPosition = wordPosition, pauseRemainingSec = 0, errorMessage = null) }
        ensureControllerConnected { controller ->
            val item = MediaItem.fromUri(url)
            controller.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f))
            controller.setMediaItem(item)
            controller.prepare()
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        val clamped = positionMs.coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(clamped)
        _state.update { it.copy(positionMs = clamped) }
        val s = _state.value
        persistLastRecited(s.surahId, s.ayahNumber, s.reciterId, clamped)
    }

    fun seekRelative(deltaMs: Long) {
        val controller = mediaController ?: return
        seekTo(controller.currentPosition + deltaMs)
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller == null) {
            val s = _state.value
            playAyah(
                surahId = s.surahId,
                ayahNumber = s.ayahNumber,
                surahName = s.surahName,
                maxAyahInSurah = s.maxAyahInSurah,
                reciterId = s.reciterId,
                reciterName = s.reciterName,
                initialPositionMs = s.lastRecitedPositionMs
            )
            return
        }
        if (controller.isPlaying) {
            val pos = controller.currentPosition.coerceAtLeast(0L)
            controller.pause()
            val s = _state.value
            persistLastRecited(s.surahId, s.ayahNumber, s.reciterId, pos)
        } else {
            pendingPauseJob?.cancel()
            _state.update { it.copy(pauseRemainingSec = 0) }
            controller.play()
        }
    }

    fun skipToNextAyah() {
        val s = _state.value
        if (s.ayahNumber < s.maxAyahInSurah) {
            playAyah(s.surahId, s.ayahNumber + 1, s.surahName, s.maxAyahInSurah, s.reciterId, s.reciterName)
        } else if (s.surahId < 114) {
            playNextSurahFromStart(s.surahId + 1, s.reciterId, s.reciterName)
        }
    }

    fun skipToPreviousAyah() {
        val s = _state.value
        val prev = (s.ayahNumber - 1).coerceAtLeast(1)
        playAyah(s.surahId, prev, s.surahName, s.maxAyahInSurah, s.reciterId, s.reciterName)
    }

    fun stopAndDismiss() {
        pendingPauseJob?.cancel()
        wordPollingJob?.cancel()
        sleepTimerJob?.cancel()
        val s = _state.value
        val capturedPos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: s.positionMs
        if (s.isVisible) {
            persistLastRecited(s.surahId, s.ayahNumber, s.reciterId, capturedPos)
        }
        mediaController?.let {
            it.stop()
            it.clearMediaItems()
        }
        isPlayingSingleWord = false
        _state.update {
            it.copy(
                isVisible = false,
                isPlaying = false,
                activeWordPosition = null,
                pauseRemainingSec = 0,
                sleepRemainingSec = null
            )
        }
    }

    fun updateHifzConfig(transform: (HifzLoopConfig) -> HifzLoopConfig) {
        val prevMode = _state.value.hifzConfig.wordByWordMode
        var newConfig = _state.value.hifzConfig
        _state.update { current ->
            val updated = transform(current.hifzConfig)
            newConfig = updated
            mediaController?.playbackParameters = PlaybackParameters(updated.playbackSpeed.coerceIn(0.5f, 2.0f))
            current.copy(hifzConfig = updated)
        }
        scope.launch {
            preferencesRepository?.let { repo ->
                repo.setAudioPlaybackSpeed(newConfig.playbackSpeed)
                repo.setAudioRepeatPerAyah(newConfig.repeatPerAyah)
                repo.setAudioPauseSeconds(newConfig.pauseSecondsAfterAyah)
                repo.setAudioWordByWordMode(newConfig.wordByWordMode)
                repo.setAutoPlayNextSurah(newConfig.autoPlayNextSurah)
            }
        }
        // If user switches Ayah <-> WBW mode while player is visible, immediately restart current Ayah in chosen mode
        val current = _state.value
        if (prevMode != newConfig.wordByWordMode && current.isVisible) {
            playAyah(
                current.surahId,
                current.ayahNumber,
                current.surahName,
                current.maxAyahInSurah,
                current.reciterId,
                current.reciterName,
                current.currentIteration
            )
        }
    }

    fun updateReciter(reciterId: Int, reciterName: String) {
        val safeReciterId = if (reciterId == 75) 7 else reciterId
        val current = _state.value
        _state.update { it.copy(reciterId = safeReciterId, reciterName = reciterName) }
        if (current.isVisible) {
            playAyah(
                current.surahId,
                current.ayahNumber,
                current.surahName,
                current.maxAyahInSurah,
                safeReciterId,
                reciterName
            )
        }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepRemainingSec = null) }
            return
        }
        var remaining = minutes * 60
        _state.update { it.copy(sleepRemainingSec = remaining) }
        sleepTimerJob = scope.launch {
            while (remaining > 0 && isActive) {
                delay(1000L)
                remaining -= 1
                _state.update { it.copy(sleepRemainingSec = remaining.takeIf { sec -> sec > 0 }) }
            }
            if (isActive) {
                pendingPauseJob?.cancel()
                wordPollingJob?.cancel()
                val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: _state.value.positionMs
                mediaController?.pause()
                val s = _state.value
                persistLastRecited(s.surahId, s.ayahNumber, s.reciterId, pos)
                _state.update { it.copy(isPlaying = false, sleepRemainingSec = null, pauseRemainingSec = 0) }
            }
        }
    }

    fun downloadSurahAudioForQari(surahId: Int, reciterId: Int) {
        audioDownloadJob?.cancel()
        audioDownloadJob = scope.launch {
            try {
                val surah = quranDao.getSurah(surahId) ?: return@launch
                val reciter = quranDao.getReciter(reciterId) ?: return@launch
                val total = surah.ayahCount.coerceAtLeast(1)
                var completed = 0
                _state.update {
                    it.copy(
                        audioDownloadProgress = AudioDownloadProgress(surahId, reciterId, 0, total),
                        errorMessage = null
                    )
                }

                withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "audio/$reciterId").apply { mkdirs() }
                    val buffer = ByteArray(8 * 1024)
                    (1..total).chunked(2).forEach { batch ->
                        coroutineScope {
                            batch.map { ayahNum ->
                                async {
                                    val dest = localAyahCacheFile(reciterId, surahId, ayahNum)
                                    if (!dest.exists() || dest.length() <= 1024L) {
                                        val url = buildReciterAudioUrl(reciter.baseUrl, reciterId, surahId, ayahNum)
                                        val req = Request.Builder().url(url).build()
                                        okHttpClient.newCall(req).execute().use { resp ->
                                            val bodyStream = resp.body?.byteStream()
                                            if (resp.isSuccessful && bodyStream != null) {
                                                val tmp = File(dir, "${dest.name}.tmp")
                                                bodyStream.use { input ->
                                                    FileOutputStream(tmp).use { output ->
                                                        while (true) {
                                                            val read = input.read(buffer)
                                                            if (read == -1) break
                                                            output.write(buffer, 0, read)
                                                        }
                                                    }
                                                }
                                                if (tmp.length() > 512L) {
                                                    tmp.renameTo(dest)
                                                } else {
                                                    tmp.delete()
                                                }
                                            }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                        completed = (completed + batch.size).coerceAtMost(total)
                        _state.update {
                            it.copy(audioDownloadProgress = AudioDownloadProgress(surahId, reciterId, completed, total))
                        }
                    }
                }
                refreshCacheSize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("QuranAudio", "Surah audio download error", e)
                _state.update {
                    it.copy(errorMessage = "Audio download error: ${e.localizedMessage ?: "Network failed"}")
                }
            } finally {
                _state.update { it.copy(audioDownloadProgress = null) }
            }
        }
    }

    fun clearOfflineAudioCache() {
        audioDownloadJob?.cancel()
        scope.launch(Dispatchers.IO) {
            File(context.filesDir, "audio").deleteRecursively()
            refreshCacheSize()
        }
    }

    fun deleteSurahAudio(surahId: Int, reciterId: Int? = null) {
        scope.launch(Dispatchers.IO) {
            val prefix = surahId.toString().padStart(3, '0')
            val audioRoot = File(context.filesDir, "audio")
            if (reciterId != null) {
                val qariDir = File(audioRoot, reciterId.toString())
                qariDir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
            } else {
                audioRoot.listFiles()?.forEach { qariDir ->
                    if (qariDir.isDirectory) {
                        qariDir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }
                    }
                }
            }
            refreshCacheSize()
        }
    }

    fun deleteReciterAudio(reciterId: Int) {
        scope.launch(Dispatchers.IO) {
            val qariDir = File(context.filesDir, "audio/$reciterId")
            qariDir.deleteRecursively()
            refreshCacheSize()
        }
    }

    fun hasSurahAudioDownloaded(surahId: Int, reciterId: Int? = null): Boolean {
        val prefix = surahId.toString().padStart(3, '0')
        val audioRoot = File(context.filesDir, "audio")
        if (!audioRoot.exists()) return false
        return if (reciterId != null) {
            val qariDir = File(audioRoot, reciterId.toString())
            qariDir.listFiles { f -> f.name.startsWith(prefix) && f.length() > 1024L }?.isNotEmpty() == true
        } else {
            audioRoot.listFiles()?.any { qariDir ->
                qariDir.isDirectory && qariDir.listFiles { f -> f.name.startsWith(prefix) && f.length() > 1024L }?.isNotEmpty() == true
            } == true
        }
    }

    fun getDownloadedAudioSurahIds(reciterId: Int? = null): Set<Int> {
        val audioRoot = File(context.filesDir, "audio")
        if (!audioRoot.exists()) return emptySet()
        val result = mutableSetOf<Int>()
        val dirs = if (reciterId != null) {
            listOf(File(audioRoot, reciterId.toString()))
        } else {
            audioRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        }
        dirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".mp3") && file.length() > 1024L) {
                    val sidStr = file.name.take(3)
                    sidStr.toIntOrNull()?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun refreshCacheSize() {
        scope.launch(Dispatchers.IO) {
            val root = File(context.filesDir, "audio")
            val totalBytes = if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
            _state.update { it.copy(offlineCacheSizeBytes = totalBytes) }
        }
    }

    private fun handleMediaEnded() {
        wordPollingJob?.cancel()

        if (isPlayingSingleWord) {
            isPlayingSingleWord = false
            _state.update { it.copy(isPlaying = false, activeWordPosition = null) }
            return
        }

        val s = _state.value

        // If Word-by-Word Sequential Recitation Mode is active, step through the remaining words of the Ayah first
        if (s.hifzConfig.wordByWordMode && currentAyahWords.isNotEmpty() && wbwWordIndex + 1 < currentAyahWords.size) {
            wbwWordIndex += 1
            val nextWord = currentAyahWords[wbwWordIndex]
            val wordUri = buildWordAudioUri(nextWord, s.surahId, s.ayahNumber)
            _state.update {
                it.copy(
                    isPlaying = true,
                    activeWordPosition = nextWord.position,
                    positionMs = 0L,
                    durationMs = 0L
                )
            }
            playUriInController(
                uri = wordUri,
                title = "${s.surahName} (${s.surahId}:${s.ayahNumber} • Word ${nextWord.position}/${currentAyahWords.size})",
                artist = "Word-by-Word Recitation",
                speed = s.hifzConfig.playbackSpeed
            )
            return
        }

        _state.update { it.copy(isPlaying = false, activeWordPosition = null) }
        if (currentSegments.isEmpty() && lastLoadedAyahId == -1) return

        when (
            val step = HifzLoopEngine.nextStep(
                currentAyah = s.ayahNumber,
                currentIteration = s.currentIteration,
                config = s.hifzConfig,
                maxAyahInSurah = s.maxAyahInSurah
            )
        ) {
            is LoopAdvanceResult.PlayAyah -> {
                pendingPauseJob?.cancel()
                pendingPauseJob = scope.launch {
                    val waitSec = (step.delayMsBeforePlay / 1000L).toInt()
                    if (waitSec > 0) {
                        for (rem in waitSec downTo 1) {
                            _state.update { it.copy(pauseRemainingSec = rem) }
                            delay(1000L)
                        }
                        _state.update { it.copy(pauseRemainingSec = 0) }
                    }
                    playAyah(
                        surahId = s.surahId,
                        ayahNumber = step.ayahNumber,
                        surahName = s.surahName,
                        maxAyahInSurah = s.maxAyahInSurah,
                        reciterId = s.reciterId,
                        reciterName = s.reciterName,
                        iteration = step.iterationOfAyah
                    )
                }
            }
            LoopAdvanceResult.Completed -> {
                if (s.hifzConfig.autoPlayNextSurah && s.surahId < 114) {
                    playNextSurahFromStart(s.surahId + 1, s.reciterId, s.reciterName)
                } else {
                    persistLastRecited(s.surahId, s.ayahNumber, s.reciterId, 0L)
                    _state.update { it.copy(isPlaying = false, activeWordPosition = null, currentIteration = 1) }
                }
            }
        }
    }

    private fun playNextSurahFromStart(nextSurahId: Int, reciterId: Int, reciterName: String) {
        scope.launch {
            val prefs = preferencesRepository?.preferencesFlow?.first()
            val surah = quranDao.getSurah(nextSurahId) ?: return@launch
            val lang = prefs?.uiLanguageCode ?: "en"
            val name = BidiUtils.extractLocalizedSurahName(surah.localizedNamesJson, lang)
                .ifBlank { surah.nameTransliteration }
            playAyah(
                surahId = nextSurahId,
                ayahNumber = 1,
                surahName = name,
                maxAyahInSurah = surah.ayahCount,
                reciterId = reciterId,
                reciterName = reciterName,
                iteration = 1
            )
        }
    }

    private fun startWordPositionPolling() {
        wordPollingJob?.cancel()
        wordPollingJob = scope.launch {
            var lastEmittedPosMs = -1L
            var lastEmittedWordPos: Int? = -1
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition
                    val duration = controller.duration.coerceAtLeast(0L)
                    val s = _state.value
                    val activeWord = if (s.hifzConfig.wordByWordMode && currentAyahWords.isNotEmpty()) {
                        currentAyahWords.getOrNull(wbwWordIndex)?.position
                    } else if (currentSegments.isNotEmpty()) {
                        HifzLoopEngine.findActiveWordPosition(currentSegments, pos)
                    } else {
                        HifzLoopEngine.estimateWordPositionByProgress(currentAyahWords.size, pos, duration)
                    }
                    if (activeWord != lastEmittedWordPos || abs(pos - lastEmittedPosMs) >= 140L || duration != s.durationMs) {
                        lastEmittedPosMs = pos
                        lastEmittedWordPos = activeWord
                        _state.update {
                            it.copy(
                                positionMs = pos,
                                durationMs = duration,
                                activeWordPosition = activeWord
                            )
                        }
                    }
                }
                delay(110L)
            }
        }
    }

    private fun localAyahCacheFile(reciterId: Int, surahId: Int, ayahNumber: Int): File {
        val dir = File(context.filesDir, "audio/$reciterId")
        val s = surahId.toString().padStart(3, '0')
        val a = ayahNumber.toString().padStart(3, '0')
        return File(dir, "${s}${a}.mp3")
    }

    private fun buildWordAudioUri(word: WordEntity, surahId: Int, ayahNumber: Int): String {
        return word.audioUrl?.takeIf { it.isNotBlank() } ?: run {
            val s = surahId.toString().padStart(3, '0')
            val a = ayahNumber.toString().padStart(3, '0')
            val p = word.position.toString().padStart(3, '0')
            "https://audio.qurancdn.com/wbw/${s}_${a}_${p}.mp3"
        }
    }

    private fun buildReciterAudioUrl(baseUrl: String?, reciterId: Int, surahId: Int, ayahNumber: Int): String {
        val resolvedBase = baseUrl?.takeIf { it.isNotBlank() } ?: when (reciterId) {
            1 -> "https://everyayah.com/data/Husary_128kbps/"
            9 -> "https://everyayah.com/data/Minshawy_Murattal_128kbps/"
            else -> "https://everyayah.com/data/Alafasy_128kbps/"
        }
        val normalizedBase = if (resolvedBase.endsWith("/")) resolvedBase else "$resolvedBase/"
        val s = surahId.toString().padStart(3, '0')
        val a = ayahNumber.toString().padStart(3, '0')
        return "${normalizedBase}${s}${a}.mp3"
    }

    fun release() {
        pendingPauseJob?.cancel()
        wordPollingJob?.cancel()
        sleepTimerJob?.cancel()
        audioDownloadJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
