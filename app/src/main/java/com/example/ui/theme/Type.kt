package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.localization.ArabicFontFamilyOption
import com.example.core.localization.QuranScriptType

val AmiriMushafQuranFont = FontFamily(
    Font(R.font.amiri_quran, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.amiri_quran, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val AmiriQuranFont = FontFamily(
    Font(R.font.amiri, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.amiri, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.amiri, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val IndoPakArabicFont = FontFamily(
    Font(R.font.scheherazade_new, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.scheherazade_new, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val LateefArabicFont = FontFamily(
    Font(R.font.lateef, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.lateef, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val NotoArabicFont = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.noto_naskh_arabic, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async)
)

val HarmattanArabicFont = FontFamily(
    Font(R.font.harmattan, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.harmattan, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val MarkaziArabicFont = FontFamily(
    Font(R.font.markazi_text, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.markazi_text, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val MirzaArabicFont = FontFamily(
    Font(R.font.mirza, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.mirza, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val ReemKufiArabicFont = FontFamily(
    Font(R.font.reem_kufi, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.reem_kufi, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val NotoKufiArabicFont = FontFamily(
    Font(R.font.noto_kufi_arabic, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.noto_kufi_arabic, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val ArefRuqaaArabicFont = FontFamily(
    Font(R.font.aref_ruqaa, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.aref_ruqaa, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val CairoArabicFont = FontFamily(
    Font(R.font.cairo, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.cairo, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val NotoNastaliqUrduFont = FontFamily(
    Font(R.font.noto_nastaliq_urdu, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.noto_nastaliq_urdu, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val GulzarArabicFont = FontFamily(
    Font(R.font.gulzar, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.gulzar, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val AlkalamiArabicFont = FontFamily(
    Font(R.font.alkalami, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.alkalami, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val IbmPlexArabicFont = FontFamily(
    Font(R.font.ibm_plex_sans_arabic, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.ibm_plex_sans_arabic, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val BanglaBodyFont = FontFamily(
    Font(R.font.noto_sans_bengali, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.noto_sans_bengali, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.noto_sans_bengali, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.Async)
)

val BanglaDisplayFont = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.hind_siliguri, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.hind_siliguri, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

val LatinUiFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.Async),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Async)
)

fun arabicScriptFont(
    scriptType: QuranScriptType,
    fontOption: ArabicFontFamilyOption = ArabicFontFamilyOption.AUTO
): FontFamily = when (fontOption) {
    ArabicFontFamilyOption.AUTO -> when (scriptType) {
        QuranScriptType.UTHMANI -> AmiriMushafQuranFont
        QuranScriptType.INDOPAK -> IndoPakArabicFont
    }
    ArabicFontFamilyOption.AMIRI_QURAN -> AmiriMushafQuranFont
    ArabicFontFamilyOption.AMIRI -> AmiriQuranFont
    ArabicFontFamilyOption.SCHEHERAZADE -> IndoPakArabicFont
    ArabicFontFamilyOption.LATEEF -> LateefArabicFont
    ArabicFontFamilyOption.NOTO_NASKH -> NotoArabicFont
    ArabicFontFamilyOption.NOTO_NASTALIQ_URDU -> NotoNastaliqUrduFont
    ArabicFontFamilyOption.GULZAR -> GulzarArabicFont
    ArabicFontFamilyOption.ALKALAMI -> AlkalamiArabicFont
    ArabicFontFamilyOption.IBM_PLEX_ARABIC -> IbmPlexArabicFont
    ArabicFontFamilyOption.HARMATTAN -> HarmattanArabicFont
    ArabicFontFamilyOption.MARKAZI -> MarkaziArabicFont
    ArabicFontFamilyOption.MIRZA -> MirzaArabicFont
    ArabicFontFamilyOption.REEM_KUFI -> ReemKufiArabicFont
    ArabicFontFamilyOption.NOTO_KUFI -> NotoKufiArabicFont
    ArabicFontFamilyOption.AREF_RUQAA -> ArefRuqaaArabicFont
    ArabicFontFamilyOption.CAIRO -> CairoArabicFont
}

fun arabicLineHeightMultiplier(
    scriptType: QuranScriptType,
    fontOption: ArabicFontFamilyOption = ArabicFontFamilyOption.AUTO
): Float = when (fontOption) {
    ArabicFontFamilyOption.AUTO -> if (scriptType == QuranScriptType.INDOPAK) 1.76f else 1.72f
    else -> fontOption.lineHeightMultiplier
}

fun contentFontForLanguage(languageCode: String): FontFamily = when (languageCode.lowercase()) {
    "bn" -> BanglaBodyFont
    "ar", "ur" -> NotoArabicFont
    else -> LatinUiFont
}

@Suppress("DEPRECATION")
fun banglaSafeBodyStyle(sizeSp: Float, languageCode: String): TextStyle {
    val isBangla = languageCode.equals("bn", ignoreCase = true)
    val lineHeightMultiplier = if (isBangla) 1.62f else 1.50f
    return TextStyle(
        fontFamily = contentFontForLanguage(languageCode),
        fontSize = sizeSp.sp,
        lineHeight = (sizeSp * lineHeightMultiplier).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = isBangla),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Proportional,
            trim = LineHeightStyle.Trim.None
        )
    )
}

@Suppress("DEPRECATION")
fun noorTypography(uiLanguageCode: String): Typography {
    val isBn = uiLanguageCode.equals("bn", ignoreCase = true)
    val isRtlLang = uiLanguageCode.equals("ar", ignoreCase = true) || uiLanguageCode.equals("ur", ignoreCase = true)
    val displayFamily = when {
        isBn -> BanglaDisplayFont
        isRtlLang -> NotoArabicFont
        else -> LatinUiFont
    }
    val bodyFamily = when {
        isBn -> BanglaBodyFont
        isRtlLang -> NotoArabicFont
        else -> LatinUiFont
    }
    val platformStyle = PlatformTextStyle(includeFontPadding = isBn)
    val lhMult = if (isBn) 1.55f else 1.4f

    return Typography(
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = (24f * lhMult).sp,
            platformStyle = platformStyle
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = (20f * lhMult).sp,
            platformStyle = platformStyle
        ),
        titleMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = (16f * lhMult).sp,
            platformStyle = platformStyle
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = (16f * lhMult).sp,
            platformStyle = platformStyle
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = (14f * lhMult).sp,
            platformStyle = platformStyle
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = (12f * lhMult).sp,
            platformStyle = platformStyle
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = (14f * lhMult).sp,
            platformStyle = platformStyle
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = (12f * lhMult).sp,
            platformStyle = platformStyle
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = (11f * lhMult).sp,
            platformStyle = platformStyle
        )
    )
}
