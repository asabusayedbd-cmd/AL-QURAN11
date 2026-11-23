package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.core.localization.NoorThemeMode

val LocalTajweedPalette = staticCompositionLocalOf { LightTajweedPalette }

private val NoorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NoorAlQuranTheme(
    themeMode: NoorThemeMode = NoorThemeMode.SERENE_EMERALD,
    uiLanguageCode: String = "en",
    content: @Composable () -> Unit
) {
    val isDark = themeMode == NoorThemeMode.SLATE_DARK || themeMode == NoorThemeMode.AMOLED_BLACK
    val colorScheme = colorSchemeForTheme(themeMode)
    val tajweedPalette = if (isDark) DarkTajweedPalette else LightTajweedPalette
    val typography = noorTypography(uiLanguageCode)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalTajweedPalette provides tajweedPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = NoorShapes,
            content = content
        )
    }
}
