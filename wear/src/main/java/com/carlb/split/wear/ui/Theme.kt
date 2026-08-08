package com.carlb.split.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Wear Material 3 tokens carrying the range identity: gunmetal ground,
 * fiber-optic front-sight orange as the single accent, brass for the standby
 * and par states. OLED black background is not a style choice on a watch —
 * unlit pixels draw no current, and this screen is on for a whole session.
 */
val HiViz = Color(0xFFFF5A1F)
val HiVizDim = Color(0xFFC33F0F)
val Brass = Color(0xFFC89A2E)
val BrassDim = Color(0xFF8A6A12)
val Steel = Color(0xFF7B8892)
val SteelDim = Color(0xFF3A444D)
val Bone = Color(0xFFE9EDF1)
val Gun = Color(0xFF000000)
val GunRaised = Color(0xFF141A20)
val Good = Color(0xFF3FD48B)
val Bad = Color(0xFFFF4D4D)

private val SplitColors = ColorScheme(
    primary = HiViz,
    primaryDim = HiVizDim,
    primaryContainer = Color(0xFF3A1206),
    onPrimary = Color(0xFF100500),
    onPrimaryContainer = HiViz,

    secondary = Brass,
    secondaryDim = BrassDim,
    secondaryContainer = Color(0xFF2A2109),
    onSecondary = Color(0xFF120E00),
    onSecondaryContainer = Brass,

    tertiary = Steel,
    tertiaryDim = SteelDim,
    tertiaryContainer = Color(0xFF1B2229),
    onTertiary = Color(0xFF06090C),
    onTertiaryContainer = Bone,

    background = Gun,
    onBackground = Bone,
    surfaceContainerLow = Color(0xFF0D1216),
    surfaceContainer = GunRaised,
    surfaceContainerHigh = Color(0xFF1D2831),
    onSurface = Bone,
    onSurfaceVariant = Steel,
    outline = Color(0xFF2A3742),
    outlineVariant = Color(0xFF1A222A),
    error = Bad,
    onError = Color(0xFF160000),
)

@Composable
fun SplitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SplitColors, content = content)
}
