package io.github.ruiquanqiao.alarmsets.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback palette for devices without Material You dynamic colour (below
 * Android 12) or when the user pins the app to its own colours.
 *
 * Generated from a single source hue following the Material 3 tonal palette
 * roles, so it behaves like a real M3 scheme rather than a hand-picked set.
 */

private val Purple10 = Color(0xFF21005D)
private val Purple20 = Color(0xFF381E72)
private val Purple30 = Color(0xFF4F378B)
private val Purple40 = Color(0xFF6750A4)
private val Purple80 = Color(0xFFD0BCFF)
private val Purple90 = Color(0xFFEADDFF)

private val Teal10 = Color(0xFF002022)
private val Teal20 = Color(0xFF00363A)
private val Teal30 = Color(0xFF004F54)
private val Teal40 = Color(0xFF006A6F)
private val Teal80 = Color(0xFF4FD8DF)
private val Teal90 = Color(0xFF9CF1F6)

private val Amber10 = Color(0xFF2A1800)
private val Amber20 = Color(0xFF452B00)
private val Amber30 = Color(0xFF633F00)
private val Amber40 = Color(0xFF855400)
private val Amber80 = Color(0xFFFFB95C)
private val Amber90 = Color(0xFFFFDDB0)

private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

private val Neutral6 = Color(0xFF141218)
private val Neutral10 = Color(0xFF1D1B20)
private val Neutral12 = Color(0xFF211F26)
private val Neutral17 = Color(0xFF2B2930)
private val Neutral22 = Color(0xFF36343B)
private val Neutral24 = Color(0xFF3B383E)
private val Neutral90 = Color(0xFFE6E0E9)
private val Neutral94 = Color(0xFFF3EDF7)
private val Neutral96 = Color(0xFFF7F2FA)
private val Neutral98 = Color(0xFFFEF7FF)
private val NeutralVariant30 = Color(0xFF49454F)
private val NeutralVariant50 = Color(0xFF79747E)
private val NeutralVariant60 = Color(0xFF938F99)
private val NeutralVariant80 = Color(0xFFCAC4D0)
private val NeutralVariant90 = Color(0xFFE7E0EC)

internal val FallbackLightColors = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral98,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral96,
    surfaceContainer = Neutral94,
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
)

internal val FallbackDarkColors = darkColorScheme(
    primary = Purple80,
    onPrimary = Purple20,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,
    secondary = Teal80,
    onSecondary = Teal20,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Amber20,
    tertiaryContainer = Amber30,
    onTertiaryContainer = Amber90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral6,
    onBackground = Neutral90,
    surface = Neutral6,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral24,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
)

/**
 * Per-set accent colours.
 *
 * These intentionally do not come from the dynamic scheme: a set's colour is
 * the user's label for it, so it must stay recognisable no matter what
 * wallpaper the system palette is currently derived from. Each pair is tuned
 * for contrast against the surface in its own theme.
 */
data class AccentColors(
    val container: Color,
    val onContainer: Color,
    val marker: Color,
)

internal val LightAccents = mapOf(
    "BLUE" to AccentColors(Color(0xFFD7E3FF), Color(0xFF001B3F), Color(0xFF3B5F9E)),
    "GREEN" to AccentColors(Color(0xFFBFF2C8), Color(0xFF00210C), Color(0xFF2E6B42)),
    "AMBER" to AccentColors(Color(0xFFFFDDB0), Color(0xFF2A1800), Color(0xFF8A5100)),
    "ROSE" to AccentColors(Color(0xFFFFD9E1), Color(0xFF3E001D), Color(0xFF9C4256)),
    "VIOLET" to AccentColors(Color(0xFFEADDFF), Color(0xFF21005D), Color(0xFF6750A4)),
    "TEAL" to AccentColors(Color(0xFF9CF1F6), Color(0xFF002022), Color(0xFF006A6F)),
)

internal val DarkAccents = mapOf(
    "BLUE" to AccentColors(Color(0xFF21405F), Color(0xFFD7E3FF), Color(0xFFA7C8FF)),
    "GREEN" to AccentColors(Color(0xFF1B4D2E), Color(0xFFBFF2C8), Color(0xFFA4D6AD)),
    "AMBER" to AccentColors(Color(0xFF5C3C00), Color(0xFFFFDDB0), Color(0xFFFFB95C)),
    "ROSE" to AccentColors(Color(0xFF5E2937), Color(0xFFFFD9E1), Color(0xFFFFB1C2)),
    "VIOLET" to AccentColors(Color(0xFF4F378B), Color(0xFFEADDFF), Color(0xFFD0BCFF)),
    "TEAL" to AccentColors(Color(0xFF004F54), Color(0xFF9CF1F6), Color(0xFF4FD8DF)),
)
