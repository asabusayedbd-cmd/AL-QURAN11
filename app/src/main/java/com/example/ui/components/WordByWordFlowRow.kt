package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val wordMeaningStyle = remember(wbwFontSizeSp, wbwLanguageCode) {
        banglaSafeBodyStyle(wbwFontSizeSp, wbwLanguageCode)
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val activeContainerColor = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val activeBorder = remember(primaryColor) { BorderStroke(1.6.dp, primaryColor) }
    val defaultBorder = remember(outlineColor) { BorderStroke(0.75.dp, outlineColor.copy(alpha = 0.7f)) }

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
                    val border = if (isActive) activeBorder else defaultBorder

                    Surface(
                        color = containerColor,
                        shape = MaterialTheme.shapes.small,
                        border = border,
                        modifier = Modifier
                            .widthIn(min = 56.dp, max = 152.dp)
                            .testTag("wbw_word_${item.word.ayahId}_${item.word.position}")
                            .clickable { onWordClick(item) }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val arabicWord = if (scriptType == QuranScriptType.INDOPAK) {
                                BidiUtils.sanitizeIndoPakArabic(item.word.textIndopak.ifBlank { item.word.textUthmani })
                            } else {
                                item.word.textUthmani
                            }

                            Text(
                                text = arabicWord,
                                fontFamily = arabicFont,
                                fontSize = arabicFontSizeSp.sp,
                                lineHeight = (arabicFontSizeSp * lineMult).sp,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textAlign = TextAlign.Center
                            )

                            if (showTransliteration && item.word.transliteration.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = item.word.transliteration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
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
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (!showTransliteration && item.word.transliteration.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = item.word.transliteration,
                                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Ornate Ayah End Marker placed at the left-most end of the RTL flow
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text(
                    text = BidiUtils.formatAyahMarker(ayahNumber, useArabicDigits = true),
                    fontFamily = arabicFont,
                    fontSize = (arabicFontSizeSp * 0.78f).sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
