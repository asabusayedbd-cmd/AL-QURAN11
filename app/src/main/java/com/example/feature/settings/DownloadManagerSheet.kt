package com.example.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.SurahEntity
import com.example.core.localization.BidiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerSheet(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var surahSearchQuery by remember { mutableStateOf("") }
    var surahFilterMode by remember { mutableIntStateOf(0) } // 0: All, 1: Downloaded, 2: Not Downloaded
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val downloadedSurahsCount = remember(state.surahs) { state.surahs.count { it.isDownloaded } }
    val downloadedTrsCount = remember(state.translationSources) { state.translationSources.count { it.isDownloaded } }
    val downloadedTafsirsCount = remember(state.tafsirSources) { state.tafsirSources.count { it.isDownloaded } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("download_manager_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Offline Content & Downloads",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Download or delete any Surah, Audio, Translation, or Tafsir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Storage Overview Card
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Downloaded Surahs: $downloadedSurahsCount / 114",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val audioMb = String.format("%.1f MB", state.offlineCacheSizeBytes / (1024.0 * 1024.0))
                            Text(
                                text = "Audio Cache: $audioMb • $downloadedTrsCount Translations • $downloadedTafsirsCount Tafsirs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { (downloadedSurahsCount / 114f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )

                    // Batch download progress indicator
                    state.batchDownloadProgress?.let { batch ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading ${batch.current}/${batch.total}: ${batch.surahName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = { viewModel.cancelBatchDownload() },
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                            }
                            LinearProgressIndicator(
                                progress = { (batch.current.toFloat() / batch.total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                            )
                        }
                    }

                    // Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.downloadAllSurahs() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_download_all_surahs"),
                            enabled = state.batchDownloadProgress == null && downloadedSurahsCount < 114
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download All (114)", fontSize = 12.sp, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_delete_all_downloads"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            enabled = downloadedSurahsCount > 0 || state.offlineCacheSizeBytes > 0L
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete All", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }

            // Tab Navigation
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Surahs (${state.surahs.size})", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Audio", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Translations", maxLines = 1) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Tafsirs", maxLines = 1) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Surahs Tab
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = surahSearchQuery,
                            onValueChange = { surahSearchQuery = it },
                            placeholder = { Text("Search Surah name or number…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            singleLine = true
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = surahFilterMode == 0,
                                onClick = { surahFilterMode = 0 },
                                label = { Text("All (114)") }
                            )
                            FilterChip(
                                selected = surahFilterMode == 1,
                                onClick = { surahFilterMode = 1 },
                                label = { Text("Downloaded ($downloadedSurahsCount)") }
                            )
                            FilterChip(
                                selected = surahFilterMode == 2,
                                onClick = { surahFilterMode = 2 },
                                label = { Text("Not Cached (${114 - downloadedSurahsCount})") }
                            )
                        }

                        val filteredSurahs = remember(state.surahs, surahSearchQuery, surahFilterMode) {
                            state.surahs.filter { surah ->
                                val matchesSearch = surahSearchQuery.isBlank() ||
                                    surah.id.toString() == surahSearchQuery.trim() ||
                                    surah.nameTransliteration.contains(surahSearchQuery, ignoreCase = true) ||
                                    surah.nameArabic.contains(surahSearchQuery)
                                val matchesFilter = when (surahFilterMode) {
                                    1 -> surah.isDownloaded
                                    2 -> !surah.isDownloaded
                                    else -> true
                                }
                                matchesSearch && matchesFilter
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredSurahs, key = { "sheet_surah_${it.id}" }) { surah ->
                                SurahDownloadRow(
                                    surah = surah,
                                    uiLang = state.preferences.uiLanguageCode,
                                    isBusy = state.busySurahId == surah.id,
                                    hasAudio = surah.id in state.downloadedAudioSurahIds,
                                    onDownloadSurah = { viewModel.downloadSurahOffline(surah.id) },
                                    onDeleteSurah = { viewModel.deleteSurahOffline(surah.id) },
                                    onDownloadAudio = { viewModel.downloadSurahAudio(surah.id, state.preferences.activeReciterId) },
                                    onDeleteAudio = { viewModel.deleteSurahAudio(surah.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Audio Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Qari: ${state.reciters.firstOrNull { it.id == state.preferences.activeReciterId }?.name ?: "Mishary Alafasy"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedButton(
                                onClick = { viewModel.clearOfflineAudioCache() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Audio")
                            }
                        }

                        Text(
                            text = "Download full recitation per Surah for offline listening:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(state.surahs) { surah ->
                                val hasAudio = surah.id in state.downloadedAudioSurahIds
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${surah.id}. ${surah.nameTransliteration} (${surah.ayahCount} Ayahs)")
                                            Text(
                                                text = if (hasAudio) "Audio Downloaded" else "Streaming Online",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (hasAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (hasAudio) {
                                                IconButton(
                                                    onClick = { viewModel.deleteSurahAudio(surah.id) },
                                                    modifier = Modifier.testTag("delete_audio_${surah.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete Audio",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { viewModel.downloadSurahAudio(surah.id, state.preferences.activeReciterId) },
                                                    modifier = Modifier.testTag("download_audio_${surah.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.VolumeUp,
                                                        contentDescription = "Download Audio",
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
                }
                2 -> {
                    // Translations Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.translationSources, key = { "tr_src_${it.id}" }) { src ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(src.translatorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "${src.languageName} • ${if (src.isDownloaded) "Offline Ready" else "On-Demand"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (state.busySourceId == src.id) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else if (src.isDownloaded) {
                                            IconButton(
                                                onClick = { viewModel.deleteTranslationPack(src.id) },
                                                modifier = Modifier.testTag("delete_tr_${src.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Translation",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { viewModel.downloadTranslationPack(src.id) },
                                                modifier = Modifier.testTag("dl_tr_${src.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.CloudDownload,
                                                    contentDescription = "Download Translation",
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
                3 -> {
                    // Tafsirs Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.tafsirSources, key = { "taf_src_${it.id}" }) { src ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(src.tafsirName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "${src.languageName} • ${if (src.isDownloaded) "Offline Ready" else "On-Demand"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (state.busySourceId == src.id) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else if (src.isDownloaded) {
                                            IconButton(
                                                onClick = { viewModel.deleteTafsirPack(src.id) },
                                                modifier = Modifier.testTag("delete_taf_${src.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Tafsir",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { viewModel.downloadTafsirPack(src) },
                                                modifier = Modifier.testTag("dl_taf_${src.id}")
                                            ) {
                                                Icon(
                                                    Icons.Default.CloudDownload,
                                                    contentDescription = "Download Tafsir",
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
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Offline Downloads?") },
            text = { Text("This will remove all downloaded Surah texts and audio files from your device. You can re-download them at any time.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloadedSurahs()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SurahDownloadRow(
    surah: SurahEntity,
    uiLang: String,
    isBusy: Boolean,
    hasAudio: Boolean,
    onDownloadSurah: () -> Unit,
    onDeleteSurah: () -> Unit,
    onDownloadAudio: () -> Unit,
    onDeleteAudio: () -> Unit
) {
    val name = BidiUtils.extractLocalizedSurahName(surah.localizedNamesJson, uiLang)
        .ifBlank { surah.nameTransliteration }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${surah.id}. $name",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = surah.nameArabic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${surah.ayahCount} Ayahs • ${surah.revelationPlace} • ${if (surah.isDownloaded) "Offline" else "Online"}${if (hasAudio) " • Audio Cached" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (surah.isDownloaded) {
                    IconButton(
                        onClick = onDeleteSurah,
                        modifier = Modifier.testTag("delete_surah_${surah.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Surah",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onDownloadSurah,
                        modifier = Modifier.testTag("download_surah_${surah.id}")
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = "Download Surah",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
