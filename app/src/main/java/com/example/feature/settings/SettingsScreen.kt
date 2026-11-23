package com.example.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.localization.BidiUtils
import com.example.core.localization.NoorThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.components.QariPickerModalSheet

import java.util.Locale

private val UI_LANGUAGES = listOf(
    "en" to "English",
    "bn" to "বাংলা (Bangla)",
    "ar" to "العربية (Arabic)",
    "ur" to "اردو (Urdu)",
    "id" to "Bahasa Indonesia",
    "tr" to "Türkçe"
)

private val WBW_LANGUAGES = listOf(
    "bn" to "বাংলা (WBW)",
    "en" to "English (WBW)",
    "ur" to "اردو (WBW)",
    "id" to "Indonesian (WBW)",
    "tr" to "Türkçe (WBW)",
    "fr" to "Français (WBW)",
    "de" to "Deutsch (WBW)",
    "ru" to "Русский (WBW)",
    "hi" to "हिन्दी (WBW)",
    "ta" to "தமிழ் (WBW)"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showQariPicker by remember { mutableStateOf(false) }
    var translationLangFilter by remember { mutableStateOf("ALL") }
    var tafsirLangFilter by remember { mutableStateOf("ALL") }

    val activeReciter = remember(state.reciters, state.preferences.activeReciterId) {
        state.reciters.firstOrNull { it.id == state.preferences.activeReciterId }
    }
    val downloadedSurahsCount = remember(state.surahs) { state.surahs.count { it.isDownloaded } }
    val featuredSurahs = remember(state.surahs) {
        state.surahs.filter { it.id in setOf(1, 2, 18, 36, 55, 67, 103, 108, 112, 113, 114) }
    }
    val tafsirLangs = remember(state.tafsirSources) {
        listOf("ALL") + state.tafsirSources.map { it.languageCode.uppercase() }.distinct()
    }
    val translationLangs = remember(state.translationSources) {
        listOf("ALL") + state.translationSources.map { it.languageCode.uppercase() }.distinct()
    }
    val filteredTranslations = remember(state.translationSources, translationLangFilter) {
        if (translationLangFilter == "ALL") state.translationSources
        else state.translationSources.filter { it.languageCode.equals(translationLangFilter, ignoreCase = true) }
    }
    val filteredTafsirs = remember(state.tafsirSources, tafsirLangFilter) {
        if (tafsirLangFilter == "ALL") state.tafsirSources
        else state.tafsirSources.filter { it.languageCode.equals(tafsirLangFilter, ignoreCase = true) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.statusMessage?.let { msg ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.clearStatusMessage() }
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 1. Offline-First & Local Privacy Summary
            item(key = "privacy_summary") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline-First • 100% On-Device",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "$downloadedSurahsCount / 114 Surahs cached • No login or account needed. Bookmarks, notes & streaks stay privately on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Dynamic In-App UI Language Switcher
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_language_title), style = MaterialTheme.typography.titleMedium)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            UI_LANGUAGES.forEach { (code, label) ->
                                FilterChip(
                                    selected = state.preferences.uiLanguageCode == code,
                                    onClick = { viewModel.setUiLanguage(code) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("ui_lang_$code")
                                )
                            }
                        }
                    }
                }
            }

            // 3. Reading Themes
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                NoorThemeMode.SERENE_EMERALD to stringResource(R.string.theme_serene_emerald),
                                NoorThemeMode.SOFT_SEPIA to stringResource(R.string.theme_sepia),
                                NoorThemeMode.SLATE_DARK to stringResource(R.string.theme_slate_dark),
                                NoorThemeMode.AMOLED_BLACK to stringResource(R.string.theme_amoled)
                            ).forEach { (mode, title) ->
                                FilterChip(
                                    selected = state.preferences.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(title) },
                                    modifier = Modifier.testTag("theme_chip_${mode.name}")
                                )
                            }
                        }
                    }
                }
            }

            // 4. Recitation & Audio Settings (75+ Genuine Qaris, Mode, Speed, Hifz Loop & Cache)
            item(key = "audio_settings") {
                val cacheMb = state.offlineCacheSizeBytes / (1024f * 1024f)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recitation & Audio (${state.reciters.size} Qaris)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        Text(
                            text = activeReciter?.let { "${it.name} (${it.localizedName}) • ${it.style}" }
                                ?: "Mishary Rashid Alafasy",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        FilledTonalButton(
                            onClick = { showQariPicker = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_choose_qari_button")
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse & Select Qari (${state.reciters.size} Reciters)")
                        }

                        HorizontalDivider()

                        Text("Playback Mode", style = MaterialTheme.typography.labelLarge)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !state.preferences.audioWordByWordMode,
                                onClick = { viewModel.setAudioWordByWordMode(false) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                                modifier = Modifier.testTag("settings_audio_mode_ayah")
                            ) {
                                Text("Full Ayah")
                            }
                            SegmentedButton(
                                selected = state.preferences.audioWordByWordMode,
                                onClick = { viewModel.setAudioWordByWordMode(true) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                                modifier = Modifier.testTag("settings_audio_mode_wbw")
                            ) {
                                Text("Word-by-Word")
                            }
                        }

                        Text("Recitation Speed", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                FilterChip(
                                    selected = state.preferences.audioPlaybackSpeed == spd,
                                    onClick = { viewModel.setAudioPlaybackSpeed(spd) },
                                    label = { Text("${spd}x") },
                                    modifier = Modifier.testTag("settings_speed_$spd")
                                )
                            }
                        }

                        Text("Default Repeat & Pause Gap", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1 to "1x", 2 to "2x", 3 to "3x", 5 to "5x", 10 to "10x", 0 to "∞").forEach { (rep, lbl) ->
                                FilterChip(
                                    selected = state.preferences.audioRepeatPerAyah == rep,
                                    onClick = { viewModel.setAudioRepeatPerAyah(rep) },
                                    label = { Text(lbl) }
                                )
                            }
                            listOf(0, 2, 3, 5, 10).forEach { gap ->
                                FilterChip(
                                    selected = state.preferences.audioPauseSeconds == gap,
                                    onClick = { viewModel.setAudioPauseSeconds(gap) },
                                    label = { Text("Gap ${gap}s") }
                                )
                            }
                        }

                        val sleepRem = state.sleepRemainingSec
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sleepRem?.let { sec ->
                                    "Sleep Timer (%d:%02d left)".format(Locale.US, sec / 60, sec % 60)
                                } ?: "Sleep Timer",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0 to "Off", 10 to "10m", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m").forEach { (mins, lbl) ->
                                val active = (mins == 0 && sleepRem == null) ||
                                    (mins > 0 && sleepRem != null && ((sleepRem + 59) / 60) in (mins - 1)..mins)
                                FilterChip(
                                    selected = active,
                                    onClick = { viewModel.setSleepTimerMinutes(mins) },
                                    label = { Text(lbl) }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Advance Next Surah", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Continue playback automatically to the next Surah",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.preferences.autoPlayNextSurah,
                                onCheckedChange = viewModel::setAutoPlayNextSurah,
                                modifier = Modifier.testTag("settings_auto_next_surah_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Pronounce Word on Tap", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Play single-word audio when opening the word morphology sheet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.preferences.autoPlayWordOnInspect,
                                onCheckedChange = viewModel::setAutoPlayWordOnInspect,
                                modifier = Modifier.testTag("settings_auto_word_audio_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Offline Audio Cache: ${"%.1f".format(Locale.US, cacheMb)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.offlineCacheSizeBytes > 0L) {
                                TextButton(onClick = viewModel::clearOfflineAudioCache) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Audio")
                                }
                            }
                        }
                    }
                }
            }

            // 5. Verified Tafsir Library (Lazy items for fast 1 GB RAM scrolling)
            item(key = "tafsir_header") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tafsir Library (${state.tafsirSources.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::syncRemoteTafsirCatalog,
                                enabled = !state.isRefreshingTafsirs,
                                modifier = Modifier.testTag("sync_tafsir_catalog_button")
                            ) {
                                if (state.isRefreshingTafsirs) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Catalog")
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tafsirLangs, key = { "tl_$it" }) { code ->
                                FilterChip(
                                    selected = tafsirLangFilter == code,
                                    onClick = { tafsirLangFilter = code },
                                    label = { Text(if (code == "ALL") "All" else code) }
                                )
                            }
                        }
                    }
                }
            }

            items(
                items = filteredTafsirs,
                key = { "taf_${it.id}" },
                contentType = { "tafsir_row" }
            ) { src ->
                val isSelected = src.id == state.preferences.activeTafsirSourceId
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectDefaultTafsirSource(src) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = src.tafsirName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "${src.languageName} • ${if (src.isDownloaded) "Offline Ready" else "On-Demand API"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Default Tafsir", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // 6. Word-by-Word & Multi-Translation Languages (Lazy items across 24+ languages)
            item(key = "translations_header") {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.content_languages_title)} (${state.translationSources.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WBW_LANGUAGES.forEach { (code, label) ->
                                FilterChip(
                                    selected = state.preferences.wbwLanguageCode == code,
                                    onClick = { viewModel.setWbwLanguage(code) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("wbw_lang_$code")
                                )
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(translationLangs, key = { "trl_$it" }) { code ->
                                FilterChip(
                                    selected = translationLangFilter == code,
                                    onClick = { translationLangFilter = code },
                                    label = { Text(if (code == "ALL") "All" else code) }
                                )
                            }
                        }
                    }
                }
            }

            items(
                items = filteredTranslations,
                key = { "tr_${it.id}" },
                contentType = { "translation_row" }
            ) { src ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (src.isEnabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(src.translatorName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${src.languageName} • ${src.fileSizeKb} KB • ${if (src.isDownloaded) "Offline" else "Cloud"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.busySourceId == src.id) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Switch(
                                checked = src.isEnabled,
                                onCheckedChange = { enabled -> viewModel.toggleTranslationSource(src, enabled) },
                                modifier = Modifier.testTag("translation_switch_${src.id}")
                            )
                        }
                    }
                }
            }

            // 7. Offline Surah Download Manager (Highlights popular Surahs)
            item(key = "dl_manager_header") {
                Text(
                    text = stringResource(R.string.download_manager_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(featuredSurahs, key = { "dl_surah_${it.id}" }, contentType = { "dl_surah_row" }) { surah ->
                val name = BidiUtils.extractLocalizedSurahName(
                    surah.localizedNamesJson,
                    state.preferences.uiLanguageCode
                ).ifBlank { surah.nameTransliteration }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${surah.id}. $name", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (surah.isDownloaded) stringResource(R.string.downloaded_badge) else "Tap to cache offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    when {
                        state.busySurahId == surah.id -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        surah.isDownloaded -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else -> IconButton(
                            onClick = { viewModel.downloadSurahOffline(surah.id) },
                            modifier = Modifier.testTag("settings_dl_surah_${surah.id}")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.download_surah))
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }

            // 8. Tajweed Guide & Offline JSON Backup/Restore
            item(key = "tajweed_backup_section") {
                var showTajweedGuide by remember { mutableStateOf(false) }
                val context = LocalContext.current
                val database = remember { com.example.core.database.NoorDatabase.getInstance(context) }
                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch(Dispatchers.IO) {
                            com.example.feature.library.BackupRestoreManager.exportUserDataToJson(context, database, it)
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        coroutineScope.launch(Dispatchers.IO) {
                            com.example.feature.library.BackupRestoreManager.importUserDataFromJson(context, database, it)
                        }
                    }
                }

                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Tajweed, Backup & Privacy Tools", style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(
                            onClick = { showTajweedGuide = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Tajweed Rules & Makhraj Guide")
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("alquran_backup_${System.currentTimeMillis()}.json") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export JSON Backup")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restore JSON")
                            }
                        }
                    }
                }

                if (showTajweedGuide) {
                    TajweedGuideSheet(onDismiss = { showTajweedGuide = false })
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    if (showQariPicker && state.reciters.isNotEmpty()) {
        QariPickerModalSheet(
            reciters = state.reciters,
            activeReciterId = state.preferences.activeReciterId,
            onSelectReciter = {
                viewModel.setActiveReciter(it)
                showQariPicker = false
            },
            onDismiss = { showQariPicker = false }
        )
    }
}
