package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.core.database.TafsirSourceEntity
import com.example.core.database.TranslationSourceEntity
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.ArabicFontGroup
import com.example.core.localization.MushafEditionStyle
import com.example.core.localization.NoorThemeMode
import com.example.core.localization.QuranScriptType
import com.example.core.localization.ReaderCardStyle
import com.example.core.localization.UserPreferences
import com.example.feature.settings.READER_WBW_LANGUAGES
import com.example.ui.theme.arabicScriptFont
import com.example.ui.theme.themePreviewSwatches
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSidebarDrawerContent(
    surahTitle: String,
    preferences: UserPreferences,
    translationSources: List<TranslationSourceEntity>,
    busyTranslationSourceId: Int?,
    tafsirSources: List<TafsirSourceEntity>,
    onClose: () -> Unit,
    onToggleShowWordByWord: (Boolean) -> Unit,
    onToggleShowTranslation: (Boolean) -> Unit,
    onToggleShowInlineTafsir: (Boolean) -> Unit,
    onToggleTajweed: (Boolean) -> Unit,
    onToggleTransliteration: (Boolean) -> Unit,
    onArabicSizeChange: (Float) -> Unit,
    onWbwSizeChange: (Float) -> Unit,
    onTranslationSizeChange: (Float) -> Unit,
    onSelectArabicFont: (ArabicFontFamilyOption) -> Unit,
    onSelectScript: (QuranScriptType) -> Unit,
    onSelectCardStyle: (ReaderCardStyle) -> Unit,
    onSelectTheme: (NoorThemeMode) -> Unit,
    onSelectWbwLanguage: (String) -> Unit,
    onToggleTranslationSource: (TranslationSourceEntity, Boolean) -> Unit,
    onOpenAllTranslations: () -> Unit,
    onSelectActiveTafsirSource: (TafsirSourceEntity) -> Unit,
    onOpenAllTafsirs: () -> Unit,
    onOpenTajweedGuide: () -> Unit,
    onSelectMushafStyle: (MushafEditionStyle) -> Unit = {}
) {
    val activeTranslationsCount = remember(translationSources) {
        translationSources.count { it.isEnabled }
    }
    val quickTranslations = remember(translationSources) {
        val enabled = translationSources.filter { it.isEnabled }
        val popularIds = listOf(161, 163, 20, 131, 85, 84)
        val extra = popularIds.mapNotNull { pid ->
            translationSources.firstOrNull { it.id == pid && !it.isEnabled }
        }
        (enabled + extra).distinctBy { it.id }.take(6)
    }
    val activeTafsirSource = remember(tafsirSources, preferences.activeTafsirSourceId) {
        tafsirSources.firstOrNull { it.id == preferences.activeTafsirSourceId } ?: tafsirSources.firstOrNull()
    }
    val quickTafsirs = remember(tafsirSources, preferences.activeTafsirSourceId) {
        val active = tafsirSources.firstOrNull { it.id == preferences.activeTafsirSourceId }
        val curatedIds = listOf(164, 165, 169, 171, 381, 16, 14)
        val curated = curatedIds.mapNotNull { tid -> tafsirSources.firstOrNull { it.id == tid } }
        (listOfNotNull(active) + curated + tafsirSources.take(4)).distinctBy { it.id }.take(6)
    }

    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(min = 290.dp, max = 352.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSizeIfNeeded()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.sidebar_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (surahTitle.isNotBlank()) {
                            Text(
                                text = surahTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("sidebar_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
            }

            // 1. Reading Layers & Visibility (Word-by-Word vs Translation & Bottom-of-Ayah Tafsir)
            SidebarSectionCard(
                icon = Icons.Default.Visibility,
                title = stringResource(R.string.sidebar_visibility_section)
            ) {
                SidebarToggleRow(
                    title = stringResource(R.string.sidebar_show_wbw),
                    subtitle = stringResource(R.string.sidebar_show_wbw_desc),
                    checked = preferences.showWordByWord,
                    onCheckedChange = onToggleShowWordByWord,
                    testTag = "sidebar_show_wbw_switch"
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SidebarToggleRow(
                    title = stringResource(R.string.sidebar_show_translation),
                    subtitle = stringResource(R.string.sidebar_show_translation_desc),
                    checked = preferences.showTranslation,
                    onCheckedChange = onToggleShowTranslation,
                    testTag = "sidebar_show_translation_switch"
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SidebarToggleRow(
                    title = stringResource(R.string.sidebar_show_inline_tafsir),
                    subtitle = stringResource(R.string.sidebar_show_inline_tafsir_desc),
                    checked = preferences.showInlineTafsir,
                    onCheckedChange = onToggleShowInlineTafsir,
                    testTag = "sidebar_show_inline_tafsir_switch"
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SidebarToggleRow(
                    title = stringResource(R.string.tajweed_colors),
                    subtitle = null,
                    checked = preferences.tajweedColorsEnabled,
                    onCheckedChange = onToggleTajweed,
                    testTag = "tajweed_switch"
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                SidebarToggleRow(
                    title = stringResource(R.string.show_transliteration),
                    subtitle = null,
                    checked = preferences.showTransliteration,
                    onCheckedChange = onToggleTransliteration,
                    testTag = "translit_switch"
                )
            }

            // 2. Font Size Controls with +/- Steppers & Sliders
            SidebarSectionCard(
                icon = Icons.Default.FormatSize,
                title = stringResource(R.string.sidebar_typography_section)
            ) {
                FontStepperSlider(
                    label = stringResource(R.string.arabic_font_size),
                    valueSp = preferences.arabicFontSizeSp,
                    valueRange = 20f..46f,
                    onValueChange = onArabicSizeChange,
                    decTestTag = "sidebar_arabic_font_dec",
                    incTestTag = "sidebar_arabic_font_inc"
                )
                FontStepperSlider(
                    label = stringResource(R.string.wbw_font_size),
                    valueSp = preferences.wbwFontSizeSp,
                    valueRange = 10f..22f,
                    onValueChange = onWbwSizeChange,
                    decTestTag = "sidebar_wbw_font_dec",
                    incTestTag = "sidebar_wbw_font_inc"
                )
                FontStepperSlider(
                    label = stringResource(R.string.translation_font_size),
                    valueSp = preferences.translationFontSizeSp,
                    valueRange = 12f..26f,
                    onValueChange = onTranslationSizeChange,
                    decTestTag = "sidebar_translation_font_dec",
                    incTestTag = "sidebar_translation_font_inc"
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                // Script Type & Ayah Card Style
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = preferences.scriptType == QuranScriptType.UTHMANI,
                        onClick = { onSelectScript(QuranScriptType.UTHMANI) },
                        label = { Text(stringResource(R.string.script_uthmani), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("script_chip_uthmani")
                    )
                    FilterChip(
                        selected = preferences.scriptType == QuranScriptType.INDOPAK,
                        onClick = { onSelectScript(QuranScriptType.INDOPAK) },
                        label = { Text(stringResource(R.string.script_indopak), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("script_chip_indopak")
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                // Mushaf Edition Style Selector (Madani 15-Line, Bangladeshi Hafezi, Kolkata IndoPak, Ottoman, Maghribi)
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    text = stringResource(R.string.mushaf_edition_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isBnUi = preferences.uiLanguageCode.equals("bn", ignoreCase = true)
                    MushafEditionStyle.entries.forEach { edition ->
                        val selected = preferences.mushafStyle == edition
                        val label = if (isBnUi) edition.titleBn else edition.titleEn
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMushafStyle(edition) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(edition.borderHex))
                                )
                            },
                            label = { Text("$label • ${edition.linesBadge}") },
                            modifier = Modifier.testTag("sidebar_mushaf_style_${edition.name}")
                        )
                    }
                }
            }

            // 3. Grouped Arabic Fonts Selector (Madani/Uthmani, Bangladeshi/IndoPak, Calligraphic/Modern)
            SidebarSectionCard(
                icon = Icons.Default.FontDownload,
                title = stringResource(R.string.sidebar_fonts_section, ArabicFontFamilyOption.entries.size - 1)
            ) {
                val isBnUi = preferences.uiLanguageCode.equals("bn", ignoreCase = true)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ArabicFontGroup.entries.forEachIndexed { groupIndex, group ->
                        val groupFonts = ArabicFontFamilyOption.entries.filter { it.group == group }
                        if (groupIndex > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        Text(
                            text = if (isBnUi) group.titleBn else group.titleEn,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (pair in groupFonts.chunked(2)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { option ->
                                        val selected = preferences.arabicFontFamily == option
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            },
                                            border = if (selected) {
                                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                            } else {
                                                BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("sidebar_font_${option.name}")
                                                .clickable { onSelectArabicFont(option) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = option.displayName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (selected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                    Text(
                                                        text = option.arabicSample,
                                                        fontFamily = arabicScriptFont(preferences.scriptType, option),
                                                        fontSize = 17.sp,
                                                        lineHeight = (18f * option.lineHeightMultiplier).sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = TextAlign.Start,
                                                        color = if (selected) {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Quick Theme Swatches
            SidebarSectionCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.theme_title)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NoorThemeMode.entries, key = { it.name }) { mode ->
                        val selected = preferences.themeMode == mode
                        val (bgSwatch, primarySwatch, accentSwatch) = themePreviewSwatches(mode)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .testTag("sidebar_theme_${mode.name}")
                                .clickable { onSelectTheme(mode) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                listOf(bgSwatch, primarySwatch, accentSwatch).forEach { swatch ->
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Word-by-Word Language Selector
            SidebarSectionCard(
                icon = Icons.Default.Translate,
                title = stringResource(R.string.sidebar_wbw_lang_section)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    READER_WBW_LANGUAGES.forEach { (code, label) ->
                        val shortLabel = label.substringBefore(" (WBW)")
                        FilterChip(
                            selected = preferences.wbwLanguageCode == code,
                            onClick = { onSelectWbwLanguage(code) },
                            label = { Text(shortLabel) },
                            modifier = Modifier.testTag("sidebar_wbw_lang_$code")
                        )
                    }
                }
            }

            // 6. Quran Translations (Quick Toggles + Full Catalog Picker)
            SidebarSectionCard(
                icon = Icons.Default.Translate,
                title = stringResource(R.string.sidebar_translations_section, activeTranslationsCount)
            ) {
                quickTranslations.forEachIndexed { idx, src ->
                    if (idx > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = src.translatorName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = src.languageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (busyTranslationSourceId == src.id) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Switch(
                                checked = src.isEnabled,
                                onCheckedChange = { enabled -> onToggleTranslationSource(src, enabled) },
                                modifier = Modifier.testTag("sidebar_tr_switch_${src.id}")
                            )
                        }
                    }
                }
                if (translationSources.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenAllTranslations,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sidebar_browse_translations_button")
                    ) {
                        Text(stringResource(R.string.sidebar_browse_translations_fmt, translationSources.size))
                    }
                }
            }

            // 7. Tafsir Source Selector (Quick Chips + Full Catalog Picker)
            SidebarSectionCard(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = stringResource(R.string.sidebar_tafsir_section)
            ) {
                activeTafsirSource?.let { active ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${active.tafsirName} (${active.languageCode.uppercase()})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTafsirs.forEach { src ->
                        FilterChip(
                            selected = src.id == preferences.activeTafsirSourceId,
                            onClick = { onSelectActiveTafsirSource(src) },
                            label = {
                                Text(
                                    text = "${src.tafsirName} (${src.languageCode.uppercase()})",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("sidebar_tafsir_chip_${src.id}")
                        )
                    }
                }
                if (tafsirSources.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onOpenAllTafsirs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sidebar_browse_tafsirs_button")
                    ) {
                        Text(stringResource(R.string.sidebar_browse_tafsirs_fmt, tafsirSources.size))
                    }
                }
            }

            // 8. Tajweed & Makharij Academy Quick Access
            FilledTonalButton(
                onClick = onOpenTajweedGuide,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sidebar_tajweed_guide_button")
            ) {
                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_tajweed_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun Modifier.fillMaxSizeIfNeeded(): Modifier = this.fillMaxWidth()

@Composable
private fun SidebarSectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun SidebarToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun FontStepperSlider(
    label: String,
    valueSp: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    decTestTag: String,
    incTestTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ${valueSp.roundToInt()}sp",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onValueChange((valueSp - 1f).coerceIn(valueRange)) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(decTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease $label"
                    )
                }
                IconButton(
                    onClick = { onValueChange((valueSp + 1f).coerceIn(valueRange)) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(incTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase $label"
                    )
                }
            }
        }
        Slider(
            value = valueSp.coerceIn(valueRange),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
