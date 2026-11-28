package com.example.feature.dua

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.database.DuaEntity
import com.example.core.localization.LineTranslator
import com.example.ui.components.LanguageTranslationPickerSheet
import com.example.ui.components.ShareCardGenerator
import com.example.ui.theme.banglaSafeBodyStyle

@Composable
private fun getDuaCategoryLabel(category: String): String = when (category) {
    "Bookmarked" -> stringResource(R.string.dua_cat_bookmarked)
    "Morning & Evening" -> stringResource(R.string.dua_cat_morning_evening)
    "Sleeping & Waking" -> stringResource(R.string.dua_cat_sleeping_waking)
    "Ruqyah & Shifa" -> stringResource(R.string.dua_cat_ruqyah_shifa)
    "Salah & Worship" -> stringResource(R.string.dua_cat_salah_worship)
    "Anxiety & Hardship" -> stringResource(R.string.dua_cat_anxiety_hardship)
    "Forgiveness & Tawbah" -> stringResource(R.string.dua_cat_forgiveness_tawbah)
    "Daily Life & Travel" -> stringResource(R.string.dua_cat_daily_travel)
    "Family & Offspring" -> stringResource(R.string.dua_cat_family_offspring)
    "Protection & Safety" -> stringResource(R.string.dua_cat_protection_safety)
    "Provision & Debt" -> stringResource(R.string.dua_cat_provision_debt)
    "Gratitude & Praise" -> stringResource(R.string.dua_cat_gratitude_praise)
    "Rabbana Quranic Duas" -> stringResource(R.string.dua_cat_rabbana)
    else -> category
}

@Composable
private fun resolveDuaGradeLabel(reference: String): String {
    val ref = reference.lowercase()
    return when {
        ref.contains("quran") || ref.contains("surah") || Regex("""\d+:\d+""").containsMatchIn(ref) ->
            stringResource(R.string.dua_grade_quranic)
        ref.contains("bukhari") || ref.contains("muslim") || ref.contains("sahih") || ref.contains("hisn") ->
            stringResource(R.string.dua_grade_sahih)
        else -> stringResource(R.string.dua_grade_hasan)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuaScreen(
    viewModel: DuaViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val duas by viewModel.duas.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val translationState by viewModel.translationState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var showBatchLangPicker by remember { mutableStateOf(false) }
    var translateTargetDua by remember { mutableStateOf<DuaEntity?>(null) }

    // Group all duas by category
    val duasByCategory = remember(duas) {
        duas.groupBy { it.category }
    }

    if (selectedTopic != null) {
        BackHandler {
            selectedTopic = null
        }
    }

    if (showBatchLangPicker) {
        LanguageTranslationPickerSheet(
            selectedLanguageCode = translationState.activeLanguage,
            onSelectLanguage = { opt ->
                viewModel.translateVisibleDuas(opt.code)
            },
            onDismiss = { showBatchLangPicker = false }
        )
    }

    translateTargetDua?.let { dua ->
        LanguageTranslationPickerSheet(
            selectedLanguageCode = translationState.translatedById[dua.id]?.first,
            onSelectLanguage = { opt ->
                viewModel.translateDua(dua, opt.code)
                translateTargetDua = null
            },
            onDismiss = { translateTargetDua = null }
        )
    }

    LaunchedEffect(audioState.statusMessage) {
        val msg = audioState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedTopic != null) getDuaCategoryLabel(selectedTopic!!) else stringResource(R.string.dua_header_title),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedTopic != null) {
                                "${duasByCategory[selectedTopic]?.size ?: 0} Duas & Parts"
                            } else {
                                stringResource(R.string.dua_header_subtitle_fmt, duas.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedTopic != null) {
                                selectedTopic = null
                            } else {
                                onBack?.invoke()
                            }
                        },
                        modifier = Modifier.testTag("dua_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("dua_search_input"),
                placeholder = { Text(stringResource(R.string.dua_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchQuery("") },
                            modifier = Modifier.testTag("dua_clear_search_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (selectedTopic == null) {
                // Topic List View
                val displayCategories = if (searchQuery.isNotBlank()) {
                    categories.filter { cat ->
                        (duasByCategory[cat]?.any {
                            it.title.contains(searchQuery, true) ||
                            it.translation.contains(searchQuery, true) ||
                            it.arabicText.contains(searchQuery, true)
                        } == true)
                    }
                } else {
                    categories
                }

                if (displayCategories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.dua_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayCategories, key = { it }) { category ->
                            val catDuas = duasByCategory[category] ?: emptyList()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTopic = category }
                                    .testTag("dua_topic_card_${category.lowercase().replace(" ", "_")}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .padding(10.dp)
                                                    .size(24.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = getDuaCategoryLabel(category),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${catDuas.size} Duas & Parts",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Open topic",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Topic Detail View (Opening sub-screen with only duas related to this topic)
                val topicDuas = duasByCategory[selectedTopic] ?: emptyList()

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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
                                text = if (translationState.activeLanguage != null) {
                                    stringResource(R.string.translate_all_in_lang_fmt, LineTranslator.languageLabel(translationState.activeLanguage!!))
                                } else {
                                    stringResource(R.string.translate_line_action)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (translationState.isBatchTranslating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            if (translationState.translatedById.isNotEmpty()) {
                                TextButton(
                                    onClick = viewModel::clearAllDuaTranslations,
                                    modifier = Modifier.height(32.dp).testTag("dua_clear_all_translations_button")
                                ) {
                                    Text(stringResource(R.string.action_clear), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            FilledTonalButton(
                                onClick = { viewModel.translateVisibleDuas("bn") },
                                modifier = Modifier.height(32.dp).testTag("dua_translate_bn_button")
                            ) {
                                Text(stringResource(R.string.translate_all_bangla), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { showBatchLangPicker = true },
                                modifier = Modifier.height(32.dp).testTag("dua_translate_any_button")
                            ) {
                                Text(stringResource(R.string.translate_any_language), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (topicDuas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.dua_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(topicDuas, key = { _, d -> d.id }) { index, dua ->
                            val isDownloaded = dua.id in audioState.downloadedIds
                            val isDownloading = dua.id in audioState.downloadingIds
                            val isPlaying = audioState.activePlayingId == dua.id || audioState.isTtsSpeakingId == dua.id
                            val isTranslating = dua.id in translationState.translatingIds
                            val translated = translationState.translatedById[dua.id]

                            DuaCard(
                                displayNumber = index + 1,
                                dua = dua,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                isPlaying = isPlaying,
                                isTranslating = isTranslating,
                                translated = translated,
                                onPlayOrDownload = { viewModel.togglePlayDuaAudio(dua) },
                                onDeleteAudio = { viewModel.deleteDuaAudio(dua.id) },
                                onBookmarkToggle = { viewModel.toggleBookmark(dua.id, dua.isBookmarked) },
                                onTranslateDua = { translateTargetDua = dua },
                                onShareCard = {
                                    val effectiveTitle = translated?.second?.title ?: dua.title
                                    val effectiveTr = translated?.second?.translation ?: dua.translation
                                    ShareCardGenerator.shareDuaAsImageCard(
                                        context = context,
                                        title = effectiveTitle,
                                        arabicText = dua.arabicText,
                                        translationText = effectiveTr,
                                        reference = dua.reference
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuaCard(
    displayNumber: Int,
    dua: DuaEntity,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPlaying: Boolean,
    isTranslating: Boolean,
    translated: Pair<String, TranslatedDuaFields>?,
    onPlayOrDownload: () -> Unit,
    onDeleteAudio: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onTranslateDua: () -> Unit,
    onShareCard: () -> Unit
) {
    val hasDownloadableRecitation = dua.audioUrl.isNotBlank()
    val effectiveTitle = translated?.second?.title ?: dua.title
    val effectiveTranslation = translated?.second?.translation ?: dua.translation
    val effectiveVirtue = translated?.second?.virtueOrContext ?: dua.virtueOrContext
    val targetLang = translated?.first ?: "en"
    val gradeLabel = resolveDuaGradeLabel(dua.reference)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dua_card_${dua.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "#$displayNumber",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = gradeLabel,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTranslateDua,
                        modifier = Modifier.testTag("dua_translate_${dua.id}")
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = stringResource(R.string.translate_line_action),
                                tint = if (translated != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onShareCard,
                        modifier = Modifier.testTag("dua_share_${dua.id}")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.testTag("dua_bookmark_${dua.id}")
                    ) {
                        Icon(
                            imageVector = if (dua.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.action_bookmark),
                            tint = if (dua.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (translated != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = stringResource(R.string.translate_all_in_lang_fmt, LineTranslator.languageLabel(targetLang)),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = effectiveTitle,
                style = if (translated != null) banglaSafeBodyStyle(18f, targetLang) else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = dua.arabicText,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    lineHeight = 44.sp,
                    textAlign = TextAlign.End
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Serif
            )

            if (dua.transliteration.isNotBlank()) {
                Text(
                    text = dua.transliteration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic
                )
            }

            Text(
                text = "\"${effectiveTranslation}\"",
                style = if (translated != null) banglaSafeBodyStyle(15f, targetLang) else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (effectiveVirtue.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.dua_virtue_fmt, effectiveVirtue),
                        modifier = Modifier.padding(10.dp),
                        style = if (translated != null) banglaSafeBodyStyle(13f, targetLang) else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPlayOrDownload,
                        enabled = !isDownloading,
                        modifier = Modifier.testTag("dua_audio_action_${dua.id}")
                    ) {
                        when {
                            isDownloading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.dua_downloading))
                            }
                            isPlaying -> {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.dua_stop))
                            }
                            hasDownloadableRecitation && isDownloaded -> {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.dua_play_offline))
                            }
                            hasDownloadableRecitation -> {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.dua_download_audio))
                            }
                            else -> {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.dua_listen_arabic))
                            }
                        }
                    }

                    if (hasDownloadableRecitation && isDownloaded && !isDownloading) {
                        IconButton(
                            onClick = onDeleteAudio,
                            modifier = Modifier.testTag("dua_audio_delete_${dua.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete downloaded audio",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.dua_ref_fmt, dua.reference),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
        }
    }
}
