package io.github.ruiquanqiao.alarmsets.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Exposes the per-set accent palette for the current theme, so components can
 * look one up without caring whether the app is light or dark.
 */
val LocalAccentPalette = staticCompositionLocalOf<Map<String, AccentColors>> {
    LightAccents
}

/** Looks up the accent colours for a [io.github.ruiquanqiao.alarmsets.core.model.SetAccent] name. */
@Composable
fun accentColorsFor(accentName: String): AccentColors {
    val palette = LocalAccentPalette.current
    return palette[accentName] ?: palette.getValue("BLUE")
}

/**
 * The app's Material 3 theme.
 *
 * Uses Material You dynamic colour on Android 12 and above, so the app adopts
 * the user's wallpaper palette the way Google's own apps do. [dynamicColor] can
 * be turned off from settings for people who would rather have a fixed look.
 */
@Composable
fun AlarmSetsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> FallbackDarkColors
        else -> FallbackLightColors
    }

    val accents = if (darkTheme) DarkAccents else LightAccents

    CompositionLocalProvider(LocalAccentPalette provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AlarmSetsTypography,
            shapes = AlarmSetsShapes,
            content = content,
        )
    }
}
