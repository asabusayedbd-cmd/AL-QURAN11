package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val isDark = isDarkTheme(themeMode)
    val targetScheme = remember(themeMode) { colorSchemeForTheme(themeMode) }
    val initialMode = remember { themeMode }
    var animateThemeChanges by remember { mutableStateOf(false) }
    LaunchedEffect(themeMode) {
        if (themeMode != initialMode) {
            animateThemeChanges = true
        }
    }

    val colorScheme = if (!animateThemeChanges) {
        targetScheme
    } else {
        val animSpec = remember { tween<androidx.compose.ui.graphics.Color>(durationMillis = 240) }
        val animPrimary by animateColorAsState(targetScheme.primary, animSpec, label = "theme_primary")
        val animPrimaryContainer by animateColorAsState(targetScheme.primaryContainer, animSpec, label = "theme_primary_cont")
        val animSecondary by animateColorAsState(targetScheme.secondary, animSpec, label = "theme_secondary")
        val animBackground by animateColorAsState(targetScheme.background, animSpec, label = "theme_bg")
        val animSurface by animateColorAsState(targetScheme.surface, animSpec, label = "theme_surface")
        val animSurfaceVariant by animateColorAsState(targetScheme.surfaceVariant, animSpec, label = "theme_surface_var")
        val animOnBg by animateColorAsState(targetScheme.onBackground, animSpec, label = "theme_on_bg")
        val animOnSurface by animateColorAsState(targetScheme.onSurface, animSpec, label = "theme_on_surface")

        remember(
            targetScheme,
            animPrimary,
            animPrimaryContainer,
            animSecondary,
            animBackground,
            animSurface,
            animSurfaceVariant,
            animOnBg,
            animOnSurface
        ) {
            targetScheme.copy(
                primary = animPrimary,
                primaryContainer = animPrimaryContainer,
                secondary = animSecondary,
                background = animBackground,
                surface = animSurface,
                surfaceVariant = animSurfaceVariant,
                onBackground = animOnBg,
                onSurface = animOnSurface
            )
        }
    }

    val tajweedPalette = remember(isDark) { if (isDark) DarkTajweedPalette else LightTajweedPalette }
    val typography = remember(uiLanguageCode) { noorTypography(uiLanguageCode) }

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
