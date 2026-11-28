package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.database.WordEntity
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.BidiUtils
import com.example.core.localization.QuranScriptType
import com.example.ui.theme.arabicLineHeightMultiplier
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.banglaSafeBodyStyle

@Immutable
data class WordDisplayItem(
    val word: WordEntity,
    val localizedMeaning: String
)

private val STRAY_NUMERAL_REGEX = Regex("^[\\u0660-\\u06690-9\\uFD3E\\uFD3F\\s]+$")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordByWordFlowRow(
    words: List<WordDisplayItem>,
    scriptType: QuranScriptType,
    wbwLanguageCode: String,
    arabicFontSizeSp: Float,
    wbwFontSizeSp: Float,
    showTransliteration: Boolean,
    activeWordPosition: Int?,
    ayahNumber: Int,
    onWordClick: (WordDisplayItem) -> Unit,
    modifier: Modifier = Modifier,
    arabicFontFamily: ArabicFontFamilyOption = ArabicFontFamilyOption.AUTO
) {
    val visibleWords = remember(words) {
        words.filterNot { STRAY_NUMERAL_REGEX.matches(it.word.textUthmani) }
    }
    val meaningDirection = if (wbwLanguageCode in setOf("ar", "ur")) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    // Strictly isolate Quranic Word-by-Word flow as Right-to-Left regardless of app chrome language.
    val arabicFont = remember(scriptType, arabicFontFamily) { arabicScriptFont(scriptType, arabicFontFamily) }
    val lineMult = remember(scriptType, arabicFontFamily) {
        maxOf(1.48f, arabicLineHeightMultiplier(scriptType, arabicFontFamily) * 0.92f)
    }
    val arabicSizeSp = arabicFontSizeSp.sp
    val arabicLineHeightSp = (arabicFontSizeSp * lineMult).sp
    val wordMeaningStyle = remember(wbwFontSizeSp, wbwLanguageCode) {
        banglaSafeBodyStyle(wbwFontSizeSp, wbwLanguageCode)
    }
    val colorScheme = MaterialTheme.colorScheme
    val tileShape = MaterialTheme.shapes.small
    val surfaceColor = colorScheme.surface
    val activeContainerColor = colorScheme.primaryContainer
    val primaryColor = colorScheme.primary
    val outlineBorderColor = remember(colorScheme.outlineVariant) { colorScheme.outlineVariant.copy(alpha = 0.7f) }
    val onPrimaryContainerColor = colorScheme.onPrimaryContainer
    val onSurfaceColor = colorScheme.onSurface
    val onSurfaceVariantColor = colorScheme.onSurfaceVariant
    val secondaryColor = colorScheme.secondary
    val labelSmallStyle = MaterialTheme.typography.labelSmall
    val italicTranslitStyle = remember(labelSmallStyle) { labelSmallStyle.copy(fontStyle = FontStyle.Italic) }
    val markerBgColor = remember(colorScheme.secondaryContainer) { colorScheme.secondaryContainer.copy(alpha = 0.7f) }
    val onSecondaryContainerColor = colorScheme.onSecondaryContainer
    val isIndoPak = scriptType == QuranScriptType.INDOPAK
    val ayahMarkerText = remember(ayahNumber) { BidiUtils.formatAyahMarker(ayahNumber, useArabicDigits = true) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            visibleWords.forEach { item ->
                key(item.word.id) {
                    val isActive = activeWordPosition != null && activeWordPosition == item.word.position
                    val containerColor = if (isActive) activeContainerColor else surfaceColor
                    val borderWidth = if (isActive) 1.6.dp else 0.75.dp
                    val borderColor = if (isActive) primaryColor else outlineBorderColor
                    val primaryTextColor = if (isActive) onPrimaryContainerColor else onSurfaceColor
                    val secondaryTextColor = if (isActive) onPrimaryContainerColor else onSurfaceVariantColor

                    val arabicWord = if (isIndoPak) {
                        val rawIndoPak = item.word.textIndopak.ifBlank { item.word.textUthmani }
                        BidiUtils.sanitizeIndoPakArabic(rawIndoPak)
                    } else {
                        item.word.textUthmani
                    }

                    Column(
                        modifier = Modifier
                            .widthIn(min = 56.dp, max = 152.dp)
                            .clip(tileShape)
                            .background(containerColor, tileShape)
                            .border(borderWidth, borderColor, tileShape)
                            .testTag("wbw_word_${item.word.ayahId}_${item.word.position}")
                            .clickable { onWordClick(item) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = arabicWord,
                            fontFamily = arabicFont,
                            fontSize = arabicSizeSp,
                            lineHeight = arabicLineHeightSp,
                            color = primaryTextColor,
                            textAlign = TextAlign.Center
                        )

                        if (showTransliteration && item.word.transliteration.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = item.word.transliteration,
                                    style = labelSmallStyle,
                                    color = secondaryColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        if (item.localizedMeaning.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides meaningDirection) {
                                Text(
                                    text = item.localizedMeaning,
                                    style = wordMeaningStyle,
                                    color = secondaryTextColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (!showTransliteration && item.word.transliteration.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = item.word.transliteration,
                                    style = italicTranslitStyle,
                                    color = onSurfaceVariantColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Ornate Ayah End Marker placed at the left-most end of the RTL flow
            Text(
                text = ayahMarkerText,
                fontFamily = arabicFont,
                fontSize = (arabicFontSizeSp * 0.78f).sp,
                color = onSecondaryContainerColor,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .clip(tileShape)
                    .background(markerBgColor, tileShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
