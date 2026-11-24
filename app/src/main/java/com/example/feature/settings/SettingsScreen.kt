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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.database.TafsirSourceEntity
import com.example.core.database.TranslationSourceEntity
import com.example.core.localization.NoorThemeMode
import com.example.ui.components.QariPickerModalSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    "en" to "English (WBW)",
    "bn" to "বাংলা (WBW)",
    "ur" to "اردو (WBW)",
    "id" to "Indonesian (WBW)",
    "tr" to "Türkçe (WBW)",
    "fr" to "Français (WBW)",
    "de" to "Deutsch (WBW)",
    "ru" to "Русский (WBW)",
    "hi" to "हिन्दी (WBW)",
    "ta" to "தமிழ் (WBW)"
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val database = remember { com.example.core.database.NoorDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var showQariPicker by remember { mutableStateOf(false) }
    var showAudioSettingsSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showTranslationsSheet by remember { mutableStateOf(false) }
    var showTafsirsSheet by remember { mutableStateOf(false) }
    var showDownloadManagerSheet by remember { mutableStateOf(false) }
    var showTajweedGuide by remember { mutableStateOf(false) }

    val activeReciter = remember(state.reciters, state.preferences.activeReciterId) {
        state.reciters.firstOrNull { it.id == state.preferences.activeReciterId }
    }
    val activeTafsir = remember(state.tafsirSources, state.preferences.activeTafsirSourceId) {
        state.tafsirSources.firstOrNull { it.id == state.preferences.activeTafsirSourceId }
    }
    val enabledTranslationsCount = remember(state.translationSources) {
        state.translationSources.count { it.isEnabled }
    }
    val downloadedSurahsCount = remember(state.surahs) {
        state.surahs.count { it.isDownloaded }
    }
    val uiLangLabel = remember(state.preferences.uiLanguageCode) {
        UI_LANGUAGES.firstOrNull { it.first == state.preferences.uiLanguageCode }?.second ?: "English"
    }
    val wbwLangLabel = remember(state.preferences.wbwLanguageCode) {
        WBW_LANGUAGES.firstOrNull { it.first == state.preferences.wbwLanguageCode }?.second ?: "English (WBW)"
    }
    val themeLabel = when (state.preferences.themeMode) {
        NoorThemeMode.SERENE_EMERALD -> stringResource(R.string.theme_serene_emerald)
        NoorThemeMode.SOFT_SEPIA -> stringResource(R.string.theme_sepia)
        NoorThemeMode.SLATE_DARK -> stringResource(R.string.theme_slate_dark)
        NoorThemeMode.AMOLED_BLACK -> stringResource(R.string.theme_amoled)
    }
    val cacheMb = state.offlineCacheSizeBytes / (1024f * 1024f)

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

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "settings_header") {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Customize reciter, translations, reading appearance, and offline library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.statusMessage?.let { msg ->
                item(key = "status_banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
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

            // Offline Ready Overview Card
            item(key = "offline_overview") {
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline-First • 100% On-Device",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$downloadedSurahsCount / 114 Surahs ready offline • Audio cache ${"%.1f".format(Locale.US, cacheMb)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 1: Audio & Recitation (Qari in dedicated sheet)
            item(key = "group_audio") {
                SettingsGroupCard(title = "Recitation & Audio") {
                    SettingsActionRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Qari / Reciter (${state.reciters.size})",
                        subtitle = activeReciter?.let { "${it.name} • ${it.style}" } ?: "Mishary Rashid Alafasy",
                        onClick = { showQariPicker = true },
                        testTag = "settings_choose_qari_button"
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    SettingsActionRow(
                        icon = Icons.Default.Tune,
                        title = "Playback, Hifz Loop & Sleep Timer",
                        subtitle = "${if (state.preferences.audioWordByWordMode) "Word-by-Word" else "Full Ayah"} • ${state.preferences.audioPlaybackSpeed}x • Repeat ${if (state.preferences.audioRepeatPerAyah == 0) "∞" else "${state.preferences.audioRepeatPerAyah}x"}",
                        onClick = { showAudioSettingsSheet = true },
                        testTag = "settings_audio_controls_button"
                    )
                }
            }

            // Section 2: Translations & Tafsir (Dedicated searchable modal sheets)
            item(key = "group_content") {
                SettingsGroupCard(title = "Quran Translations & Tafsir") {
                    SettingsActionRow(
                        icon = Icons.Default.Translate,
                        title = "Quran Translations (${state.translationSources.size})",
                        subtitle = "$enabledTranslationsCount active • Select & download languages",
                        onClick = { showTranslationsSheet = true },
                        testTag = "settings_translations_button"
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    SettingsActionRow(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Tafsir Library (${state.tafsirSources.size})",
                        subtitle = activeTafsir?.tafsirName ?: "Select default Tafsir source",
                        onClick = { showTafsirsSheet = true },
                        testTag = "settings_tafsir_button"
                    )
                }
            }

            // Section 3: Appearance & Languages
            item(key = "group_appearance") {
                SettingsGroupCard(title = "Appearance & Languages") {
                    SettingsActionRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.theme_title),
                        subtitle = themeLabel,
                        onClick = { showThemeSheet = true },
                        testTag = "settings_theme_button"
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    SettingsActionRow(
                        icon = Icons.Default.Language,
                        title = "App & Word-by-Word Languages",
                        subtitle = "$uiLangLabel • WBW: $wbwLangLabel",
                        onClick = { showLanguageSheet = true },
                        testTag = "settings_language_button"
                    )
                }
            }

            // Section 4: Offline Downloads Manager
            item(key = "group_downloads") {
                SettingsGroupCard(title = "Storage & Downloads") {
                    SettingsActionRow(
                        icon = Icons.Default.CloudDownload,
                        title = stringResource(R.string.download_manager_title),
                        subtitle = "$downloadedSurahsCount / 114 Surahs offline • Manage audio, translations & tafsirs",
                        onClick = { showDownloadManagerSheet = true },
                        testTag = "open_download_manager_button"
                    )
                }
            }

            // Section 5: Tools & Data Backup
            item(key = "group_tools") {
                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Tajweed & Backup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalButton(
                            onClick = { showTajweedGuide = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_tajweed_guide_button")
                        ) {
                            Text("Open Tajweed Rules & Makhraj Guide")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("alquran_backup_${System.currentTimeMillis()}.json") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_export_backup_button")
                            ) {
                                Text("Export JSON")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_import_backup_button")
                            ) {
                                Text("Restore JSON")
                            }
                        }
                    }
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

    if (showAudioSettingsSheet) {
        AudioSettingsModalSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showAudioSettingsSheet = false }
        )
    }

    if (showLanguageSheet) {
        LanguagePickerModalSheet(
            uiLanguageCode = state.preferences.uiLanguageCode,
            wbwLanguageCode = state.preferences.wbwLanguageCode,
            onSelectUiLanguage = viewModel::setUiLanguage,
            onSelectWbwLanguage = viewModel::setWbwLanguage,
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showThemeSheet) {
        ThemePickerModalSheet(
            activeTheme = state.preferences.themeMode,
            onSelectTheme = { mode ->
                viewModel.setThemeMode(mode)
                showThemeSheet = false
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showTranslationsSheet) {
        TranslationsPickerModalSheet(
            sources = state.translationSources,
            busySourceId = state.busySourceId,
            onToggle = { src, enabled -> viewModel.toggleTranslationSource(src, enabled) },
            onDismiss = { showTranslationsSheet = false }
        )
    }

    if (showTafsirsSheet) {
        TafsirsPickerModalSheet(
            sources = state.tafsirSources,
            activeTafsirSourceId = state.preferences.activeTafsirSourceId,
            isRefreshing = state.isRefreshingTafsirs,
            onSelect = { src ->
                viewModel.selectDefaultTafsirSource(src)
                showTafsirsSheet = false
            },
            onSyncCatalog = viewModel::syncRemoteTafsirCatalog,
            onDismiss = { showTafsirsSheet = false }
        )
    }

    if (showDownloadManagerSheet) {
        DownloadManagerSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showDownloadManagerSheet = false }
        )
    }

    if (showTajweedGuide) {
        TajweedGuideSheet(onDismiss = { showTajweedGuide = false })
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        ElevatedCard(
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AudioSettingsModalSheet(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val cacheMb = state.offlineCacheSizeBytes / (1024f * 1024f)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Playback & Hifz Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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
                    Text("Continue playback automatically to the next Surah", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Play single-word audio when inspecting word morphology", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LanguagePickerModalSheet(
    uiLanguageCode: String,
    wbwLanguageCode: String,
    onSelectUiLanguage: (String) -> Unit,
    onSelectWbwLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.ui_language_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UI_LANGUAGES.forEach { (code, label) ->
                    FilterChip(
                        selected = uiLanguageCode == code,
                        onClick = { onSelectUiLanguage(code) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("ui_lang_$code")
                    )
                }
            }

            HorizontalDivider()

            Text("Word-by-Word Meaning Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WBW_LANGUAGES.forEach { (code, label) ->
                    FilterChip(
                        selected = wbwLanguageCode == code,
                        onClick = { onSelectWbwLanguage(code) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("wbw_lang_$code")
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePickerModalSheet(
    activeTheme: NoorThemeMode,
    onSelectTheme: (NoorThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        NoorThemeMode.SERENE_EMERALD to stringResource(R.string.theme_serene_emerald),
        NoorThemeMode.SOFT_SEPIA to stringResource(R.string.theme_sepia),
        NoorThemeMode.SLATE_DARK to stringResource(R.string.theme_slate_dark),
        NoorThemeMode.AMOLED_BLACK to stringResource(R.string.theme_amoled)
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            options.forEach { (mode, label) ->
                val selected = activeTheme == mode
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_chip_${mode.name}")
                        .clickable { onSelectTheme(mode) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (selected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationsPickerModalSheet(
    sources: List<TranslationSourceEntity>,
    busySourceId: Int?,
    onToggle: (TranslationSourceEntity, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var langFilter by remember { mutableStateOf("ALL") }
    val langs = remember(sources) {
        listOf("ALL") + sources.map { it.languageCode.uppercase() }.distinct()
    }
    val filtered = remember(sources, query, langFilter) {
        sources.filter { src ->
            val matchesLang = langFilter == "ALL" || src.languageCode.equals(langFilter, ignoreCase = true)
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                src.translatorName.contains(q, ignoreCase = true) ||
                src.languageName.contains(q, ignoreCase = true)
            matchesLang && matchesQuery
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
                text = "Quran Translations (${sources.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search translator or language…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(langs, key = { "trl_$it" }) { code ->
                    FilterChip(
                        selected = langFilter == code,
                        onClick = { langFilter = code },
                        label = { Text(if (code == "ALL") "All" else code) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { "tr_${it.id}" }) { src ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (src.isEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.translatorName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "${src.languageName} • ${src.fileSizeKb} KB • ${if (src.isDownloaded) "Offline" else "Cloud"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (busySourceId == src.id) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Switch(
                                    checked = src.isEnabled,
                                    onCheckedChange = { enabled -> onToggle(src, enabled) },
                                    modifier = Modifier.testTag("translation_switch_${src.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TafsirsPickerModalSheet(
    sources: List<TafsirSourceEntity>,
    activeTafsirSourceId: Int,
    isRefreshing: Boolean,
    onSelect: (TafsirSourceEntity) -> Unit,
    onSyncCatalog: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var langFilter by remember { mutableStateOf("ALL") }
    val langs = remember(sources) {
        listOf("ALL") + sources.map { it.languageCode.uppercase() }.distinct()
    }
    val filtered = remember(sources, query, langFilter) {
        sources.filter { src ->
            val matchesLang = langFilter == "ALL" || src.languageCode.equals(langFilter, ignoreCase = true)
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                src.tafsirName.contains(q, ignoreCase = true) ||
                src.languageName.contains(q, ignoreCase = true)
            matchesLang && matchesQuery
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tafsir Library (${sources.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = onSyncCatalog,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("sync_tafsir_catalog_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search Tafsir name or language…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(langs, key = { "tl_$it" }) { code ->
                    FilterChip(
                        selected = langFilter == code,
                        onClick = { langFilter = code },
                        label = { Text(if (code == "ALL") "All" else code) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { "taf_${it.id}" }) { src ->
                    val isSelected = src.id == activeTafsirSourceId
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(src) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = src.tafsirName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
            }
        }
    }
}
