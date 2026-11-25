package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.localization.BidiUtils
import com.example.core.localization.LineTranslator
import com.example.core.localization.QuranScriptType
import com.example.core.localization.ReaderCardStyle
import com.example.core.localization.SegmentRoleType
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.UserPreferences
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordMorphologySheet(
    inspected: WordInspectorState,
    scriptType: QuranScriptType,
    wbwLanguageCode: String,
    uiLanguageCode: String,
    onPlayWordAudio: (speed: Float) -> Unit,
    onNavigateToOccurrence: (surahId: Int, ayahNumber: Int) -> Unit,
    onTranslateWord: (targetLang: String) -> Unit,
    onDismiss: () -> Unit,
    arabicFontFamily: ArabicFontFamilyOption = ArabicFontFamilyOption.AUTO
) {
    val analysis = inspected.analysis
    var selectedTab by remember { mutableStateOf(0) }
    var showLangPicker by remember { mutableStateOf(false) }

    if (showLangPicker) {
        com.example.ui.components.LanguageTranslationPickerSheet(
            selectedLanguageCode = inspected.translatedTargetLang,
            onSelectLanguage = { opt ->
                onTranslateWord(opt.code)
            },
            onDismiss = { showLangPicker = false }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Location Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.word_inspector_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${inspected.surahName} • ${BidiUtils.wrapVerseKey(inspected.ayahVerseKey)} • ${stringResource(R.string.word_header_pos_fmt, inspected.word.position)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = analysis.localizedPos(uiLanguageCode).substringBefore(" ("),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Hero Arabic Script + Script Comparison + Audio Card
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val primaryWordArabic = if (scriptType == QuranScriptType.INDOPAK) {
                        inspected.word.textIndopak.ifBlank { inspected.word.textUthmani }
                    } else {
                        inspected.word.textUthmani
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = primaryWordArabic,
                            fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                            fontSize = 44.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (inspected.word.textIndopak.isNotBlank() && inspected.word.textIndopak != inspected.word.textUthmani) {
                        val altScriptLabel = if (scriptType == QuranScriptType.INDOPAK) "Uthmani" else "IndoPak"
                        val altArabic = if (scriptType == QuranScriptType.INDOPAK) inspected.word.textUthmani else inspected.word.textIndopak
                        Text(
                            text = "$altScriptLabel: $altArabic",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                        )
                    }
                    if (inspected.word.transliteration.isNotBlank()) {
                        Text(
                            text = inspected.word.transliteration,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (inspected.localizedMeaning.isNotBlank()) {
                        Text(
                            text = inspected.localizedMeaning,
                            style = banglaSafeBodyStyle(20f, wbwLanguageCode),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayWordAudio(1.0f) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("play_single_word_audio_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.play_word_audio), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = { onPlayWordAudio(0.7f) },
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("play_single_word_slow_button")
                        ) {
                            Icon(Icons.Default.SlowMotionVideo, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.word_slow_audio), maxLines = 1)
                        }
                    }
                }
            }

            // Root vs Derived Form Banner & Grammar Guide Tip
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("word_root_derived_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.word_root_derived_banner),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val activeRoot = analysis.rootLetters.ifBlank { analysis.spacedRoot }
                        if (activeRoot.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = stringResource(R.string.word_root_fmt, activeRoot),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    val activeRoot = analysis.rootLetters.ifBlank { analysis.spacedRoot }
                    val activeDerived = analysis.derivedFormArabic.ifBlank { analysis.stemArabic }
                    val activeDerivedLabel = if (uiLanguageCode.equals("bn", true)) analysis.derivedFormBn.ifBlank { activeDerived } else activeDerived

                    if (activeRoot.isNotBlank()) {
                        Text(
                            text = "${stringResource(R.string.word_root_fmt, activeRoot)} • ${stringResource(R.string.word_derived_fmt, activeDerivedLabel)}",
                            style = banglaSafeBodyStyle(14.5f, uiLanguageCode),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.word_derived_fmt, activeDerivedLabel),
                            style = banglaSafeBodyStyle(14.5f, uiLanguageCode),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = stringResource(R.string.word_grammar_guide_tip),
                        style = banglaSafeBodyStyle(12.5f, uiLanguageCode),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4 Specialized Inspector Tabs (Meaning, Morphology, Grammar, Occurrences)
            val tabs = listOf(
                R.string.word_tab_meaning,
                R.string.word_tab_sarf,
                R.string.word_tab_nahw,
                R.string.word_tab_occurrences
            )
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, titleRes ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Tab 0: Meaning (অর্থ)
            if (selectedTab == 0) {
                // Classical Theological Lexicon Definition Card
                if (analysis.classicalDefinitionAr.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.word_classical_lexicon_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = analysis.classicalDefinitionAr,
                                    fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            val localizedDef = if (uiLanguageCode.equals("bn", true)) analysis.classicalDefinitionBn else analysis.classicalDefinitionEn
                            if (localizedDef.isNotBlank()) {
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                Text(
                                    text = localizedDef,
                                    style = banglaSafeBodyStyle(14f, uiLanguageCode),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // Word Explanation & Context Card (with Instant বাংলা + Any Language translation)
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.word_explanation_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onTranslateWord("bn") },
                                modifier = Modifier.testTag("translate_word_bn_button")
                            ) {
                                Text(stringResource(R.string.translate_all_bangla))
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showLangPicker = true },
                                modifier = Modifier.testTag("translate_word_picker_button")
                            ) {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.translate_any_language))
                            }
                        }

                        if (inspected.isTranslatingWord) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.translate_translating), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (inspected.wordTranslationError != null) {
                            Text(
                                text = inspected.wordTranslationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Display active translated meaning & explanation if user triggered translation
                        if (inspected.translatedTargetLang != null && (!inspected.translatedMeaning.isNullOrBlank() || !inspected.translatedExplanation.isNullOrBlank())) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val langLabel = LineTranslator.languageLabel(inspected.translatedTargetLang)
                                    Text(
                                        text = stringResource(R.string.translate_all_in_lang_fmt, langLabel),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!inspected.translatedMeaning.isNullOrBlank()) {
                                        Text(
                                            text = inspected.translatedMeaning,
                                            style = banglaSafeBodyStyle(18f, inspected.translatedTargetLang),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (!inspected.translatedExplanation.isNullOrBlank()) {
                                        Text(
                                            text = inspected.translatedExplanation,
                                            style = banglaSafeBodyStyle(15f, inspected.translatedTargetLang),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // Default localized explanation (Bangla by default when Bengali UI, English or Arabic otherwise)
                            Text(
                                text = analysis.localizedExplanation(uiLanguageCode),
                                style = banglaSafeBodyStyle(15f, uiLanguageCode),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // Meanings Across Languages
                if (analysis.allMeanings.isNotEmpty()) {
                    OutlinedCard(
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.word_meanings_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                            analysis.allMeanings.forEach { (langLabel, meaningText) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = langLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(0.42f)
                                    )
                                    Text(
                                        text = meaningText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.58f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab 1: Morphology / Sarf (শব্দতত্ত্ব)
            if (selectedTab == 1) {
                // Classical Sarf & Derivation Analysis Card
                if (analysis.classicalSarfAr.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.word_classical_sarf_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = analysis.classicalSarfAr,
                                    fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                    fontSize = 17.sp,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            val localizedSarf = if (uiLanguageCode.equals("bn", true)) analysis.classicalSarfBn else analysis.classicalSarfEn
                            if (localizedSarf.isNotBlank()) {
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                                Text(
                                    text = localizedSarf,
                                    style = banglaSafeBodyStyle(14f, uiLanguageCode),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // Morphological Pattern & Conjugation (الوزن والباب)
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.word_bab_wazn_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        MorphologyRow(
                            label = stringResource(R.string.word_sarf_pattern_label),
                            value = analysis.localizedSarfPattern(uiLanguageCode)
                        )
                        MorphologyRow(
                            label = "কাণ্ড রূপ (Stem)",
                            value = analysis.stemArabic
                        )
                        val activeRoot = analysis.rootLetters.ifBlank { analysis.spacedRoot }
                        if (activeRoot.isNotBlank()) {
                            MorphologyRow(
                                label = "${stringResource(R.string.word_root_label)} (جذر)",
                                value = activeRoot
                            )
                        }
                        if (inspected.word.lemmaArabic.isNotBlank()) {
                            MorphologyRow(
                                label = "${stringResource(R.string.word_lemma_label)} (قاعدة)",
                                value = inspected.word.lemmaArabic
                            )
                        }
                        val sarfNote = if (uiLanguageCode.equals("bn", true)) analysis.sarfNoteBn else analysis.sarfNoteEn
                        if (sarfNote.isNotBlank()) {
                            Text(
                                text = "${stringResource(R.string.word_sarf_prefix)}: $sarfNote",
                                style = banglaSafeBodyStyle(13.5f, uiLanguageCode),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Morphological Segmentation Strip (Prefixes • Stem • Suffixes)
                if (analysis.segments.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.word_segments_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                analysis.segments.forEach { seg ->
                                    val bgColor = when (seg.roleType) {
                                        SegmentRoleType.PREFIX -> MaterialTheme.colorScheme.tertiaryContainer
                                        SegmentRoleType.STEM -> MaterialTheme.colorScheme.primaryContainer
                                        SegmentRoleType.SUFFIX -> MaterialTheme.colorScheme.secondaryContainer
                                    }
                                    val fgColor = when (seg.roleType) {
                                        SegmentRoleType.PREFIX -> MaterialTheme.colorScheme.onTertiaryContainer
                                        SegmentRoleType.STEM -> MaterialTheme.colorScheme.onPrimaryContainer
                                        SegmentRoleType.SUFFIX -> MaterialTheme.colorScheme.onSecondaryContainer
                                    }
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = bgColor,
                                        border = BorderStroke(0.75.dp, fgColor.copy(alpha = 0.25f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = seg.arabicPrefixOrPart,
                                                fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                                fontSize = 22.sp,
                                                color = fgColor
                                            )
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = if (uiLanguageCode.equals("bn", true)) seg.banglaRoleLabel else seg.roleLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = fgColor
                                                )
                                                Text(
                                                    text = "${seg.arabicRoleLabel} • ${if (uiLanguageCode.equals("bn", true)) seg.banglaRoleLabel else seg.roleLabel}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = fgColor.copy(alpha = 0.85f)
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

            // Tab 2: Grammar / Nahw (ব্যাকরণ ও ই‘রাব)
            if (selectedTab == 2) {
                // Classical I'rab Analysis Card
                if (analysis.classicalIrabAr.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.word_classical_irab_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = analysis.classicalIrabAr,
                                    fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                    fontSize = 17.sp,
                                    lineHeight = 26.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            val localizedIrab = if (uiLanguageCode.equals("bn", true)) analysis.classicalIrabBn else analysis.classicalIrabEn
                            if (localizedIrab.isNotBlank()) {
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                                Text(
                                    text = localizedIrab,
                                    style = banglaSafeBodyStyle(14f, uiLanguageCode),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // Full Linguistic & Syntax Table
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.word_grammar_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        MorphologyRow(
                            label = stringResource(R.string.word_pos_label),
                            value = analysis.localizedPos(uiLanguageCode)
                        )
                        MorphologyRow(
                            label = stringResource(R.string.word_case_label),
                            value = analysis.localizedCaseMood(uiLanguageCode)
                        )
                        MorphologyRow(
                            label = stringResource(R.string.word_definiteness_label),
                            value = analysis.localizedDefiniteness(uiLanguageCode)
                        )
                        MorphologyRow(
                            label = stringResource(R.string.word_number_gender_label),
                            value = analysis.localizedNumberGender(uiLanguageCode)
                        )
                        val nahwNote = analysis.localizedNahwFunction(uiLanguageCode)
                        if (nahwNote.isNotBlank()) {
                            MorphologyRow(
                                label = "বাক্যগত অবস্থান",
                                value = nahwNote
                            )
                        }
                        if (inspected.word.grammarTagJson.isNotBlank()) {
                            MorphologyRow(
                                label = stringResource(R.string.word_grammar_label),
                                value = inspected.word.grammarTagJson
                            )
                        }
                    }
                }
            }

            // Tab 3: Occurrences / Concordance (পুনরাবৃত্তি)
            if (selectedTab == 3) {
                // Occurrence Counts Summary
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "কুরআনে শব্দের সংখ্যা ও পুনরাবৃত্তি",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                        MorphologyRow(
                            label = stringResource(R.string.word_exact_occurrences_label),
                            value = stringResource(R.string.word_times_fmt, inspected.exactOccurrencesCount)
                        )
                        MorphologyRow(
                            label = stringResource(R.string.word_occurrences_label),
                            value = stringResource(R.string.word_times_fmt, inspected.rootOccurrencesCount)
                        )
                    }
                }

                // Other Forms in the Quran (Sarf Morphological Family)
                if (inspected.otherForms.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.word_other_forms_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            inspected.otherForms.forEach { form ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .testTag("word_form_chip_${form.sampleVerseKey}")
                                        .clickable {
                                            onNavigateToOccurrence(form.sampleSurahId, form.sampleAyahNumber)
                                            onDismiss()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = form.textUthmani,
                                            fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "×${form.occurrenceCount}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Clickable Root Occurrences in Loaded Quran
                if (inspected.rootOccurrences.isNotEmpty()) {
                    val concordanceTitle = if (inspected.word.rootArabic.isNotBlank()) {
                        stringResource(R.string.word_concordance_root_fmt, inspected.word.rootArabic)
                    } else {
                        stringResource(R.string.word_concordance_word_fmt, inspected.word.textUthmani)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = concordanceTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        inspected.rootOccurrences.take(8).forEach { occ ->
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("root_occurrence_${occ.verseKey}")
                                    .clickable {
                                        onNavigateToOccurrence(occ.surahId, occ.ayahNumber)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = BidiUtils.wrapVerseKey(occ.verseKey),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = occ.wordArabic,
                                                fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                            Text(
                                                text = occ.ayahArabic,
                                                fontFamily = arabicScriptFont(scriptType, arabicFontFamily),
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Open verse ${occ.verseKey}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MorphologyRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderDisplaySettingsSheet(
    preferences: UserPreferences,
    onSelectScript: (QuranScriptType) -> Unit,
    onSelectCardStyle: (ReaderCardStyle) -> Unit = {},
    onToggleTajweed: (Boolean) -> Unit,
    onToggleTransliteration: (Boolean) -> Unit,
    onArabicSizeChange: (Float) -> Unit,
    onWbwSizeChange: (Float) -> Unit,
    onTranslationSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.display_settings), style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preferences.scriptType == QuranScriptType.UTHMANI,
                    onClick = { onSelectScript(QuranScriptType.UTHMANI) },
                    label = { Text(stringResource(R.string.script_uthmani)) },
                    modifier = Modifier.testTag("script_chip_uthmani")
                )
                FilterChip(
                    selected = preferences.scriptType == QuranScriptType.INDOPAK,
                    onClick = { onSelectScript(QuranScriptType.INDOPAK) },
                    label = { Text(stringResource(R.string.script_indopak)) },
                    modifier = Modifier.testTag("script_chip_indopak")
                )
            }

            Text(stringResource(R.string.card_style_title), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    ReaderCardStyle.CLASSIC_ELEVATED to stringResource(R.string.card_style_classic),
                    ReaderCardStyle.CRISP_OUTLINED to stringResource(R.string.card_style_outlined),
                    ReaderCardStyle.MINIMAL_PAPER to stringResource(R.string.card_style_minimal)
                ).forEach { (style, label) ->
                    FilterChip(
                        selected = preferences.readerCardStyle == style,
                        onClick = { onSelectCardStyle(style) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("reader_card_style_${style.name}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.tajweed_colors), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = preferences.tajweedColorsEnabled,
                    onCheckedChange = onToggleTajweed,
                    modifier = Modifier.testTag("tajweed_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.show_transliteration), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = preferences.showTransliteration,
                    onCheckedChange = onToggleTransliteration,
                    modifier = Modifier.testTag("translit_switch")
                )
            }

            HorizontalDivider()

            Text("${stringResource(R.string.arabic_font_size)}: ${preferences.arabicFontSizeSp.toInt()}sp")
            Slider(
                value = preferences.arabicFontSizeSp,
                onValueChange = onArabicSizeChange,
                valueRange = 20f..44f
            )

            Text("${stringResource(R.string.wbw_font_size)}: ${preferences.wbwFontSizeSp.toInt()}sp")
            Slider(
                value = preferences.wbwFontSizeSp,
                onValueChange = onWbwSizeChange,
                valueRange = 10f..20f
            )

            Text("${stringResource(R.string.translation_font_size)}: ${preferences.translationFontSizeSp.toInt()}sp")
            Slider(
                value = preferences.translationFontSizeSp,
                onValueChange = onTranslationSizeChange,
                valueRange = 12f..24f
            )

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
