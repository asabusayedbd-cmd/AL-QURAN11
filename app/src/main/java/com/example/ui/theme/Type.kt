package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.localization.QuranScriptType

val AmiriQuranFont = FontFamily(
    Font(R.font.amiri, FontWeight.Normal),
    Font(R.font.amiri, FontWeight.SemiBold),
    Font(R.font.amiri, FontWeight.Bold)
)

val IndoPakArabicFont = FontFamily(
    Font(R.font.scheherazade_new, FontWeight.Normal),
    Font(R.font.scheherazade_new, FontWeight.Bold)
)

val NotoArabicFont = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic, FontWeight.Medium)
)

val BanglaBodyFont = FontFamily(
    Font(R.font.noto_sans_bengali, FontWeight.Normal),
    Font(R.font.noto_sans_bengali, FontWeight.Medium),
    Font(R.font.noto_sans_bengali, FontWeight.SemiBold)
)

val BanglaDisplayFont = FontFamily(
    Font(R.font.hind_siliguri, FontWeight.Normal),
    Font(R.font.hind_siliguri, FontWeight.SemiBold),
    Font(R.font.hind_siliguri, FontWeight.Bold)
)

val LatinUiFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

fun arabicScriptFont(scriptType: QuranScriptType): FontFamily = when (scriptType) {
    QuranScriptType.UTHMANI -> AmiriQuranFont
    QuranScriptType.INDOPAK -> IndoPakArabicFont
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
