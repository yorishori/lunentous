package com.lunentous.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lunentous.app.R
import com.lunentous.app.data.settings.ThemeVariant

/**
 * Direct port of web/src/index.css's design tokens. Two palettes -- Catppuccin
 * Mocha (dark) and Latte (light) -- either following the system light/dark
 * setting or pinned explicitly via Settings' appearance picker (see
 * AppearanceStore/ThemeVariant).
 */
data class LunentousColors(
    val bg: Color,
    val surface: Color,
    val surfaceHover: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentText: Color,
    val accentSoft: Color,
    val overdue: Color,
    val overdueSoft: Color,
    val dueToday: Color,
    val dueTodaySoft: Color,
    val ok: Color,
    val okSoft: Color,
)

private val MochaColors = LunentousColors(
    bg = Color(0xFF1E1E2E),
    surface = Color(0xFF313244),
    surfaceHover = Color(0xFF45475A),
    border = Color(0xFF45475A),
    text = Color(0xFFCDD6F4),
    textMuted = Color(0xFFA6ADC8),
    accent = Color(0xFFCBA6F7),
    accentText = Color(0xFF1E1E2E),
    accentSoft = Color(0x29CBA6F7),
    overdue = Color(0xFFF38BA8),
    overdueSoft = Color(0x29F38BA8),
    dueToday = Color(0xFFFAB387),
    dueTodaySoft = Color(0x29FAB387),
    ok = Color(0xFFA6E3A1),
    okSoft = Color(0x29A6E3A1),
)

private val LatteColors = LunentousColors(
    bg = Color(0xFFE6E9EF),
    surface = Color(0xFFEFF1F5),
    surfaceHover = Color(0xFFDCE0E8),
    border = Color(0xFFCCD0DA),
    text = Color(0xFF4C4F69),
    textMuted = Color(0xFF6C6F85),
    accent = Color(0xFF8839EF),
    accentText = Color(0xFFEFF1F5),
    accentSoft = Color(0x1F8839EF),
    overdue = Color(0xFFD20F39),
    overdueSoft = Color(0x1FD20F39),
    dueToday = Color(0xFFFE640B),
    dueTodaySoft = Color(0x1FFE640B),
    ok = Color(0xFF40A02B),
    okSoft = Color(0x1F40A02B),
)

private val LocalLunentousColors = staticCompositionLocalOf { MochaColors }

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val BaseTypography = Typography()
private val LunentousTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = JetBrainsMono),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = JetBrainsMono),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = JetBrainsMono),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = JetBrainsMono),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = JetBrainsMono),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = JetBrainsMono),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = JetBrainsMono),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = JetBrainsMono),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = JetBrainsMono),
)

// Rounded, bubbly -- matches --radius/--radius-sm/--radius-pill on the web.
private val LunentousShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LunentousTheme(themeVariant: ThemeVariant = ThemeVariant.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeVariant) {
        ThemeVariant.SYSTEM -> isSystemInDarkTheme()
        ThemeVariant.MOCHA -> true
        ThemeVariant.LATTE -> false
    }
    val colors = if (dark) MochaColors else LatteColors

    // Fills out the full M3 role set (not just the handful with a direct
    // web CSS-token equivalent) so components that read secondary/
    // tertiary/container/surfaceContainer roles -- segmented buttons,
    // filled-tonal buttons, the nav rail's selected-item indicator, etc. --
    // stay on-palette instead of falling back to M3's default Material You
    // purple/teal. Containers reuse surfaceHover as a neutral base with the
    // relevant accent hue as their "on" color, rather than inventing new
    // hardcoded tones beyond LunentousColors' existing set.
    //
    // surfaceContainerLow specifically matters: Card()'s default
    // containerColor reads it, and it was previously set equal to
    // `background`, making every Card render pixel-identical to the page
    // background -- invisible in Latte, where bg/surface/surfaceHover are
    // already close in lightness (unlike Mocha, where the dark palette's
    // wider absolute gaps mostly hid the same bug). It must be `surface`,
    // never `bg`, so cards stay visually distinct from the page underneath
    // them in both themes.
    val materialScheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.accentText,
            primaryContainer = colors.surfaceHover, onPrimaryContainer = colors.accent,
            secondary = colors.textMuted, onSecondary = colors.bg,
            secondaryContainer = colors.surfaceHover, onSecondaryContainer = colors.text,
            tertiary = colors.ok, onTertiary = colors.accentText,
            tertiaryContainer = colors.surfaceHover, onTertiaryContainer = colors.ok,
            background = colors.bg, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceHover, onSurfaceVariant = colors.textMuted,
            outline = colors.border, outlineVariant = colors.border,
            error = colors.overdue, onError = colors.accentText,
            errorContainer = colors.surfaceHover, onErrorContainer = colors.overdue,
            surfaceContainerLowest = colors.bg,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceHover,
            surfaceContainerHighest = colors.surfaceHover,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.accentText,
            primaryContainer = colors.surfaceHover, onPrimaryContainer = colors.accent,
            secondary = colors.textMuted, onSecondary = colors.bg,
            secondaryContainer = colors.surfaceHover, onSecondaryContainer = colors.text,
            tertiary = colors.ok, onTertiary = colors.accentText,
            tertiaryContainer = colors.surfaceHover, onTertiaryContainer = colors.ok,
            background = colors.bg, onBackground = colors.text,
            surface = colors.surface, onSurface = colors.text,
            surfaceVariant = colors.surfaceHover, onSurfaceVariant = colors.textMuted,
            outline = colors.border, outlineVariant = colors.border,
            error = colors.overdue, onError = colors.accentText,
            errorContainer = colors.surfaceHover, onErrorContainer = colors.overdue,
            surfaceContainerLowest = colors.bg,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surfaceHover,
            surfaceContainerHighest = colors.surfaceHover,
        )
    }

    CompositionLocalProvider(LocalLunentousColors provides colors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = LunentousTypography,
            shapes = LunentousShapes,
            content = content,
        )
    }
}

/** Semantic colors beyond Material3's own roles (overdue/due-today/ok +
 * soft variants) -- access via `LunentousExtendedTheme.colors.overdue` etc.,
 * mirroring the CSS custom properties on the web. */
object LunentousExtendedTheme {
    val colors: LunentousColors
        @Composable get() = LocalLunentousColors.current
}
