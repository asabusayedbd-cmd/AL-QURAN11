package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.audio.AudioPlaybackState
import com.example.core.audio.HifzLoopConfig
import com.example.core.audio.HifzLoopEngine
import com.example.core.database.AudioReciterEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioHifzBar(
    playbackState: AudioPlaybackState,
    reciters: List<AudioReciterEntity>,
    autoPlayWordOnInspect: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onPreviousAyah: () -> Unit,
    onNextAyah: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onDismiss: () -> Unit,
    onSelectReciter: (AudioReciterEntity) -> Unit,
    onUpdateHifzConfig: ((HifzLoopConfig) -> HifzLoopConfig) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit = {},
    onDownloadSurahAudio: (surahId: Int, reciterId: Int) -> Unit = { _, _ -> },
    onClearAudioCache: () -> Unit = {},
    onToggleAutoPlayWordOnInspect: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!playbackState.isVisible) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    var showQariPicker by remember { mutableStateOf(false) }
    var showRecitationSettings by remember { mutableStateOf(false) }

    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val rawProgress = (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Interactive Seek Slider + Duration Timestamps
            if (playbackState.durationMs > 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = HifzLoopEngine.formatDurationMmSs(
                            if (isScrubbing) (scrubFraction * playbackState.durationMs).toLong()
                            else playbackState.positionMs
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = if (isScrubbing) scrubFraction else rawProgress,
                        onValueChange = {
                            isScrubbing = true
                            scrubFraction = it
                        },
                        onValueChangeFinished = {
                            onSeekTo((scrubFraction * playbackState.durationMs).toLong())
                            isScrubbing = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .testTag("audio_seek_slider")
                    )
                    Text(
                        text = HifzLoopEngine.formatDurationMmSs(playbackState.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Active Mode / Repeat / Pause Countdown / Sleep Timer Status Chips
            val statusBadges = buildList {
                if (playbackState.hifzConfig.wordByWordMode) {
                    val wordIdx = playbackState.activeWordPosition ?: 1
                    val wordTotal = playbackState.totalWordsInAyah.coerceAtLeast(wordIdx)
                    add("Word $wordIdx/$wordTotal")
                }
                if (playbackState.hifzConfig.repeatPerAyah != 1) {
                    val targetLabel = if (playbackState.hifzConfig.repeatPerAyah == 0) "∞" else "${playbackState.hifzConfig.repeatPerAyah}"
                    add("Repeat ${playbackState.currentIteration}/$targetLabel")
                }
                if (playbackState.pauseRemainingSec > 0) {
                    add("Paused • ${playbackState.pauseRemainingSec}s")
                }
                playbackState.sleepRemainingSec?.let { sec ->
                    val mins = sec / 60
                    val rem = sec % 60
                    add("Sleep %d:%02d".format(Locale.US, mins, rem))
                }
                if (playbackState.isPlayingFromOfflineCache) {
                    add("Offline")
                } else if (playbackState.isPlaying) {
                    add("Background Ready")
                }
            }

            if (statusBadges.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(statusBadges) { badge ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Primary Transport & Qari Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousAyah,
                    modifier = Modifier.testTag("audio_prev_button")
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Ayah")
                }

                FilledTonalIconButton(
                    onClick = onTogglePlayPause,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("audio_play_pause_button")
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (playbackState.isPlaying) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                }

                IconButton(
                    onClick = onNextAyah,
                    modifier = Modifier.testTag("audio_next_button")
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Ayah")
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showQariPicker = true }
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "${playbackState.surahName} • ${playbackState.surahId}:${playbackState.ayahNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playbackState.errorMessage ?: "${playbackState.reciterName} (${reciters.size} Qaris)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playbackState.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { showRecitationSettings = true },
                    modifier = Modifier.testTag("audio_settings_sheet_button")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Recitation & audio settings")
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("audio_expand_hifz_button")
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = stringResource(R.string.hifz_controls)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("audio_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close audio player")
                }
            }

            // Expandable Al-Quran Word-by-Word Style Hifz & Recitation Drawer
            AnimatedVisibility(visible = expanded) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider()

                        // Recitation Mode: Ayah-by-Ayah vs Word-by-Word Sequential
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !playbackState.hifzConfig.wordByWordMode,
                                onClick = { onUpdateHifzConfig { it.copy(wordByWordMode = false) } },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                modifier = Modifier.testTag("audio_mode_ayah_button")
                            ) {
                                Text("Ayah Recitation")
                            }
                            SegmentedButton(
                                selected = playbackState.hifzConfig.wordByWordMode,
                                onClick = { onUpdateHifzConfig { it.copy(wordByWordMode = true) } },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                modifier = Modifier.testTag("audio_mode_wbw_button")
                            ) {
                                Text("Word-by-Word Audio")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { showQariPicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_all_qaris_button")
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Qaris (${reciters.size})", maxLines = 1)
                            }

                            val dlProgress = playbackState.audioDownloadProgress
                            OutlinedButton(
                                onClick = { onDownloadSurahAudio(playbackState.surahId, playbackState.reciterId) },
                                enabled = dlProgress == null,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("download_surah_audio_button")
                            ) {
                                if (dlProgress != null) {
                                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("${dlProgress.downloadedCount}/${dlProgress.totalCount}")
                                } else {
                                    Icon(
                                        if (playbackState.isPlayingFromOfflineCache) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (playbackState.isPlayingFromOfflineCache) "Surah Cached" else "Cache Audio",
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Horizontal Quick Qari Strip
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val featured = reciters.sortedByDescending { it.id == playbackState.reciterId }.take(12)
                            items(featured, key = { it.id }) { reciter ->
                                FilterChip(
                                    selected = reciter.id == playbackState.reciterId,
                                    onClick = { onSelectReciter(reciter) },
                                    label = {
                                        Text(
                                            text = reciter.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("reciter_chip_${reciter.id}")
                                )
                            }
                        }

                        // Repeat per Ayah
                        Text(
                            text = stringResource(R.string.repeat_ayah),
                            style = MaterialTheme.typography.labelLarge
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val repeatOptions = listOf(1 to "1x", 2 to "2x", 3 to "3x", 5 to "5x", 10 to "10x", 0 to "∞")
                            items(repeatOptions) { (count, label) ->
                                FilterChip(
                                    selected = playbackState.hifzConfig.repeatPerAyah == count,
                                    onClick = { onUpdateHifzConfig { it.copy(repeatPerAyah = count) } },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("repeat_chip_$count")
                                )
                            }
                        }

                        // Pause Gap + Playback Speed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.pause_between_ayahs), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.playback_speed), style = MaterialTheme.typography.labelMedium)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val pauseOptions = listOf(0 to "0s", 2 to "2s", 3 to "3s", 5 to "5s", 10 to "10s")
                            items(pauseOptions) { (sec, label) ->
                                FilterChip(
                                    selected = playbackState.hifzConfig.pauseSecondsAfterAyah == sec,
                                    onClick = { onUpdateHifzConfig { it.copy(pauseSecondsAfterAyah = sec) } },
                                    label = { Text("Gap $label") }
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val speedOptions = listOf(0.5f to "0.5x", 0.75f to "0.75x", 1.0f to "1.0x", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2.0x")
                            items(speedOptions) { (spd, label) ->
                                FilterChip(
                                    selected = playbackState.hifzConfig.playbackSpeed == spd,
                                    onClick = { onUpdateHifzConfig { it.copy(playbackSpeed = spd) } },
                                    label = { Text(label) }
                                )
                            }
                        }

                        // A-B Range Loop controls with instant Set-Here buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${stringResource(R.string.loop_range)}: ${playbackState.hifzConfig.startAyah}–${playbackState.hifzConfig.endAyah}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Switch(
                                checked = playbackState.hifzConfig.loopRangeEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateHifzConfig {
                                        it.copy(
                                            loopRangeEnabled = enabled,
                                            startAyah = playbackState.ayahNumber.coerceAtMost(playbackState.maxAyahInSurah),
                                            endAyah = (playbackState.ayahNumber + 2).coerceAtMost(playbackState.maxAyahInSurah)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("hifz_loop_switch")
                            )
                        }

                        if (playbackState.hifzConfig.loopRangeEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onUpdateHifzConfig { cfg ->
                                            cfg.copy(startAyah = (cfg.startAyah - 1).coerceAtLeast(1))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("A -") }
                                FilledTonalButton(
                                    onClick = {
                                        onUpdateHifzConfig { cfg ->
                                            cfg.copy(startAyah = playbackState.ayahNumber.coerceIn(1, playbackState.maxAyahInSurah))
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f)
                                ) { Text("Set A") }
                                FilledTonalButton(
                                    onClick = {
                                        onUpdateHifzConfig { cfg ->
                                            cfg.copy(endAyah = playbackState.ayahNumber.coerceIn(1, playbackState.maxAyahInSurah))
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f)
                                ) { Text("Set B") }
                                OutlinedButton(
                                    onClick = {
                                        onUpdateHifzConfig { cfg ->
                                            cfg.copy(endAyah = (cfg.endAyah + 1).coerceAtMost(playbackState.maxAyahInSurah))
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("B +") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQariPicker) {
        QariPickerModalSheet(
            reciters = reciters,
            activeReciterId = playbackState.reciterId,
            onSelectReciter = {
                onSelectReciter(it)
                showQariPicker = false
            },
            onDismiss = { showQariPicker = false }
        )
    }

    if (showRecitationSettings) {
        RecitationSettingsModalSheet(
            playbackState = playbackState,
            reciters = reciters,
            autoPlayWordOnInspect = autoPlayWordOnInspect,
            onSelectReciter = onSelectReciter,
            onUpdateHifzConfig = onUpdateHifzConfig,
            onSetSleepTimerMinutes = onSetSleepTimerMinutes,
            onDownloadSurahAudio = onDownloadSurahAudio,
            onClearAudioCache = onClearAudioCache,
            onToggleAutoPlayWordOnInspect = onToggleAutoPlayWordOnInspect,
            onOpenQariPicker = {
                showRecitationSettings = false
                showQariPicker = true
            },
            onDismiss = { showRecitationSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecitationSettingsModalSheet(
    playbackState: AudioPlaybackState,
    reciters: List<AudioReciterEntity>,
    autoPlayWordOnInspect: Boolean,
    onSelectReciter: (AudioReciterEntity) -> Unit,
    onUpdateHifzConfig: ((HifzLoopConfig) -> HifzLoopConfig) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onDownloadSurahAudio: (surahId: Int, reciterId: Int) -> Unit,
    onClearAudioCache: () -> Unit,
    onToggleAutoPlayWordOnInspect: (Boolean) -> Unit,
    onOpenQariPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val activeQari = reciters.firstOrNull { it.id == playbackState.reciterId }
    val cacheMb = playbackState.offlineCacheSizeBytes / (1024f * 1024f)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Recitation & Audio Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            // Active Qari & Offline Surah Cache Card
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Active Qari (${reciters.size} Verified Reciters)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = activeQari?.let { "${it.name} (${it.localizedName}) • ${it.style}" }
                            ?: playbackState.reciterName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onOpenQariPicker,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Qari")
                        }
                        val dlProg = playbackState.audioDownloadProgress
                        OutlinedButton(
                            onClick = { onDownloadSurahAudio(playbackState.surahId, playbackState.reciterId) },
                            enabled = dlProg == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (dlProg != null) "${dlProg.downloadedCount}/${dlProg.totalCount}" else "Save Surah MP3")
                        }
                    }
                }
            }

            // Playback Mode
            Text("Playback Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !playbackState.hifzConfig.wordByWordMode,
                    onClick = { onUpdateHifzConfig { it.copy(wordByWordMode = false) } },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) {
                    Text("Full Ayah")
                }
                SegmentedButton(
                    selected = playbackState.hifzConfig.wordByWordMode,
                    onClick = { onUpdateHifzConfig { it.copy(wordByWordMode = true) } },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) {
                    Text("Word-by-Word")
                }
            }

            // Speed & Repeat
            Text("Recitation Speed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                    FilterChip(
                        selected = playbackState.hifzConfig.playbackSpeed == spd,
                        onClick = { onUpdateHifzConfig { it.copy(playbackSpeed = spd) } },
                        label = { Text("${spd}x") }
                    )
                }
            }

            Text("Repeat Each Ayah & Pause Gap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(1 to "1x", 2 to "2x", 3 to "3x", 5 to "5x", 10 to "10x", 0 to "∞").forEach { (rep, lbl) ->
                    FilterChip(
                        selected = playbackState.hifzConfig.repeatPerAyah == rep,
                        onClick = { onUpdateHifzConfig { it.copy(repeatPerAyah = rep) } },
                        label = { Text(lbl) }
                    )
                }
                listOf(0, 2, 3, 5, 10).forEach { gap ->
                    FilterChip(
                        selected = playbackState.hifzConfig.pauseSecondsAfterAyah == gap,
                        onClick = { onUpdateHifzConfig { it.copy(pauseSecondsAfterAyah = gap) } },
                        label = { Text("+${gap}s") }
                    )
                }
            }

            // Sleep Timer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sleep Timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0 to "Off", 10 to "10m", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, lbl) ->
                    val active = (mins == 0 && playbackState.sleepRemainingSec == null) ||
                        (mins > 0 && playbackState.sleepRemainingSec != null && ((playbackState.sleepRemainingSec + 59) / 60) in (mins - 1)..mins)
                    FilterChip(
                        selected = active,
                        onClick = { onSetSleepTimerMinutes(mins) },
                        label = { Text(lbl) }
                    )
                }
            }

            HorizontalDivider()

            // Behavior Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Advance to Next Surah", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Continue smoothly from Ayah 1 of the following Surah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = playbackState.hifzConfig.autoPlayNextSurah,
                    onCheckedChange = { enabled -> onUpdateHifzConfig { it.copy(autoPlayNextSurah = enabled) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Pronounce Word on Tap", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Speak Arabic word aloud automatically when inspecting a word",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoPlayWordOnInspect,
                    onCheckedChange = onToggleAutoPlayWordOnInspect
                )
            }

            // Offline Cache Management
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offline Audio Cache: ${"%.1f".format(Locale.US, cacheMb)} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (playbackState.offlineCacheSizeBytes > 0L) {
                    TextButton(onClick = onClearAudioCache) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Cache")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QariPickerModalSheet(
    reciters: List<AudioReciterEntity>,
    activeReciterId: Int,
    onSelectReciter: (AudioReciterEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var styleFilter by remember { mutableStateOf("All") }
    val styles = remember(reciters) {
        listOf("All") + reciters.map { it.style }.distinct().sorted()
    }
    val filtered = remember(reciters, search, styleFilter) {
        reciters.filter { reciter ->
            val matchesStyle = styleFilter == "All" || reciter.style.equals(styleFilter, ignoreCase = true)
            val q = search.trim()
            val matchesQuery = q.isEmpty() ||
                reciter.name.contains(q, ignoreCase = true) ||
                reciter.localizedName.contains(q, ignoreCase = true) ||
                reciter.style.contains(q, ignoreCase = true)
            matchesStyle && matchesQuery
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Qari / Reciter (${reciters.size} Verified Reciters)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search Qari name, Arabic, or style…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qari_search_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(styles) { st ->
                    FilterChip(
                        selected = styleFilter == st,
                        onClick = { styleFilter = st },
                        label = { Text(st) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { reciter ->
                    val isSelected = reciter.id == activeReciterId
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qari_picker_item_${reciter.id}")
                            .clickable { onSelectReciter(reciter) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = reciter.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = "${reciter.localizedName} • ${reciter.style}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected Qari",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
