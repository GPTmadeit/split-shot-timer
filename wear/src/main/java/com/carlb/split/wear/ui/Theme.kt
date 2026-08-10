package com.carlb.split.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Wear OS 6 theming.
 *
 * The chrome — lists, buttons, cards, the scaffold — follows the system. On
 * Wear OS 6 `dynamicColorScheme` derives a palette from the active watch face,
 * so SPLIT looks like it belongs on whatever face the user has chosen rather
 * than imposing its own colours on their watch.
 *
 * The *instrument* does not follow the system, and that is deliberate. Shot
 * ticks, the running clock and the standby arc keep fixed high-contrast colours
 * below, because a watch face palette could easily produce a low-contrast pair
 * that is unreadable in bright sun with the display dimmed. Chrome is
 * decoration; the instrument is the thing you have to read at a glance while
 * looking at a target.
 */

// Instrument colours. Fixed on purpose — see above.
val HiViz = Color(0xFFFF5A1F)
val HiVizDim = Color(0xFFC33F0F)
val Brass = Color(0xFFC89A2E)
val BrassDim = Color(0xFF8A6A12)
val Steel = Color(0xFF7B8892)
val SteelDim = Color(0xFF3A444D)
val Bone = Color(0xFFE9EDF1)
val Good = Color(0xFF3FD48B)
val Bad = Color(0xFFFF4D4D)

/** Ink for text sitting on the fixed instrument colours. Not from the dynamic
 *  scheme: onPrimary there is derived from the watch face and can land at very
 *  low contrast against this orange. */
val OnInstrument = Color(0xFF14100D)

/**
 * Fallback for devices with no dynamic source. OLED black ground: unlit pixels
 * draw no current, and this screen stays on for a whole range session.
 */
private val FallbackColors = ColorScheme(
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

    background = Color(0xFF000000),
    onBackground = Bone,
    surfaceContainerLow = Color(0xFF0D1216),
    surfaceContainer = Color(0xFF141A20),
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
    val context = LocalContext.current
    // Null on devices that expose no dynamic source; the fallback then applies.
    val scheme = dynamicColorScheme(context) ?: FallbackColors
    MaterialTheme(colorScheme = scheme, content = content)
}
