package com.carlb.split.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.max
import kotlin.math.min

/**
 * Personal-best badge. A circle morphing into a scalloped rosette — the
 * graphics-shapes Morph that Material 3 Expressive's shape system is built on.
 * It only animates when the string actually is a best, so the motion carries
 * information rather than decorating.
 */
@Composable
fun MorphBadge(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val circle = remember { RoundedPolygon.circle(numVertices = 12) }
    val rosette = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.72f,
            rounding = CornerRounding(0.28f),
        )
    }
    val morph = remember { Morph(circle, rosette) }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            progress.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow))
        } else {
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )

    Canvas(modifier) {
        val r = size.minDimension / 2f
        val path = morph.toPath(progress.value).asComposePath2()
        val m = androidx.compose.ui.graphics.Matrix().apply {
            translate(size.width / 2f, size.height / 2f)
            scale(r, r)
            if (active) rotateZ(angle)
        }
        path.transform(m)
        drawPath(path, color.copy(alpha = if (active) 1f else 0.35f))
    }
}

/** graphics-shapes emits an android.graphics.Path; Compose wants its own. */
private fun android.graphics.Path.asComposePath2(): Path = androidx.compose.ui.graphics.Path().also { composePath ->
    composePath.asAndroidPath().set(this)
}

/**
 * Split bars with a staggered spring entry. Draw is brass, fastest green,
 * slowest red — the two splits worth looking at are pre-attention coded so you
 * do not have to read the column.
 */
@Composable
fun SplitBars(draw: Double?, splits: List<Double>, modifier: Modifier = Modifier, surface: Color, accent: Color) {
    val values = buildList {
        draw?.let { add(it) }
        addAll(splits)
    }
    if (values.isEmpty()) return

    val peak = max(values.max(), 0.2)
    val fastest = splits.minOrNull()
    val slowest = splits.maxOrNull()

    val grow = remember(values.size) { Animatable(0f) }
    LaunchedEffect(values.size) {
        grow.snapTo(0f)
        grow.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }

    Canvas(modifier) {
        val n = values.size
        val gap = 5f
        val rowH = (size.height - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val y = i * (rowH + gap)
            // Per-row stagger: each bar starts a beat after the one above it.
            val local = ((grow.value * n) - i).coerceIn(0f, 1f)
            val eased = 1f - (1f - local) * (1f - local)
            val w = size.width * (v / peak).toFloat() * eased

            drawRoundRect(
                color = surface,
                topLeft = Offset(0f, y),
                size = Size(size.width, rowH),
                cornerRadius = CornerRadius(rowH / 2),
            )
            val c = when {
                i == 0 && draw != null -> Brass
                v == fastest && splits.size > 1 -> Good
                v == slowest && splits.size > 1 -> Bad
                else -> accent
            }
            if (w > 1f) {
                drawRoundRect(
                    color = c,
                    topLeft = Offset(0f, y),
                    size = Size(max(w, rowH), rowH),
                    cornerRadius = CornerRadius(rowH / 2),
                )
            }
        }
    }
}

/**
 * Draw-time trend across the session. The path draws itself in on first
 * composition, which is the one place a duration-based tween beats a spring —
 * a stroke revealing at constant rate reads as writing, not as arriving.
 */
@Composable
fun TrendChart(values: List<Double>, modifier: Modifier = Modifier, line: Color, fill: Color, grid: Color) {
    if (values.size < 2) return

    val reveal = remember(values.size) { Animatable(0f) }
    LaunchedEffect(values.size) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(900, easing = LinearEasing))
    }

    Canvas(modifier.fillMaxSize()) {
        val lo = values.min()
        val hi = values.max()
        val span = max(hi - lo, 0.05)
        val n = values.size
        val shown = max(2, (n * reveal.value).toInt())

        repeat(3) { g ->
            val y = size.height * (g + 1) / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        fun pt(i: Int): Offset {
            val x = size.width * i / (n - 1).toFloat()
            val norm = ((values[i] - lo) / span).toFloat()
            return Offset(x, size.height * (1f - norm) * 0.86f + size.height * 0.07f)
        }

        val path = Path().apply {
            moveTo(pt(0).x, pt(0).y)
            for (i in 1 until shown) {
                val p0 = pt(i - 1)
                val p1 = pt(i)
                val midX = (p0.x + p1.x) / 2
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }
        val area = Path().apply {
            addPath(path)
            lineTo(pt(shown - 1).x, size.height)
            lineTo(pt(0).x, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(fill.copy(alpha = 0.32f), Color.Transparent)),
        )
        drawPath(path, line, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // Emphasised endpoint — the value you actually care about.
        val last = pt(shown - 1)
        drawCircle(line.copy(alpha = 0.22f), radius = 9f, center = last)
        drawCircle(line, radius = 3.5f, center = last)
    }
}

/** Live-mirror pulse shown while the watch is running a string. */
@Composable
fun LivePulse(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pulse")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "p",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(color.copy(alpha = (1f - p) * 0.55f), radius = r * (0.45f + p * 0.55f))
            drawCircle(color, radius = r * 0.34f)
        }
    }
}

internal fun DrawScope.unusedGuard() = min(size.width, size.height)
