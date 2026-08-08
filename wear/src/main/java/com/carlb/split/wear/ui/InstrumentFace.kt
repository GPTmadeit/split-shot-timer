package com.carlb.split.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The bezel is the instrument.
 *
 * The ring is a time tape: the acoustic envelope draws inward from the band and
 * every detected shot burns a tick outward at its angular position in the
 * string. By the end of a run the ring *is* the string — you read cadence at a
 * glance without parsing digits, which is the whole point when the watch is on
 * your wrist and your eyes are on the target.
 */

data class FaceModel(
    val elapsedSec: Double,
    val scaleSec: Double,
    val shots: List<Double>,
    val envelope: FloatArray,
    val armProgress: Float,   // 1 -> 0 while standing by
    val parSec: Double,
    val isRunning: Boolean,
    val isArmed: Boolean,
    val isComplete: Boolean,
    val levelNorm: Float,     // 0..1 live mic level, idle only
    val clipping: Boolean,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

const val ENVELOPE_BINS = 240

@Composable
fun InstrumentFace(
    model: () -> FaceModel,
    shotCount: Int,
    startPulseKey: Int,
    modifier: Modifier = Modifier,
) {
    // Hero moment: the shockwave when the tone fires. Low damping so it
    // overshoots and settles — a duration-based fade reads as a screen wipe,
    // a spring reads as an impact.
    val shock = remember { Animatable(0f) }
    LaunchedEffect(startPulseKey) {
        if (startPulseKey > 0) {
            shock.snapTo(0f)
            shock.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = Spring.StiffnessLow))
            shock.animateTo(0f, tween(220))
        }
    }

    // Each detected shot kicks the ring. Independent from the shockwave so a
    // fast split does not cancel the previous kick, it stacks on it.
    val kick = remember { Animatable(0f) }
    LaunchedEffect(shotCount) {
        if (shotCount > 0) {
            kick.snapTo(1f)
            kick.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    Canvas(modifier = modifier) {
        val m = model()
        val c = Offset(size.width / 2f, size.height / 2f)
        val band = size.minDimension * 0.055f
        val r = size.minDimension / 2f - band * 1.35f
        val kickPx = kick.value * band * 0.55f

        // --- track ---------------------------------------------------------
        drawCircle(SteelDim.copy(alpha = 0.35f), radius = r, center = c, style = Stroke(band))

        // --- second ticks --------------------------------------------------
        val tickCount = if (m.scaleSec <= 5) 5 else if (m.scaleSec <= 10) 10 else 12
        repeat(tickCount) { i ->
            val a = -PI / 2 + (i.toFloat() / tickCount) * 2 * PI
            val inner = r + band / 2 + band * 0.25f
            val outer = inner + band * 0.35f
            drawLine(
                color = SteelDim,
                start = Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                end = Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                strokeWidth = 1.5f,
            )
        }

        // --- standby countdown, brass, depleting ---------------------------
        if (m.isArmed) {
            drawArc(
                color = Brass,
                startAngle = -90f,
                sweepAngle = 360f * m.armProgress,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(band, cap = StrokeCap.Butt),
            )
        }

        // --- acoustic envelope, inward -------------------------------------
        if (m.isRunning || m.isComplete) {
            val binW = (2 * PI * r / ENVELOPE_BINS).toFloat() * 0.92f
            for (i in 0 until ENVELOPE_BINS) {
                val v = m.envelope[i]
                if (v <= 0.02f) continue
                val a = -PI / 2 + (i.toDouble() / ENVELOPE_BINS) * 2 * PI
                val r0 = r - band / 2
                val r1 = r0 - min(v, 1f) * band * 1.9f
                drawLine(
                    color = Steel.copy(alpha = 0.55f),
                    start = Offset(c.x + cos(a).toFloat() * r0, c.y + sin(a).toFloat() * r0),
                    end = Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                    strokeWidth = binW,
                )
            }
        }

        // --- par arc, outboard ---------------------------------------------
        if (m.parSec > 0 && (m.isRunning || m.isComplete)) {
            val pr = r + band * 0.95f
            val over = m.elapsedSec > m.parSec
            drawArc(
                color = if (over) Bad else Brass,
                startAngle = -90f,
                sweepAngle = (360f * (m.parSec / m.scaleSec).coerceAtMost(1.0)).toFloat(),
                useCenter = false,
                topLeft = Offset(c.x - pr, c.y - pr),
                size = Size(pr * 2, pr * 2),
                style = Stroke(band * 0.18f, cap = StrokeCap.Round),
            )
        }

        // --- shot ticks: the string, burned into the ring -------------------
        m.shots.forEachIndexed { idx, t ->
            val a = -PI / 2 + (t / m.scaleSec).coerceAtMost(1.0) * 2 * PI
            val age = (m.elapsedSec - t).coerceAtLeast(0.0)
            val fresh = ((0.30 - age) / 0.30).coerceIn(0.0, 1.0).toFloat()
            val extra = if (idx == m.shots.lastIndex) kickPx else 0f
            val r0 = r - band / 2 - band * 0.2f
            val r1 = r + band / 2 + band * 0.35f + fresh * band * 0.8f + extra

            // glow first, tick over it
            if (fresh > 0f) {
                drawLine(
                    color = HiViz.copy(alpha = 0.30f * fresh),
                    start = Offset(c.x + cos(a).toFloat() * r0, c.y + sin(a).toFloat() * r0),
                    end = Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                    strokeWidth = band * 0.85f,
                    cap = StrokeCap.Round,
                )
            }
            drawLine(
                color = HiViz,
                start = Offset(c.x + cos(a).toFloat() * r0, c.y + sin(a).toFloat() * r0),
                end = Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                strokeWidth = band * 0.30f,
                cap = StrokeCap.Round,
            )
        }

        // --- sweep head -----------------------------------------------------
        if (m.isRunning) {
            val a = -PI / 2 + (m.elapsedSec / m.scaleSec).coerceAtMost(1.0) * 2 * PI
            val p = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
            drawCircle(HiViz.copy(alpha = 0.25f), radius = band * 0.9f, center = p)
            drawCircle(HiViz, radius = band * 0.32f, center = p)
        }

        // --- idle: live input level so you can see the mic is hot -----------
        if (!m.isRunning && !m.isArmed && !m.isComplete) {
            drawArc(
                color = if (m.clipping) HiViz else SteelDim,
                startAngle = -90f,
                sweepAngle = 360f * m.levelNorm.coerceIn(0.02f, 1f),
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(band, cap = StrokeCap.Round),
            )
        }

        // --- the shockwave --------------------------------------------------
        val s = shock.value
        if (s > 0.001f) {
            val wave = r * (0.35f + s * 0.95f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HiViz.copy(alpha = 0.45f * (1f - s)), Color.Transparent),
                    center = c,
                    radius = wave,
                ),
                radius = wave,
                center = c,
            )
            drawCircle(
                color = HiViz.copy(alpha = (1f - s) * 0.9f),
                radius = wave,
                center = c,
                style = Stroke(band * 0.35f * (1f - s * 0.6f)),
            )
        }
    }
}

/** Unused helper kept for symmetry with the phone renderer. */
internal fun DrawScope.rotateAround(deg: Float, c: Offset, block: DrawScope.() -> Unit) =
    rotate(deg, c) { block() }
