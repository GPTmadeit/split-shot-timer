package com.carlb.split.wear.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

/**
 * The menu. Until this existed the Settings route was unreachable: it was
 * declared in the nav graph but nothing ever navigated to it.
 */
data class MenuEntry(val label: String, val detail: String, val badge: Boolean = false, val onClick: () -> Unit)

@Composable
fun MenuScreen(entries: List<MenuEntry>, version: String) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("Menu") } }

            items(entries.size) { i ->
                val e = entries[i]
                Button(
                    onClick = e.onClick,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                e.detail,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (e.badge) {
                            Spacer(Modifier.width(6.dp))
                            Canvas(Modifier.size(8.dp)) {
                                drawCircle(HiViz, radius = size.minDimension / 2f)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "SPLIT v$version",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            item { Spacer(Modifier.height(10.dp)) }
        }
    }
}

/**
 * The affordance that opens the menu: the current drill name with a three-dot
 * glyph, drawn rather than typeset so it stays crisp and needs no icon
 * dependency. One element instead of a button stacked above a label, which is
 * all a 227 dp round face has room for above the readout.
 */
@Composable
fun DrillChip(drill: String, hasBadge: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val badge by animateFloatAsState(
        if (hasBadge) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "badge",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(14.dp, 8.dp)) {
            val cy = size.height / 2f
            val gap = size.width * 0.3f
            val cx = size.width / 2f
            listOf(cx - gap, cx, cx + gap).forEach { x ->
                drawCircle(steelTint, radius = size.height * 0.16f, center = Offset(x, cy))
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            drill.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (badge > 0.01f) {
            Spacer(Modifier.width(6.dp))
            Canvas(Modifier.size(7.dp)) {
                drawCircle(HiViz.copy(alpha = badge), radius = size.minDimension / 2f * badge)
            }
        }
    }
}

private val steelTint = Steel

/** Unused-colour guard so the palette stays referenced from previews. */
internal val menuPalette = listOf<Color>(HiViz, Steel, SteelDim)
