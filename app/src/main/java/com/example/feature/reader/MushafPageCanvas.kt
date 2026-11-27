package com.example.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.database.AyahEntity
import com.example.core.database.SurahEntity
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.BidiUtils
import com.example.core.localization.MushafEditionStyle
import com.example.core.localization.QuranScriptType
import com.example.ui.components.TajweedTextFormatter
import com.example.ui.theme.TajweedPalette
import com.example.ui.theme.arabicLineHeightMultiplier
import com.example.ui.theme.arabicScriptFont

@Composable
fun MushafPageCard(
    pageNumber: Int,
    pageAyahs: List<AyahDisplayBundle>,
    surah: SurahEntity?,
    localizedSurahName: String,
    mushafStyle: MushafEditionStyle,
    scriptType: QuranScriptType,
    arabicFontFamily: ArabicFontFamilyOption,
    arabicFontSizeSp: Float,
    tajweedColorsEnabled: Boolean,
    tajweedPalette: TajweedPalette,
    uiLanguageCode: String,
    playingAyahNumber: Int?,
    selectedAyahId: Int?,
    chromeVisible: Boolean,
    hasPrevPage: Boolean,
    hasNextPage: Boolean,
    onToggleChrome: () -> Unit,
    onAyahClick: (AyahEntity) -> Unit,
    onOpenPageJumper: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.45f
    val paperColor = Color(if (isDark) mushafStyle.darkPaperHex else mushafStyle.lightPaperHex)
    val ornamentColor = Color(mushafStyle.borderHex).let { base ->
        if (isDark) base.copy(alpha = 0.85f) else base
    }
    val inkColor = if (isDark) Color(0xFFF3EFE6) else Color(0xFF1A1C19)
    val secondaryInk = if (isDark) Color(0xFFCBC4B4) else Color(0xFF4F544C)

    val juzNumber = pageAyahs.firstOrNull()?.ayah?.juzNumber ?: surah?.startJuz ?: 1
    val hizbNumber = ((juzNumber - 1) * 2 + 1).coerceIn(1, 60)
    val isFirstPageOfSurah = pageAyahs.any { it.ayah.ayahNumber == 1 } && surah != null
    val isIndoPak = scriptType == QuranScriptType.INDOPAK
    val fontFamily = remember(scriptType, arabicFontFamily) {
        arabicScriptFont(scriptType, arabicFontFamily)
    }
    val effectiveLineMultiplier = remember(scriptType, arabicFontFamily) {
        maxOf(1.92f, arabicLineHeightMultiplier(scriptType, arabicFontFamily))
    }

    val highlightBg = ornamentColor.copy(alpha = if (isDark) 0.28f else 0.18f)
    val selectedBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)

    val pageAnnotatedText = remember(
        pageAyahs,
        scriptType,
        tajweedColorsEnabled,
        tajweedPalette,
        playingAyahNumber,
        selectedAyahId,
        highlightBg,
        selectedBg
    ) {
        buildAnnotatedString {
            pageAyahs.forEachIndexed { index, bundle ->
                val rawArabic = if (isIndoPak) {
                    BidiUtils.sanitizeIndoPakArabic(bundle.ayah.textIndopak.ifBlank { bundle.ayah.textUthmani })
                } else {
                    bundle.ayah.textUthmani
                }
                val formattedVerse = TajweedTextFormatter.formatArabicVerse(
                    rawText = rawArabic,
                    tajweedHtml = bundle.ayah.textTajweedHtml,
                    tajweedEnabled = tajweedColorsEnabled,
                    palette = tajweedPalette,
                    ayahEndMarker = BidiUtils.formatAyahMarker(bundle.ayah.ayahNumber, useArabicDigits = true),
                    useTajweedMarkup = !isIndoPak
                )
                val isPlayingThis = playingAyahNumber == bundle.ayah.ayahNumber
                val isSelectedThis = selectedAyahId == bundle.ayah.id

                val link = LinkAnnotation.Clickable(tag = bundle.ayah.verseKey) {
                    onAyahClick(bundle.ayah)
                }
                withLink(link) {
                    when {
                        isPlayingThis -> withStyle(SpanStyle(background = highlightBg)) {
                            append(formattedVerse)
                        }
                        isSelectedThis -> withStyle(SpanStyle(background = selectedBg)) {
                            append(formattedVerse)
                        }
                        else -> append(formattedVerse)
                    }
                }
                if (index < pageAyahs.lastIndex) {
                    append("  ")
                }
            }
        }
    }

    Surface(
        color = paperColor,
        contentColor = inkColor,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("mushaf_page_card_$pageNumber")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Ornamental Nested Mushaf Page Border & Corner Rosettes
            Canvas(modifier = Modifier.matchParentSize()) {
                val outerPad = 8.dp.toPx()
                val innerPad = 14.dp.toPx()
                val outerCorner = 14.dp.toPx()
                val innerCorner = 10.dp.toPx()

                drawRoundRect(
                    color = ornamentColor.copy(alpha = 0.55f),
                    topLeft = Offset(outerPad, outerPad),
                    size = Size(size.width - outerPad * 2f, size.height - outerPad * 2f),
                    cornerRadius = CornerRadius(outerCorner, outerCorner),
                    style = Stroke(width = 1.6.dp.toPx())
                )
                drawRoundRect(
                    color = ornamentColor.copy(alpha = 0.30f),
                    topLeft = Offset(innerPad, innerPad),
                    size = Size(size.width - innerPad * 2f, size.height - innerPad * 2f),
                    cornerRadius = CornerRadius(innerCorner, innerCorner),
                    style = Stroke(width = 0.9.dp.toPx())
                )

                // Corner rosettes / geometric diamonds
                val corners = listOf(
                    Offset(innerPad, innerPad),
                    Offset(size.width - innerPad, innerPad),
                    Offset(innerPad, size.height - innerPad),
                    Offset(size.width - innerPad, size.height - innerPad)
                )
                val diamondRadius = 5.5.dp.toPx()
                corners.forEach { c ->
                    val path = Path().apply {
                        moveTo(c.x, c.y - diamondRadius)
                        lineTo(c.x + diamondRadius, c.y)
                        lineTo(c.x, c.y + diamondRadius)
                        lineTo(c.x - diamondRadius, c.y)
                        close()
                    }
                    drawPath(path = path, color = ornamentColor.copy(alpha = 0.55f))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mushaf Page Running Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleChrome() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = ornamentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.7.dp, ornamentColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .testTag("mushaf_page_header_badge_$pageNumber")
                            .clickable { onOpenPageJumper() }
                    ) {
                        val pageLabel = stringResource(
                            R.string.reader_page_fmt,
                            BidiUtils.formatNumber(pageNumber, uiLanguageCode)
                        )
                        val juzText = BidiUtils.formatNumber(juzNumber, uiLanguageCode)
                        val hizbText = BidiUtils.formatNumber(hizbNumber, uiLanguageCode)
                        Text(
                            text = "$pageLabel • ${stringResource(R.string.explorer_juz_fmt, juzText)} • Hizb $hizbText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ornamentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = ornamentColor.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = mushafStyle.linesBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = ornamentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        if (surah != null) {
                            Text(
                                text = surah.nameArabic,
                                fontFamily = fontFamily,
                                fontSize = 18.sp,
                                color = ornamentColor
                            )
                        }
                        if (!chromeVisible) {
                            IconButton(
                                onClick = onToggleChrome,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = ornamentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = ornamentColor.copy(alpha = 0.30f)
                )

                // Ornate Surah Header Cartouche + Bismillah on Surah opening page
                if (isFirstPageOfSurah && surah != null) {
                    Surface(
                        color = ornamentColor.copy(alpha = if (isDark) 0.16f else 0.09f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, ornamentColor.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mushaf_surah_cartouche_${surah.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = localizedSurahName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ornamentColor
                                )
                                Text(
                                    text = "سُورَةُ ${surah.nameArabic}",
                                    fontFamily = fontFamily,
                                    fontSize = 22.sp,
                                    color = inkColor
                                )
                                Text(
                                    text = "${BidiUtils.formatNumber(surah.ayahCount, uiLanguageCode)} Ayahs",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryInk
                                )
                            }
                        }
                    }

                    if (surah.bismillahPre && surah.id != 9) {
                        Text(
                            text = "بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ",
                            fontFamily = fontFamily,
                            fontSize = (arabicFontSizeSp * 0.92f).coerceIn(22f, 34f).sp,
                            lineHeight = (arabicFontSizeSp * 1.45f).sp,
                            textAlign = TextAlign.Center,
                            color = ornamentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        )
                    }
                }

                // Continuous Justified RTL Mushaf Body
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = pageAnnotatedText,
                        fontFamily = fontFamily,
                        fontSize = arabicFontSizeSp.sp,
                        lineHeight = (arabicFontSizeSp * effectiveLineMultiplier).sp,
                        color = inkColor,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                HorizontalDivider(
                    thickness = 0.8.dp,
                    color = ornamentColor.copy(alpha = 0.25f)
                )

                // Mushaf Page Bottom Bar with Ornate Page Medallion + Page Turn Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onPrevPage,
                        enabled = hasPrevPage,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("mushaf_prev_page_btn_$pageNumber")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.prev_page),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.prev_page),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Surface(
                        color = ornamentColor.copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, ornamentColor.copy(alpha = 0.50f)),
                        modifier = Modifier
                            .testTag("mushaf_page_medallion_$pageNumber")
                            .clickable { onOpenPageJumper() }
                    ) {
                        Text(
                            text = BidiUtils.formatNumber(pageNumber, "ar"),
                            fontFamily = fontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ornamentColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }

                    TextButton(
                        onClick = onNextPage,
                        enabled = hasNextPage,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("mushaf_next_page_btn_$pageNumber")
                    ) {
                        Text(
                            text = stringResource(R.string.next_page),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.next_page),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.reader_mushaf_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryInk.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MushafPageJumperModal(
    currentPage: Int,
    availablePages: List<Int>,
    uiLanguageCode: String,
    onJumpToPage: (Int) -> Unit,
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
            Text(
                text = stringResource(R.string.jump_to_page),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                availablePages.forEach { pageNum ->
                    FilterChip(
                        selected = pageNum == currentPage,
                        onClick = {
                            onJumpToPage(pageNum)
                            onDismiss()
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    R.string.reader_page_fmt,
                                    BidiUtils.formatNumber(pageNum, uiLanguageCode)
                                )
                            )
                        },
                        modifier = Modifier.testTag("mushaf_jump_page_$pageNum")
                    )
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_close))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
