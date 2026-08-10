package com.carlb.split.wear.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * The menu. Until this existed the Settings route was unreachable: it was
 * declared in the nav graph but nothing ever navigated to it.
 */
data class MenuEntry(val label: String, val detail: String, val badge: Boolean = false, val onClick: () -> Unit)

@Composable
fun MenuScreen(entries: List<MenuEntry>, version: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("Menu") } }

        items(entries.size) { i ->
            val e = entries[i]
            FilledTonalButton(
                onClick = e.onClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(e.detail, fontSize = 9.sp, color = Steel)
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
                color = SteelDim,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * The affordance that opens it: a three-dot glyph drawn rather than typeset, so
 * it stays crisp at 20 dp and needs no icon dependency.
 */
@Composable
fun MenuButton(hasBadge: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val badge by animateFloatAsState(
        if (hasBadge) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "badge",
    )
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier.size(38.dp, 26.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val gap = size.width * 0.19f
            val cx = size.width / 2f
            listOf(cx - gap, cx, cx + gap).forEach { x ->
                drawCircle(Steel, radius = size.height * 0.075f, center = Offset(x, cy))
            }
            if (badge > 0.01f) {
                drawCircle(
                    HiViz.copy(alpha = badge),
                    radius = size.height * 0.11f * badge,
                    center = Offset(size.width - size.height * 0.16f, size.height * 0.18f),
                )
            }
        }
    }
}

/** Unused-colour guard so the palette stays referenced from previews. */
internal val menuPalette = listOf<Color>(HiViz, Steel, SteelDim)
