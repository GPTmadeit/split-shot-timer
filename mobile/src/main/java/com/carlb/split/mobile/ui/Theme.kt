package com.carlb.split.mobile.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HiViz = Color(0xFFFF5A1F)
val HiVizLight = Color(0xFFD8410A)
val Brass = Color(0xFFC89A2E)
val Steel = Color(0xFF7B8892)
val Bone = Color(0xFFE9EDF1)
val Gun = Color(0xFF0A0D10)
val GunRaised = Color(0xFF161E26)
val Good = Color(0xFF3FD48B)
val Bad = Color(0xFFFF4D4D)

private val DarkScheme = darkColorScheme(
    primary = HiViz,
    onPrimary = Color(0xFF160500),
    primaryContainer = Color(0xFF3A1206),
    onPrimaryContainer = HiViz,
    secondary = Brass,
    onSecondary = Color(0xFF120E00),
    secondaryContainer = Color(0xFF2A2109),
    onSecondaryContainer = Brass,
    tertiary = Steel,
    background = Gun,
    onBackground = Bone,
    surface = Gun,
    onSurface = Bone,
    surfaceVariant = GunRaised,
    onSurfaceVariant = Steel,
    surfaceContainer = Color(0xFF141B22),
    surfaceContainerHigh = Color(0xFF1D2831),
    surfaceContainerLow = Color(0xFF0F151A),
    outline = Color(0xFF2A3742),
    outlineVariant = Color(0xFF1A222A),
    error = Bad,
)

private val LightScheme = lightColorScheme(
    primary = HiVizLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCE),
    onPrimaryContainer = Color(0xFF5C1800),
    secondary = Color(0xFF8A6A12),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE9AE),
    onSecondaryContainer = Color(0xFF2A2000),
    tertiary = Color(0xFF5A6772),
    background = Color(0xFFF3F5F7),
    onBackground = Gun,
    surface = Color(0xFFF3F5F7),
    onSurface = Gun,
    surfaceVariant = Color(0xFFE4E9ED),
    onSurfaceVariant = Color(0xFF5A6772),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE2E8ED),
    surfaceContainerLow = Color(0xFFF7F9FA),
    outline = Color(0xFFC2CCD4),
    outlineVariant = Color(0xFFD8E0E6),
    error = Color(0xFFC41F1F),
)

/**
 * Monospace for every figure on the screen. That is not a stylistic tic — a
 * fixed-pitch readout is the shot timer vernacular, and tabular digits are the
 * only way a column of splits stays scannable.
 */
private val SplitTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-2).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.5).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
    )
}

val MonoNum = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SplitTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        // Expressive springs, not duration curves. Everything that moves in this
        // app is reacting to a physical event, so it should settle like one.
        motionScheme = MotionScheme.expressive(),
        typography = SplitTypography,
        content = content,
    )
}
