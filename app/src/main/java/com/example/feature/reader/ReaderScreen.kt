package com.example.feature.reader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.database.AyahEntity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.rememberCoroutineScope
import com.example.core.localization.BidiUtils
import com.example.core.localization.LineTranslator
import com.example.core.localization.QuranScriptType
import com.example.core.localization.ReaderCardStyle
import com.example.core.localization.ReaderViewMode
import com.example.feature.settings.TafsirsPickerModalSheet
import com.example.feature.settings.TajweedGuideSheet
import com.example.feature.settings.TranslationsPickerModalSheet
import com.example.feature.tafsir.TafsirSheet
import com.example.ui.components.AudioHifzBar
import com.example.ui.components.LanguageTranslationPickerSheet
import com.example.ui.components.QariPickerModalSheet
import com.example.ui.components.RecitationSettingsModalSheet
import com.example.ui.components.ShareCardGenerator
import com.example.ui.components.TajweedTextFormatter
import com.example.ui.components.WordByWordFlowRow
import com.example.ui.theme.LocalTajweedPalette
import com.example.ui.theme.arabicLineHeightMultiplier
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val INLINE_TAFSIR_TAG_REGEX = Regex("<[^>]+>")
private val INLINE_TAFSIR_NEWLINES_REGEX = Regex("\n{2,}")

private fun formatInlineTafsirText(raw: String): String = raw
    .replace(INLINE_TAFSIR_TAG_REGEX, "\n")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&rsquo;", "’")
    .replace("&lsquo;", "‘")
    .replace("&ldquo;", "“")
    .replace("&rdquo;", "”")
    .replace("&mdash;", "—")
    .replace("&ndash;", "–")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(INLINE_TAFSIR_NEWLINES_REGEX, "\n\n")
    .trim()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    surahId: Int,
    targetAyah: Int,
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    targetPage: Int = 0,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(surahId) {
        viewModel.loadSurah(surahId)
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackCursor by viewModel.playbackCursor.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val lineState by viewModel.lineTranslationState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val tajweedPalette = LocalTajweedPalette.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var showTranslationsSheet by remember { mutableStateOf(false) }
    var showTafsirsSheet by remember { mutableStateOf(false) }
    var showTajweedGuide by remember { mutableStateOf(false) }
    var showRecitationSettings by remember { mutableStateOf(false) }
    var showQariPickerFromReader by remember { mutableStateOf(false) }
    var showSurahSwitcher by remember { mutableStateOf(false) }
    var showBatchLangPicker by remember { mutableStateOf(false) }
    var pageJumperCurrentPage by remember { mutableStateOf<Int?>(null) }
    var lineTranslateAyah by remember { mutableStateOf<AyahDisplayBundle?>(null) }
    var mushafChromeVisible by rememberSaveable { mutableStateOf(true) }
    var pendingTargetAyah by rememberSaveable { mutableIntStateOf(targetAyah) }
    var pendingTargetPage by rememberSaveable { mutableIntStateOf(targetPage) }
    var lastScrolledSurahId by rememberSaveable { mutableIntStateOf(surahId) }
    var selectedMushafAyah by remember { mutableStateOf<AyahEntity?>(null) }
    var lastObservedPlaybackSurahId by remember { mutableIntStateOf(playbackCursor.surahId) }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    if (showBatchLangPicker) {
        LanguageTranslationPickerSheet(
            selectedLanguageCode = lineState.activeBatchLanguage,
            onSelectLanguage = { opt ->
                viewModel.translateSurahLines(opt.code)
            },
            onDismiss = { showBatchLangPicker = false }
        )
    }

    lineTranslateAyah?.let { bundle ->
        LanguageTranslationPickerSheet(
            selectedLanguageCode = lineState.ayahLines[bundle.ayah.id]?.first,
            onSelectLanguage = { opt ->
                viewModel.translateAyahLine(bundle, opt.code)
                lineTranslateAyah = null
            },
            onDismiss = { lineTranslateAyah = null }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Playback starts regardless; granted enables foreground notification card */ }

    val playAyahWithBackgroundPermission: (AyahEntity) -> Unit = { ayah ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        viewModel.playAyah(ayah)
    }

    LaunchedEffect(targetAyah, targetPage) {
        pendingTargetAyah = targetAyah
        pendingTargetPage = targetPage
    }

    val mushafPages = remember(state.ayahs) {
        state.ayahs.groupBy { it.ayah.pageNumber }.entries.map { it.key to it.value }
    }

    val activeSurahId = state.surah?.id ?: surahId
    val showTopBismillah = state.preferences.readerViewMode == ReaderViewMode.WORD_BY_WORD && state.surah?.bismillahPre == true
    val headerOffset = if (showTopBismillah) 1 else 0

    // Scroll cleanly to targetPage, targetAyah, or reset to top when Surah changes (mode-aware for WBW vs Mushaf)
    LaunchedEffect(activeSurahId, state.ayahs.size, pendingTargetAyah, pendingTargetPage, state.preferences.readerViewMode, headerOffset) {
        if (state.ayahs.isEmpty()) return@LaunchedEffect
        val surahChanged = lastScrolledSurahId != activeSurahId
        if (surahChanged) {
            lastScrolledSurahId = activeSurahId
        }
        val effectivePage = pendingTargetPage
        val effectiveTarget = pendingTargetAyah

        if (effectivePage > 0) {
            val foundPageIndex = if (state.preferences.readerViewMode == ReaderViewMode.MUSHAF) {
                mushafPages.indexOfFirst { (pageNum, _) -> pageNum == effectivePage }
                    .let { if (it >= 0) it + headerOffset else -1 }
            } else {
                state.ayahs.indexOfFirst { it.ayah.pageNumber == effectivePage }
                    .let { if (it >= 0) it + headerOffset else -1 }
            }
            if (foundPageIndex >= 0) {
                listState.scrollToItem(foundPageIndex)
                pendingTargetPage = 0
                pendingTargetAyah = 1
                return@LaunchedEffect
            }
        }

        val foundIndex = if (state.preferences.readerViewMode == ReaderViewMode.MUSHAF) {
            if (effectiveTarget <= 1) 0
            else mushafPages.indexOfFirst { (_, pageAyahs) ->
                pageAyahs.any { it.ayah.ayahNumber == effectiveTarget }
            }.let { if (it >= 0) it + headerOffset else -1 }
        } else {
            if (effectiveTarget <= 1) 0
            else state.ayahs.indexOfFirst { it.ayah.ayahNumber == effectiveTarget }
                .let { if (it >= 0) it + headerOffset else -1 }
        }

        if (effectiveTarget > 1 && foundIndex >= 0) {
            listState.scrollToItem(foundIndex)
            pendingTargetAyah = 1
        } else if (surahChanged && effectiveTarget <= 1 && effectivePage <= 0) {
            listState.scrollToItem(0)
        }
    }

    // Auto-scroll when currently playing Ayah advances
    LaunchedEffect(playbackCursor.ayahNumber, playbackCursor.surahId, playbackCursor.isPlaying, state.preferences.readerViewMode, headerOffset) {
        if (playbackCursor.isPlaying && playbackCursor.surahId == activeSurahId) {
            val targetNum = playbackCursor.ayahNumber
            val listIndex = if (state.preferences.readerViewMode == ReaderViewMode.MUSHAF) {
                val pIdx = mushafPages.indexOfFirst { (_, pageAyahs) ->
                    pageAyahs.any { it.ayah.ayahNumber == targetNum }
                }
                if (pIdx >= 0) pIdx + headerOffset else -1
            } else {
                val aIdx = state.ayahs.indexOfFirst { it.ayah.ayahNumber == targetNum }
                if (aIdx >= 0) aIdx + headerOffset else -1
            }
            if (listIndex >= 0) {
                if (kotlin.math.abs(listIndex - listState.firstVisibleItemIndex) > 3) {
                    listState.scrollToItem(listIndex)
                } else {
                    listState.animateScrollToItem(listIndex)
                }
            }
        }
    }

    // Sync ReaderScreen ONLY if active Surah playback auto-advances to the next Surah
    LaunchedEffect(playbackCursor.isVisible, playbackCursor.isPlaying, playbackCursor.surahId) {
        val pbSurah = playbackCursor.surahId
        if (playbackCursor.isVisible &&
            playbackCursor.isPlaying &&
            lastObservedPlaybackSurahId == activeSurahId &&
            pbSurah != activeSurahId &&
            pbSurah in 1..114
        ) {
            lastObservedPlaybackSurahId = pbSurah
            pendingTargetAyah = 1
            pendingTargetPage = 0
            selectedMushafAyah = null
            viewModel.loadSurah(pbSurah)
        } else {
            lastObservedPlaybackSurahId = pbSurah
        }
    }

    val showChrome = state.preferences.readerViewMode == ReaderViewMode.WORD_BY_WORD || mushafChromeVisible

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showChrome,
        drawerContent = {
            ReaderSidebarDrawerContent(
                surahTitle = state.localizedSurahName,
                preferences = state.preferences,
                translationSources = state.translationSources,
                busyTranslationSourceId = state.busyTranslationSourceId,
                tafsirSources = state.tafsirSources,
                onClose = { coroutineScope.launch { drawerState.close() } },
                onToggleShowWordByWord = viewModel::setShowWordByWord,
                onToggleShowTranslation = viewModel::setShowTranslation,
                onToggleShowInlineTafsir = viewModel::setShowInlineTafsir,
                onToggleTajweed = viewModel::setTajweedEnabled,
                onToggleTransliteration = viewModel::setShowTransliteration,
                onArabicSizeChange = viewModel::setArabicFontSize,
                onWbwSizeChange = viewModel::setWbwFontSize,
                onTranslationSizeChange = viewModel::setTranslationFontSize,
                onSelectArabicFont = viewModel::setArabicFontFamily,
                onSelectScript = viewModel::setScriptType,
                onSelectCardStyle = viewModel::setReaderCardStyle,
                onSelectTheme = viewModel::setThemeMode,
                onSelectWbwLanguage = viewModel::setWbwLanguage,
                onToggleTranslationSource = viewModel::toggleTranslationSource,
                onOpenAllTranslations = {
                    coroutineScope.launch { drawerState.close() }
                    showTranslationsSheet = true
                },
                onSelectActiveTafsirSource = viewModel::selectActiveTafsirSource,
                onOpenAllTafsirs = {
                    coroutineScope.launch { drawerState.close() }
                    showTafsirsSheet = true
                },
                onOpenTajweedGuide = {
                    coroutineScope.launch { drawerState.close() }
                    showTajweedGuide = true
                },
                onSelectMushafStyle = viewModel::setMushafStyle
            )
        }
    ) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AnimatedVisibility(visible = showChrome) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("reader_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    title = {
                        Column(
                            modifier = Modifier
                                .clickable { showSurahSwitcher = true }
                                .testTag("reader_surah_switcher_trigger")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.localizedSurahName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = "Switch Surah",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            state.surah?.let { surah ->
                                Text(
                                    text = "Al-Quran • ${surah.nameArabic} • ${BidiUtils.formatNumber(surah.ayahCount, state.preferences.uiLanguageCode)} Ayahs • Juz ${surah.startJuz}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.ayahs.isNotEmpty()) {
                            IconButton(
                                onClick = { playAyahWithBackgroundPermission(state.ayahs.first().ayah) },
                                modifier = Modifier.testTag("reader_play_surah_button")
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Play Surah"
                                )
                            }
                        }
                        if (state.preferences.readerViewMode == ReaderViewMode.MUSHAF) {
                            IconButton(
                                onClick = { mushafChromeVisible = false },
                                modifier = Modifier.testTag("mushaf_focus_toggle_button")
                            ) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = "Distraction-free Mushaf reading"
                                )
                            }
                        }
                        if (state.surah?.isDownloaded != true) {
                            IconButton(
                                onClick = { viewModel.downloadCurrentSurah() },
                                modifier = Modifier.testTag("reader_download_surah_button")
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = "Download Surah",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { showRecitationSettings = true },
                            modifier = Modifier.testTag("reader_audio_settings_button")
                        ) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = "Recitation & audio settings"
                            )
                        }
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("reader_display_settings_button")
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.cd_reader_sidebar)
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            AudioHifzBar(
                playbackState = playbackState,
                reciters = state.reciters,
                autoPlayWordOnInspect = state.preferences.autoPlayWordOnInspect,
                onTogglePlayPause = viewModel::togglePlayPause,
                onPreviousAyah = viewModel::previousAyah,
                onNextAyah = viewModel::nextAyah,
                onSeekTo = viewModel::seekAudioTo,
                onDismiss = viewModel::dismissAudioBar,
                onSelectReciter = viewModel::selectReciter,
                onUpdateHifzConfig = viewModel::updateHifzConfig,
                onSetSleepTimerMinutes = viewModel::setSleepTimerMinutes,
                onDownloadSurahAudio = viewModel::downloadSurahAudio,
                onClearAudioCache = viewModel::clearAudioCache,
                onToggleAutoPlayWordOnInspect = viewModel::setAutoPlayWordOnInspect
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                // Mode Toggle Row (Word-by-Word vs Mushaf)
                AnimatedVisibility(visible = showChrome) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        SegmentedButton(
                            selected = state.preferences.readerViewMode == ReaderViewMode.WORD_BY_WORD,
                            onClick = { viewModel.toggleViewMode(ReaderViewMode.WORD_BY_WORD) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            modifier = Modifier.testTag("mode_wbw_button")
                        ) {
                            Text(stringResource(R.string.mode_word_by_word))
                        }
                        SegmentedButton(
                            selected = state.preferences.readerViewMode == ReaderViewMode.MUSHAF,
                            onClick = { viewModel.toggleViewMode(ReaderViewMode.MUSHAF) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            modifier = Modifier.testTag("mode_mushaf_button")
                        ) {
                            Text(stringResource(R.string.mode_mushaf))
                        }
                    }
                }

                // Surah-level Translation Action Bar (Instant বাংলা + Any Language)
                AnimatedVisibility(visible = showChrome && state.ayahs.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (lineState.activeBatchLanguage != null) {
                                        stringResource(R.string.translate_all_in_lang_fmt, LineTranslator.languageLabel(lineState.activeBatchLanguage!!))
                                    } else {
                                        stringResource(R.string.translate_line_action)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (lineState.isBatchTranslating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                                if (lineState.ayahLines.isNotEmpty()) {
                                    TextButton(
                                        onClick = viewModel::clearAllLineTranslations,
                                        modifier = Modifier.height(32.dp).testTag("clear_all_line_translations_button")
                                    ) {
                                        Text(stringResource(R.string.action_clear), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.translateSurahLines("bn") },
                                    modifier = Modifier.height(32.dp).testTag("surah_translate_bn_button")
                                ) {
                                    Text(stringResource(R.string.translate_all_bangla), style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { showBatchLangPicker = true },
                                    modifier = Modifier.height(32.dp).testTag("surah_translate_any_button")
                                ) {
                                    Text(stringResource(R.string.translate_any_language), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Resume Last Recitation Quick Chip (when audio bar is closed and a previous position is saved)
                AnimatedVisibility(visible = showChrome && !playbackState.isVisible && playbackState.hasSavedRecitation) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("reader_resume_recitation_button")
                            .clickable { viewModel.resumeLastRecitation() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.resume_recitation_fmt,
                                        BidiUtils.formatNumber(playbackState.lastRecitedSurahId, state.preferences.uiLanguageCode),
                                        BidiUtils.formatNumber(playbackState.lastRecitedAyahNumber, state.preferences.uiLanguageCode)
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = stringResource(R.string.resume_recitation),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Non-blocking partial Surah / downloading progress banner
                if (state.ayahs.isNotEmpty() && (state.isDownloadingSurah || state.isSurahIncomplete || state.downloadError != null)) {
                    Surface(
                        color = if (state.downloadError != null) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (state.isDownloadingSurah) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.5.dp))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val expected = state.surah?.ayahCount ?: state.ayahs.size
                                val bannerText = when {
                                    state.isDownloadingSurah -> stringResource(R.string.reader_loading_surah_fmt, state.ayahs.size, expected)
                                    state.downloadError != null -> stringResource(R.string.reader_cached_err_fmt, state.ayahs.size, expected)
                                    else -> stringResource(R.string.reader_showing_partial_fmt, state.ayahs.size, expected)
                                }
                                Text(
                                    text = bannerText,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!state.isDownloadingSurah) {
                                    TextButton(
                                        onClick = { viewModel.downloadCurrentSurah() },
                                        modifier = Modifier.testTag("sync_full_surah_banner_button")
                                    ) {
                                        Text(if (state.downloadError != null) stringResource(R.string.action_retry) else stringResource(R.string.reader_download_full))
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.ayahs.isEmpty()) {
                    // Fetching or Download prompt for Surahs not in starter bundle
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.localizedSurahName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.surah?.let { s ->
                            Text(
                                text = stringResource(R.string.reader_ayahs_count_fmt, s.nameArabic, s.ayahCount),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (state.isDownloadingSurah) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.reader_empty_loading_msg),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.cancelDownload() },
                                modifier = Modifier.testTag("cancel_surah_download_button")
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.reader_empty_prompt_msg),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.downloadError?.let { err ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { viewModel.downloadCurrentSurah() },
                                modifier = Modifier.testTag("download_current_surah_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.download_surah))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (showTopBismillah) {
                            item(key = "bismillah_${activeSurahId}", contentType = "bismillah") {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                                        fontFamily = arabicScriptFont(state.preferences.scriptType, state.preferences.arabicFontFamily),
                                        fontSize = 26.sp,
                                        lineHeight = (26f * arabicLineHeightMultiplier(state.preferences.scriptType, state.preferences.arabicFontFamily)).sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }

                        if (state.preferences.readerViewMode == ReaderViewMode.MUSHAF) {
                            itemsIndexed(
                                items = mushafPages,
                                key = { _, item -> "page_${item.first}" },
                                contentType = { _, _ -> "mushaf_page" }
                            ) { pageIndex, (pageNumber, pageAyahs) ->
                                val playingAyahNum = if (playbackCursor.isVisible && playbackCursor.surahId == activeSurahId) {
                                    playbackCursor.ayahNumber
                                } else null

                                MushafPageCard(
                                    pageNumber = pageNumber,
                                    pageAyahs = pageAyahs,
                                    surah = state.surah,
                                    localizedSurahName = state.localizedSurahName,
                                    mushafStyle = state.preferences.mushafStyle,
                                    scriptType = state.preferences.scriptType,
                                    arabicFontFamily = state.preferences.arabicFontFamily,
                                    arabicFontSizeSp = state.preferences.arabicFontSizeSp,
                                    tajweedColorsEnabled = state.preferences.tajweedColorsEnabled,
                                    tajweedPalette = tajweedPalette,
                                    uiLanguageCode = state.preferences.uiLanguageCode,
                                    playingAyahNumber = playingAyahNum,
                                    selectedAyahId = selectedMushafAyah?.id,
                                    chromeVisible = mushafChromeVisible,
                                    hasPrevPage = pageIndex > 0,
                                    hasNextPage = pageIndex < mushafPages.lastIndex,
                                    onToggleChrome = { mushafChromeVisible = !mushafChromeVisible },
                                    onAyahClick = { ayah ->
                                        selectedMushafAyah = ayah
                                        viewModel.recordAyahRead(ayah)
                                    },
                                    onOpenPageJumper = { pageJumperCurrentPage = pageNumber },
                                    onPrevPage = {
                                        if (pageIndex > 0) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((pageIndex - 1 + headerOffset).coerceAtLeast(0))
                                            }
                                        }
                                    },
                                    onNextPage = {
                                        if (pageIndex < mushafPages.lastIndex) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(pageIndex + 1 + headerOffset)
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            items(
                                items = state.ayahs,
                                key = { it.ayah.id },
                                contentType = { "ayah_card" }
                            ) { bundle ->
                                val isPlayingThis = playbackCursor.surahId == bundle.ayah.surahId &&
                                    playbackCursor.ayahNumber == bundle.ayah.ayahNumber &&
                                    playbackCursor.isVisible
                                val activeWordPos by if (isPlayingThis) {
                                    remember(bundle.ayah.surahId, bundle.ayah.ayahNumber) {
                                        viewModel.playbackState
                                            .map { pb ->
                                                if (pb.isVisible && pb.surahId == bundle.ayah.surahId && pb.ayahNumber == bundle.ayah.ayahNumber) {
                                                    pb.activeWordPosition
                                                } else null
                                            }
                                            .distinctUntilChanged()
                                    }.collectAsStateWithLifecycle(initialValue = null)
                                } else {
                                    remember { mutableStateOf<Int?>(null) }
                                }

                                AyahStyleContainer(
                                    cardStyle = state.preferences.readerCardStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ayah_card_${bundle.ayah.verseKey}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Ayah Header Action Bar
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(
                                                    text = BidiUtils.wrapVerseKey(bundle.ayah.verseKey),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { playAyahWithBackgroundPermission(bundle.ayah) },
                                                    modifier = Modifier.testTag("play_ayah_${bundle.ayah.verseKey}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = stringResource(R.string.cd_play_ayah)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.toggleBookmark(bundle.ayah) },
                                                    modifier = Modifier.testTag("bookmark_ayah_${bundle.ayah.verseKey}")
                                                ) {
                                                    Icon(
                                                        if (bundle.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                        contentDescription = stringResource(R.string.cd_bookmark_ayah),
                                                        tint = if (bundle.isBookmarked) {
                                                            MaterialTheme.colorScheme.secondary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.openTafsir(bundle.ayah) },
                                                    modifier = Modifier.testTag("open_tafsir_${bundle.ayah.verseKey}")
                                                ) {
                                                    Icon(
                                                        if (bundle.hasNote) Icons.Default.EditNote else Icons.AutoMirrored.Filled.MenuBook,
                                                        contentDescription = stringResource(R.string.cd_open_tafsir)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val tr = bundle.translations.firstOrNull()
                                                        ShareCardGenerator.shareAyahAsImageCard(
                                                            context = context,
                                                            surahName = state.localizedSurahName,
                                                            verseKey = bundle.ayah.verseKey,
                                                            arabicText = bundle.ayah.textUthmani,
                                                            translationText = tr?.translatedText.orEmpty(),
                                                            translatorName = tr?.translatorName ?: "Quran Translation"
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("share_card_${bundle.ayah.verseKey}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.Share,
                                                        contentDescription = stringResource(R.string.cd_share_ayah)
                                                    )
                                                }
                                                val isTranslatingThisAyah = bundle.ayah.id in lineState.translatingAyahIds
                                                IconButton(
                                                    onClick = { lineTranslateAyah = bundle },
                                                    modifier = Modifier.testTag("translate_ayah_${bundle.ayah.verseKey}")
                                                ) {
                                                    if (isTranslatingThisAyah) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    } else {
                                                        Icon(
                                                            Icons.Default.Translate,
                                                            contentDescription = stringResource(R.string.translate_line_action),
                                                            tint = if (bundle.ayah.id in lineState.ayahLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val firstTr = bundle.translations.firstOrNull()?.translatedText.orEmpty()
                                                        val clip = "${bundle.ayah.textUthmani}\n$firstTr\n[${state.localizedSurahName} ${bundle.ayah.verseKey}]"
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Quran Ayah", clip))
                                                        Toast.makeText(context, context.getString(R.string.ayah_copied_toast), Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.testTag("copy_ayah_${bundle.ayah.verseKey}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.ContentCopy,
                                                        contentDescription = stringResource(R.string.cd_copy_ayah)
                                                    )
                                                }
                                            }
                                        }

                                        // Full Arabic Ayah (Memoized, supports Uthmani Tajweed and preserves sanitized IndoPak script)
                                        val isIndoPak = state.preferences.scriptType == QuranScriptType.INDOPAK
                                        val arabicRaw = if (isIndoPak) {
                                            BidiUtils.sanitizeIndoPakArabic(bundle.ayah.textIndopak.ifBlank { bundle.ayah.textUthmani })
                                        } else {
                                            bundle.ayah.textUthmani
                                        }
                                        val formattedArabic = remember(
                                            arabicRaw,
                                            bundle.ayah.textTajweedHtml,
                                            state.preferences.tajweedColorsEnabled,
                                            isIndoPak,
                                            tajweedPalette,
                                            bundle.ayah.ayahNumber
                                        ) {
                                            TajweedTextFormatter.formatArabicVerse(
                                                rawText = arabicRaw,
                                                tajweedHtml = bundle.ayah.textTajweedHtml,
                                                tajweedEnabled = state.preferences.tajweedColorsEnabled,
                                                palette = tajweedPalette,
                                                ayahEndMarker = BidiUtils.formatAyahMarker(bundle.ayah.ayahNumber, useArabicDigits = true),
                                                useTajweedMarkup = !isIndoPak
                                            )
                                        }
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                            Text(
                                                text = formattedArabic,
                                                fontFamily = arabicScriptFont(state.preferences.scriptType, state.preferences.arabicFontFamily),
                                                fontSize = state.preferences.arabicFontSizeSp.sp,
                                                lineHeight = (state.preferences.arabicFontSizeSp * arabicLineHeightMultiplier(state.preferences.scriptType, state.preferences.arabicFontFamily)).sp,
                                                textAlign = TextAlign.Start,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Word-by-Word RTL FlowRow (independently toggleable in Reader Sidebar)
                                        if (state.preferences.showWordByWord && bundle.words.isNotEmpty()) {
                                            WordByWordFlowRow(
                                                words = bundle.words,
                                                scriptType = state.preferences.scriptType,
                                                wbwLanguageCode = state.preferences.wbwLanguageCode,
                                                arabicFontSizeSp = (state.preferences.arabicFontSizeSp * 0.72f).coerceAtLeast(18f),
                                                wbwFontSizeSp = state.preferences.wbwFontSizeSp,
                                                showTransliteration = state.preferences.showTransliteration,
                                                activeWordPosition = activeWordPos,
                                                ayahNumber = bundle.ayah.ayahNumber,
                                                onWordClick = { wItem ->
                                                    viewModel.inspectWord(wItem, bundle.ayah.verseKey)
                                                },
                                                arabicFontFamily = state.preferences.arabicFontFamily
                                            )
                                        }

                                        val lineTr = lineState.ayahLines[bundle.ayah.id]
                                        val hasVisibleTranslation = state.preferences.showTranslation &&
                                            (bundle.translations.isNotEmpty() || lineTr != null)

                                        if (hasVisibleTranslation || state.preferences.showInlineTafsir) {
                                            HorizontalDivider(thickness = 0.6.dp)
                                        }

                                        // Multi-translation Stack (hidden cleanly when reading Arabic + WBW only)
                                        if (state.preferences.showTranslation) {
                                            bundle.translations.forEach { tr ->
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = tr.translatedText,
                                                        style = banglaSafeBodyStyle(
                                                            state.preferences.translationFontSizeSp,
                                                            tr.languageCode
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = tr.translatorName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            // Dynamic Line Translation (Instant বাংলা or Any Selected Language)
                                            if (lineTr != null) {
                                                Surface(
                                                    shape = MaterialTheme.shapes.small,
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Text(
                                                                text = LineTranslator.languageLabel(lineTr.first),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = lineTr.second,
                                                                style = banglaSafeBodyStyle(
                                                                    state.preferences.translationFontSizeSp,
                                                                    lineTr.first
                                                                ),
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.clearAyahLineTranslation(bundle.ayah.id) },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = stringResource(R.string.action_clear),
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Inline Tafsir directly at the bottom of each Ayah card (toggled from Reader Sidebar)
                                        if (state.preferences.showInlineTafsir) {
                                            val activeTafsirMeta = state.tafsirSources.firstOrNull {
                                                it.id == state.preferences.activeTafsirSourceId
                                            }
                                            val inlineTafsir = bundle.inlineTafsir
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                                border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("inline_tafsir_card_${bundle.ayah.verseKey}")
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                            Text(
                                                                text = inlineTafsir?.let { "${it.tafsirName} (${it.languageCode.uppercase()})" }
                                                                    ?: activeTafsirMeta?.let { "${it.tafsirName} (${it.languageCode.uppercase()})" }
                                                                    ?: stringResource(R.string.tafsir_title),
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        TextButton(
                                                            onClick = { viewModel.openTafsir(bundle.ayah) },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            modifier = Modifier.testTag("inline_tafsir_open_${bundle.ayah.verseKey}")
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.inline_tafsir_full_btn),
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    }

                                                    if (inlineTafsir != null) {
                                                        val cleanTafsirBody = remember(inlineTafsir.tafsirHtmlOrMarkdown) {
                                                            formatInlineTafsirText(inlineTafsir.tafsirHtmlOrMarkdown)
                                                        }
                                                        var isTafsirExpanded by remember(bundle.ayah.id, inlineTafsir.sourceId) {
                                                            mutableStateOf(false)
                                                        }
                                                        val isRtlTafsir = inlineTafsir.languageCode.lowercase() in setOf("ar", "ur", "fa", "ps")
                                                        CompositionLocalProvider(
                                                            LocalLayoutDirection provides if (isRtlTafsir) LayoutDirection.Rtl else LayoutDirection.Ltr
                                                        ) {
                                                            Text(
                                                                text = cleanTafsirBody,
                                                                style = banglaSafeBodyStyle(
                                                                    (state.preferences.translationFontSizeSp - 1f).coerceAtLeast(12f),
                                                                    inlineTafsir.languageCode
                                                                ),
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = if (isTafsirExpanded) Int.MAX_VALUE else 5,
                                                                overflow = TextOverflow.Ellipsis,
                                                                textAlign = TextAlign.Start,
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                        if (cleanTafsirBody.length > 260) {
                                                            TextButton(
                                                                onClick = { isTafsirExpanded = !isTafsirExpanded },
                                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                                modifier = Modifier.testTag("inline_tafsir_expand_${bundle.ayah.verseKey}")
                                                            ) {
                                                                Text(
                                                                    text = if (isTafsirExpanded) {
                                                                        stringResource(R.string.inline_tafsir_show_less)
                                                                    } else {
                                                                        stringResource(R.string.inline_tafsir_read_more)
                                                                    },
                                                                    style = MaterialTheme.typography.labelMedium
                                                                )
                                                            }
                                                        }
                                                    } else if (state.isLoadingInlineTafsirs) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                            Text(
                                                                text = stringResource(R.string.inline_tafsir_loading),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    } else {
                                                        FilledTonalButton(
                                                            onClick = { viewModel.fetchInlineTafsirForAyah(bundle.ayah) },
                                                            modifier = Modifier.testTag("inline_tafsir_fetch_${bundle.ayah.verseKey}")
                                                        ) {
                                                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(stringResource(R.string.inline_tafsir_fetch_btn))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Previous / Next Surah Navigation Footer
                        item(key = "surah_footer_${activeSurahId}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (activeSurahId > 1) {
                                    OutlinedButton(
                                        onClick = {
                                            pendingTargetAyah = 1
                                            pendingTargetPage = 0
                                            selectedMushafAyah = null
                                            viewModel.loadSurah(activeSurahId - 1)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reader_prev_surah_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.reader_prev_surah))
                                    }
                                }
                                if (activeSurahId < 114) {
                                    FilledTonalButton(
                                        onClick = {
                                            pendingTargetAyah = 1
                                            pendingTargetPage = 0
                                            selectedMushafAyah = null
                                            viewModel.loadSurah(activeSurahId + 1)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("reader_next_surah_button")
                                    ) {
                                        Text(stringResource(R.string.reader_next_surah))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(72.dp)) }
                    }
                }
            }
        }
    }
    } // end ModalNavigationDrawer

    // Mushaf Ayah Quick Action Bottom Sheet
    selectedMushafAyah?.let { mushafAyah ->
        val bundle = state.ayahs.firstOrNull { it.ayah.id == mushafAyah.id }
        val isIndoPak = state.preferences.scriptType == QuranScriptType.INDOPAK
        val previewArabic = if (isIndoPak) {
            BidiUtils.sanitizeIndoPakArabic(mushafAyah.textIndopak.ifBlank { mushafAyah.textUthmani })
        } else {
            mushafAyah.textUthmani
        }
        val firstTranslation = bundle?.translations?.firstOrNull()

        ModalBottomSheet(onDismissRequest = { selectedMushafAyah = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${state.localizedSurahName} • ${BidiUtils.wrapVerseKey(mushafAyah.verseKey)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.reader_page_juz_fmt, mushafAyah.pageNumber, mushafAyah.juzNumber),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = previewArabic,
                        fontFamily = arabicScriptFont(state.preferences.scriptType, state.preferences.arabicFontFamily),
                        fontSize = 24.sp,
                        lineHeight = (24f * arabicLineHeightMultiplier(state.preferences.scriptType, state.preferences.arabicFontFamily)).sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.preferences.showTranslation) {
                    firstTranslation?.let { tr ->
                        Text(
                            text = tr.translatedText,
                            style = banglaSafeBodyStyle(15f, tr.languageCode),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.preferences.showInlineTafsir && bundle?.inlineTafsir != null) {
                    val cleanMushafTafsir = remember(bundle.inlineTafsir.tafsirHtmlOrMarkdown) {
                        formatInlineTafsirText(bundle.inlineTafsir.tafsirHtmlOrMarkdown)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = bundle.inlineTafsir.tafsirName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = cleanMushafTafsir,
                                style = banglaSafeBodyStyle(14f, bundle.inlineTafsir.languageCode),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                val mushafLineTr = lineState.ayahLines[mushafAyah.id]
                if (mushafLineTr != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = LineTranslator.languageLabel(mushafLineTr.first),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = mushafLineTr.second,
                                style = banglaSafeBodyStyle(15f, mushafLineTr.first),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider()

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            playAyahWithBackgroundPermission(mushafAyah)
                            selectedMushafAyah = null
                        },
                        modifier = Modifier.testTag("mushaf_action_play")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_play))
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleBookmark(mushafAyah) },
                        modifier = Modifier.testTag("mushaf_action_bookmark")
                    ) {
                        Icon(
                            if (bundle?.isBookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (bundle?.isBookmarked == true) stringResource(R.string.action_bookmarked) else stringResource(R.string.action_bookmark))
                    }

                    FilledTonalButton(
                        onClick = {
                            viewModel.openTafsir(mushafAyah)
                            selectedMushafAyah = null
                        },
                        modifier = Modifier.testTag("mushaf_action_tafsir")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.reader_tafsir_note_action))
                    }

                    if (bundle != null) {
                        FilledTonalButton(
                            onClick = {
                                lineTranslateAyah = bundle
                                selectedMushafAyah = null
                            },
                            modifier = Modifier.testTag("mushaf_action_translate")
                        ) {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.translate_line_action))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            pendingTargetAyah = mushafAyah.ayahNumber
                            viewModel.toggleViewMode(ReaderViewMode.WORD_BY_WORD)
                            selectedMushafAyah = null
                        },
                        modifier = Modifier.testTag("mushaf_action_wbw")
                    ) {
                        Text(stringResource(R.string.mode_word_by_word))
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val trText = firstTranslation?.translatedText.orEmpty()
                            val clip = "${mushafAyah.textUthmani}\n$trText\n[${state.localizedSurahName} ${mushafAyah.verseKey}]"
                            clipboard.setPrimaryClip(ClipData.newPlainText("Quran Ayah", clip))
                            Toast.makeText(context, context.getString(R.string.ayah_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("mushaf_action_copy")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_copy))
                    }

                    OutlinedButton(
                        onClick = {
                            ShareCardGenerator.shareAyahAsImageCard(
                                context = context,
                                surahName = state.localizedSurahName,
                                verseKey = mushafAyah.verseKey,
                                arabicText = mushafAyah.textUthmani,
                                translationText = firstTranslation?.translatedText.orEmpty(),
                                translatorName = firstTranslation?.translatorName ?: "Quran Translation"
                            )
                        },
                        modifier = Modifier.testTag("mushaf_action_share")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_share))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSurahSwitcher && state.allSurahs.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showSurahSwitcher = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.reader_jump_to_surah),
                    style = MaterialTheme.typography.titleLarge
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.allSurahs, key = { it.id }) { itemSurah ->
                        val isCurrent = itemSurah.id == activeSurahId
                        val title = BidiUtils.formatSurahTitle(
                            itemSurah.nameTransliteration,
                            itemSurah.localizedNamesJson,
                            state.preferences.uiLanguageCode
                        )
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingTargetAyah = 1
                                    pendingTargetPage = 0
                                    selectedMushafAyah = null
                                    viewModel.loadSurah(itemSurah.id)
                                    showSurahSwitcher = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${itemSurah.id}. $title",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = itemSurah.nameArabic,
                                    fontFamily = arabicScriptFont(state.preferences.scriptType, state.preferences.arabicFontFamily),
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTranslationsSheet) {
        TranslationsPickerModalSheet(
            sources = state.translationSources,
            busySourceId = state.busyTranslationSourceId,
            onToggle = viewModel::toggleTranslationSource,
            onDismiss = { showTranslationsSheet = false }
        )
    }

    if (showTafsirsSheet) {
        TafsirsPickerModalSheet(
            sources = state.tafsirSources,
            activeTafsirSourceId = state.preferences.activeTafsirSourceId,
            isRefreshing = state.isRefreshingTafsirs,
            onSelect = { src ->
                viewModel.selectActiveTafsirSource(src)
                showTafsirsSheet = false
            },
            onSyncCatalog = viewModel::syncRemoteTafsirCatalog,
            onDismiss = { showTafsirsSheet = false }
        )
    }

    if (showTajweedGuide) {
        TajweedGuideSheet(
            uiLanguageCode = state.preferences.uiLanguageCode,
            onDismiss = { showTajweedGuide = false }
        )
    }

    pageJumperCurrentPage?.let { currentP ->
        MushafPageJumperModal(
            currentPage = currentP,
            availablePages = mushafPages.map { it.first },
            uiLanguageCode = state.preferences.uiLanguageCode,
            onJumpToPage = { targetP ->
                val idx = mushafPages.indexOfFirst { it.first == targetP }
                if (idx >= 0) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(idx + headerOffset)
                    }
                }
            },
            onDismiss = { pageJumperCurrentPage = null }
        )
    }

    if (showRecitationSettings) {
        val effectivePlaybackState = if (playbackState.isVisible) {
            playbackState
        } else {
            playbackState.copy(
                surahId = activeSurahId,
                surahName = state.localizedSurahName,
                reciterId = state.preferences.activeReciterId,
                reciterName = state.reciters.firstOrNull { it.id == state.preferences.activeReciterId }?.name
                    ?: playbackState.reciterName
            )
        }
        RecitationSettingsModalSheet(
            playbackState = effectivePlaybackState,
            reciters = state.reciters,
            autoPlayWordOnInspect = state.preferences.autoPlayWordOnInspect,
            onSelectReciter = viewModel::selectReciter,
            onUpdateHifzConfig = viewModel::updateHifzConfig,
            onSetSleepTimerMinutes = viewModel::setSleepTimerMinutes,
            onDownloadSurahAudio = viewModel::downloadSurahAudio,
            onClearAudioCache = viewModel::clearAudioCache,
            onToggleAutoPlayWordOnInspect = viewModel::setAutoPlayWordOnInspect,
            onOpenQariPicker = {
                showRecitationSettings = false
                showQariPickerFromReader = true
            },
            onDismiss = { showRecitationSettings = false }
        )
    }

    if (showQariPickerFromReader && state.reciters.isNotEmpty()) {
        QariPickerModalSheet(
            reciters = state.reciters,
            activeReciterId = state.preferences.activeReciterId,
            onSelectReciter = {
                viewModel.selectReciter(it)
                showQariPickerFromReader = false
            },
            onDismiss = { showQariPickerFromReader = false }
        )
    }

    state.inspectedWord?.let { inspected ->
        WordMorphologySheet(
            inspected = inspected,
            scriptType = state.preferences.scriptType,
            wbwLanguageCode = state.preferences.wbwLanguageCode,
            uiLanguageCode = state.preferences.uiLanguageCode,
            onPlayWordAudio = { speed -> viewModel.playInspectedWord(speed) },
            onNavigateToOccurrence = { targetS, targetA ->
                pendingTargetAyah = targetA
                if (targetS != activeSurahId) {
                    viewModel.loadSurah(targetS)
                }
            },
            onTranslateWord = viewModel::translateInspectedWord,
            onDismiss = viewModel::dismissWordInspector,
            arabicFontFamily = state.preferences.arabicFontFamily
        )
    }

    state.selectedTafsirAyah?.let { selectedAyah ->
        val bundle = state.ayahs.firstOrNull { it.ayah.id == selectedAyah.id }
        TafsirSheet(
            ayah = selectedAyah,
            surahName = state.localizedSurahName,
            translations = bundle?.translations.orEmpty(),
            tafsirSources = state.tafsirSources,
            loadedTafsirs = state.selectedAyahTafsirs,
            selectedTafsirSourceId = state.preferences.activeTafsirSourceId,
            existingNoteText = state.selectedAyahNoteText,
            isFetchingOnlineTafsir = state.isFetchingTafsir,
            tafsirError = state.tafsirError,
            translationFontSizeSp = state.preferences.translationFontSizeSp,
            uiLanguageCode = state.preferences.uiLanguageCode,
            translatedTafsir = lineState.translatedTafsir,
            isTranslatingTafsir = lineState.isTranslatingTafsir,
            onSelectTafsirSource = viewModel::selectTafsirSource,
            onTranslateTafsir = viewModel::translateActiveTafsir,
            onClearTranslatedTafsir = viewModel::clearTranslatedTafsir,
            onSaveNote = viewModel::saveNoteForSelectedAyah,
            onShareAsCard = {
                val tr = bundle?.translations?.firstOrNull()
                ShareCardGenerator.shareAyahAsImageCard(
                    context = context,
                    surahName = state.localizedSurahName,
                    verseKey = selectedAyah.verseKey,
                    arabicText = selectedAyah.textUthmani,
                    translationText = tr?.translatedText.orEmpty(),
                    translatorName = tr?.translatorName ?: "Quran Translation"
                )
            },
            onDismiss = viewModel::closeTafsir
        )
    }
}

@Composable
private fun AyahStyleContainer(
    cardStyle: ReaderCardStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when (cardStyle) {
        ReaderCardStyle.CLASSIC_ELEVATED -> {
            ElevatedCard(
                shape = MaterialTheme.shapes.large,
                modifier = modifier
            ) {
                content()
            }
        }
        ReaderCardStyle.CRISP_OUTLINED -> {
            OutlinedCard(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = modifier
            ) {
                content()
            }
        }
        ReaderCardStyle.MINIMAL_PAPER -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                tonalElevation = 0.dp,
                modifier = modifier
            ) {
                content()
            }
        }
    }
}
