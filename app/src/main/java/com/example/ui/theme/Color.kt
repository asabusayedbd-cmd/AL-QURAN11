package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.core.localization.NoorThemeMode

// Primary Brand Accents (Serene Cream & Emerald + Muted Gold)
val EmeraldPrimary = Color(0xFF0F5C4D)
val EmeraldContainer = Color(0xFFD6EDE6)
val OnEmeraldContainer = Color(0xFF062E25)
val MutedGold = Color(0xFFC89D54)
val MutedGoldContainer = Color(0xFFF6EAD1)
val OnMutedGoldContainer = Color(0xFF3E2C0B)

// Serene Cream & Emerald (Light Default)
val CreamBackground = Color(0xFFFAF7F0)
val CreamSurface = Color(0xFFF4EFE4)
val CreamSurfaceHigh = Color(0xFFECE5D6)
val CharcoalInk = Color(0xFF1E2421)
val MutedSlateInk = Color(0xFF545F59)
val CreamOutline = Color(0xFFD4CBB8)

// Soft Sepia
val SepiaBackground = Color(0xFFF4ECD8)
val SepiaSurface = Color(0xFFECE2CA)
val SepiaSurfaceHigh = Color(0xFFE2D5B8)
val SepiaInk = Color(0xFF322719)
val SepiaAccent = Color(0xFF8C5A2B)
val SepiaGold = Color(0xFFB57C3C)

// Deep Slate Teal (Dark)
val SlateBackground = Color(0xFF0F1A1B)
val SlateSurface = Color(0xFF162426)
val SlateSurfaceHigh = Color(0xFF1F3134)
val SlateEmerald = Color(0xFF5DC4AE)
val SlateGold = Color(0xFFE0B86F)
val SlateInk = Color(0xFFE7EFEA)
val SlateMutedInk = Color(0xFF9FB2AC)

// Pure AMOLED Night
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF0C1211)
val AmoledSurfaceHigh = Color(0xFF151E1D)
val AmoledEmerald = Color(0xFF66D1B9)

data class TajweedPalette(
    val ghunnah: Color,
    val ikhfa: Color,
    val idgham: Color,
    val qalqalah: Color,
    val madd: Color,
    val iqlab: Color,
    val silent: Color
)

val LightTajweedPalette = TajweedPalette(
    ghunnah = Color(0xFF16825D),
    ikhfa = Color(0xFFB85C1C),
    idgham = Color(0xFF1F6E9C),
    qalqalah = Color(0xFF9E3A64),
    madd = Color(0xFF7B4DA8),
    iqlab = Color(0xFF0D7C8C),
    silent = Color(0xFF8A918D)
)

val DarkTajweedPalette = TajweedPalette(
    ghunnah = Color(0xFF6EE0B8),
    ikhfa = Color(0xFFF5A462),
    idgham = Color(0xFF6EC4F0),
    qalqalah = Color(0xFFF087B3),
    madd = Color(0xFFC89DF5),
    iqlab = Color(0xFF5AD4E6),
    silent = Color(0xFF7D8984)
)

fun colorSchemeForTheme(mode: NoorThemeMode): ColorScheme = when (mode) {
    NoorThemeMode.SERENE_EMERALD -> lightColorScheme(
        primary = EmeraldPrimary,
        onPrimary = Color.White,
        primaryContainer = EmeraldContainer,
        onPrimaryContainer = OnEmeraldContainer,
        secondary = MutedGold,
        onSecondary = Color(0xFF261A05),
        secondaryContainer = MutedGoldContainer,
        onSecondaryContainer = OnMutedGoldContainer,
        tertiary = Color(0xFF2A7B6F),
        onTertiary = Color.White,
        background = CreamBackground,
        onBackground = CharcoalInk,
        surface = CreamSurface,
        onSurface = CharcoalInk,
        surfaceVariant = CreamSurfaceHigh,
        onSurfaceVariant = MutedSlateInk,
        outline = CreamOutline,
        outlineVariant = Color(0xFFE4DCCB)
    )

    NoorThemeMode.SOFT_SEPIA -> lightColorScheme(
        primary = SepiaAccent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7D3B5),
        onPrimaryContainer = SepiaInk,
        secondary = SepiaGold,
        onSecondary = Color(0xFF2A1908),
        secondaryContainer = Color(0xFFEFDEC2),
        onSecondaryContainer = SepiaInk,
        tertiary = Color(0xFF4F6F58),
        onTertiary = Color.White,
        background = SepiaBackground,
        onBackground = SepiaInk,
        surface = SepiaSurface,
        onSurface = SepiaInk,
        surfaceVariant = SepiaSurfaceHigh,
        onSurfaceVariant = Color(0xFF665642),
        outline = Color(0xFFCBBBA0),
        outlineVariant = Color(0xFFDDD0B9)
    )

    NoorThemeMode.SLATE_DARK -> darkColorScheme(
        primary = SlateEmerald,
        onPrimary = Color(0xFF04261F),
        primaryContainer = Color(0xFF17453C),
        onPrimaryContainer = Color(0xFFC8F3E8),
        secondary = SlateGold,
        onSecondary = Color(0xFF2B1E06),
        secondaryContainer = Color(0xFF3E3016),
        onSecondaryContainer = Color(0xFFFBE6BC),
        tertiary = Color(0xFF7AC2D4),
        onTertiary = Color(0xFF08252C),
        background = SlateBackground,
        onBackground = SlateInk,
        surface = SlateSurface,
        onSurface = SlateInk,
        surfaceVariant = SlateSurfaceHigh,
        onSurfaceVariant = SlateMutedInk,
        outline = Color(0xFF32484B),
        outlineVariant = Color(0xFF223437)
    )

    NoorThemeMode.AMOLED_BLACK -> darkColorScheme(
        primary = AmoledEmerald,
        onPrimary = Color(0xFF00241C),
        primaryContainer = Color(0xFF10382F),
        onPrimaryContainer = Color(0xFFCBF5EB),
        secondary = SlateGold,
        onSecondary = Color(0xFF281B04),
        secondaryContainer = Color(0xFF332611),
        onSecondaryContainer = Color(0xFFFAE2B4),
        tertiary = Color(0xFF72BECA),
        onTertiary = Color(0xFF062126),
        background = AmoledBackground,
        onBackground = Color(0xFFF0F4F2),
        surface = AmoledSurface,
        onSurface = Color(0xFFF0F4F2),
        surfaceVariant = AmoledSurfaceHigh,
        onSurfaceVariant = Color(0xFF9EAFA9),
        outline = Color(0xFF2A3634),
        outlineVariant = Color(0xFF192220)
    )
}
