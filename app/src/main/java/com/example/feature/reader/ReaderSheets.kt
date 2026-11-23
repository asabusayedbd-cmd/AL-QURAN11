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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.core.localization.QuranScriptType
import com.example.core.localization.SegmentRoleType
import com.example.core.localization.UserPreferences
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordMorphologySheet(
    inspected: WordInspectorState,
    scriptType: QuranScriptType,
    wbwLanguageCode: String,
    onPlayWordAudio: (speed: Float) -> Unit,
    onNavigateToOccurrence: (surahId: Int, ayahNumber: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val analysis = inspected.analysis

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
                        text = "${inspected.surahName} • ${BidiUtils.wrapVerseKey(inspected.ayahVerseKey)} • Word ${inspected.word.position}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = analysis.partOfSpeechEn.substringBefore(" ("),
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
                            fontFamily = arabicScriptFont(scriptType),
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
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
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
                            Text("Slow (0.7x)", maxLines = 1)
                        }
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
                            text = "Prefixes, Stem & Grammar Segments (صرف وبنية)",
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
                                            fontFamily = arabicScriptFont(scriptType),
                                            fontSize = 22.sp,
                                            color = fgColor
                                        )
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = seg.roleLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = fgColor
                                            )
                                            Text(
                                                text = "${seg.arabicRoleLabel} • ${seg.banglaRoleLabel}",
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
                                text = "Word Meanings Across Languages",
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

            // Full Linguistic & Morphology Table
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
                        text = "Grammatical & Root Breakdown (التحليل الصرفي)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                    MorphologyRow(
                        label = "Part of Speech",
                        value = "${analysis.partOfSpeechEn}\n${analysis.partOfSpeechAr} • ${analysis.partOfSpeechBn}"
                    )
                    MorphologyRow(
                        label = "Case / Mood (إعراب)",
                        value = analysis.grammaticalCaseOrMood
                    )
                    MorphologyRow(
                        label = "Number & Gender",
                        value = analysis.numberAndGender
                    )
                    if (inspected.word.rootArabic.isNotBlank()) {
                        MorphologyRow(
                            label = "${stringResource(R.string.word_root_label)} (جذر)",
                            value = analysis.spacedRoot
                        )
                    }
                    if (inspected.word.lemmaArabic.isNotBlank()) {
                        MorphologyRow(
                            label = "${stringResource(R.string.word_lemma_label)} (قاعدة)",
                            value = inspected.word.lemmaArabic
                        )
                    }
                    if (inspected.word.grammarTagJson.isNotBlank()) {
                        MorphologyRow(
                            label = stringResource(R.string.word_grammar_label),
                            value = inspected.word.grammarTagJson
                        )
                    }
                    MorphologyRow(
                        label = stringResource(R.string.word_occurrences_label),
                        value = "${inspected.rootOccurrencesCount} times"
                    )
                }
            }

            // Clickable Root Occurrences in Loaded Quran
            if (inspected.rootOccurrences.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Concordance: Same Root (${inspected.word.rootArabic})",
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
                                            fontFamily = arabicScriptFont(scriptType),
                                            fontSize = 18.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                        Text(
                                            text = occ.ayahArabic,
                                            fontFamily = arabicScriptFont(scriptType),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderDisplaySettingsSheet(
    preferences: UserPreferences,
    onSelectScript: (QuranScriptType) -> Unit,
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
