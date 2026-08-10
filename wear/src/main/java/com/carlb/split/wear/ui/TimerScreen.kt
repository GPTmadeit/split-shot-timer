package com.carlb.split.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.carlb.split.core.Drill
import com.carlb.split.wear.timer.TimerPhase
import com.carlb.split.wear.timer.TimerUiState
import kotlin.math.floor

/**
 * The timer face. Everything here is driven off a single frame loop rather
 * than recomposition-per-tick — the readout updates at display rate while the
 * composition itself stays still.
 */
@Composable
fun TimerFace(state: TimerUiState, elapsedProvider: () -> Double, modifier: Modifier = Modifier) {
    val phase = state.phase
    val isRunning = phase is TimerPhase.Running
    val isArmed = phase is TimerPhase.Armed
    val isComplete = phase is TimerPhase.Complete

    var frameTick by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var armProgress by remember { mutableFloatStateOf(1f) }
    var scaleSec by remember { mutableDoubleStateOf(5.0) }
    val envelope = remember { FloatArray(ENVELOPE_BINS) }
    var startPulse by remember { mutableIntStateOf(0) }

    // Reset the tape at the top of every string.
    LaunchedEffect(phase.javaClass, (phase as? TimerPhase.Armed)?.armedAtNanos) {
        if (isArmed) {
            envelope.fill(0f)
            scaleSec = 5.0
            elapsed = 0.0
        }
    }
    LaunchedEffect(isRunning) { if (isRunning) startPulse++ }

    // Frames are only produced while something actually moves. Previously this
    // ran a full-rate canvas redraw forever, including while the watch sat on
    // READY with a static face.
    LaunchedEffect(phase.javaClass) {
        val animating = isArmed || isRunning
        // Complete still needs a short settle for the last shot's spring.
        val until = if (animating) Long.MAX_VALUE else System.nanoTime() + SETTLE_NANOS
        while (animating || System.nanoTime() < until) {
            withFrameNanos { now ->
                when (val p = phase) {
                    is TimerPhase.Armed -> {
                        val gone = (now - p.armedAtNanos) / 1_000_000.0
                        armProgress = (1.0 - gone / p.delayMillis).coerceIn(0.0, 1.0).toFloat()
                    }

                    is TimerPhase.Running -> {
                        elapsed = elapsedProvider()
                        while (elapsed > scaleSec) {
                            // Fold the envelope in half so the trace keeps
                            // lining up with the ticks after a rescale.
                            for (i in 0 until ENVELOPE_BINS) {
                                val j = i / 2
                                if (envelope[i] > envelope[j]) envelope[j] = envelope[i]
                            }
                            for (i in ENVELOPE_BINS / 2 until ENVELOPE_BINS) envelope[i] = 0f
                            scaleSec *= 2
                        }
                        val bin = ((elapsed / scaleSec) * ENVELOPE_BINS).toInt()
                            .coerceIn(0, ENVELOPE_BINS - 1)
                        val lvl = ((state.levelDbfs + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
                        if (lvl > envelope[bin]) envelope[bin] = lvl
                    }

                    is TimerPhase.Complete -> elapsed = p.string.total ?: 0.0

                    else -> elapsed = 0.0
                }
                frameTick++
            }
        }
    }

    val faceModel = remember(state, frameTick) {
        {
            FaceModel(
                elapsedSec = elapsed,
                scaleSec = scaleSec,
                shots = state.shots,
                envelope = envelope,
                armProgress = armProgress,
                parSec = state.drill.par,
                isRunning = isRunning,
                isArmed = isArmed,
                isComplete = isComplete,
                levelNorm = ((state.levelDbfs + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat(),
                clipping = state.clipping,
            )
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        InstrumentFace(
            model = faceModel,
            shotCount = state.shots.size,
            startPulseKey = startPulse,
            modifier = Modifier.fillMaxSize(),
        )
        Readout(
            state = state,
            elapsed = elapsed,
            frameTick = frameTick,
            isRunning = isRunning,
            isArmed = isArmed,
            isComplete = isComplete,
        )
    }
}

@Composable
private fun Readout(
    state: TimerUiState,
    elapsed: Double,
    frameTick: Int,
    isRunning: Boolean,
    isArmed: Boolean,
    isComplete: Boolean,
) {
    val digitColor by animateColorAsState(
        targetValue = when {
            isRunning -> HiViz
            isComplete -> Bone
            else -> SteelDim
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "digitColor",
    )

    // Each shot gives the readout a small physical kick.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(state.shots.size) {
        if (state.shots.isNotEmpty()) {
            bump.snapTo(1.07f)
            bump.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    frameTick // read to tie this to the frame loop

    val shown = when {
        isArmed -> null
        isRunning || isComplete -> elapsed
        else -> null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = shown?.let { formatClock(it) } ?: "--.--",
            color = digitColor,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.scale(bump.value),
        )

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MicroStat("DRAW", state.shots.firstOrNull()?.let { fmt2(it) } ?: "--")
            MicroStat(
                "SPLIT",
                if (isRunning && state.shots.isNotEmpty()) {
                    fmt2(elapsed - state.shots.last())
                } else {
                    state.shots.zipWithNext { a, b -> b - a }.lastOrNull()?.let { fmt2(it) } ?: "--"
                },
            )
            MicroStat(
                "SHOTS",
                if (state.drill.shots > 0) {
                    "${state.shots.size}/${state.drill.shots}"
                } else {
                    "${state.shots.size}"
                },
            )
        }

        Spacer(Modifier.height(6.dp))

        val label = when {
            isArmed -> "STANDBY"
            isRunning -> "RUNNING"
            isComplete -> if ((state.phase as TimerPhase.Complete).metStandard) "STANDARD MET" else "COMPLETE"
            state.micReady -> "READY"
            else -> "MIC OFF"
        }
        val labelColor by animateColorAsState(
            when {
                isArmed -> Brass
                isRunning -> HiViz
                isComplete && (state.phase as TimerPhase.Complete).metStandard -> Good
                else -> Steel
            },
            label = "labelColor",
        )
        Text(
            text = label,
            color = labelColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )

        AnimatedVisibility(
            visible = state.rejectedByRecoil > 0,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
        ) {
            Text(
                text = "${state.rejectedByRecoil} rejected",
                color = Steel,
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MicroStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = Steel,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/** Live level bar used on the calibration screen. */
@Composable
fun LevelMeter(levelDbfs: Double, clipping: Boolean, thresholdDb: Int, modifier: Modifier = Modifier) {
    val norm by animateFloatAsState(
        ((levelDbfs + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat(),
        spring(stiffness = Spring.StiffnessHigh),
        label = "level",
    )
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = 0.dp),
        ) {
            Canvas2(norm, clipping, ((thresholdDb + 60f) / 60f).coerceIn(0f, 1f))
        }
    }
}

@Composable
private fun Canvas2(norm: Float, clipping: Boolean, mark: Float) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val h = size.height
        drawRoundRect(
            color = SteelDim.copy(alpha = 0.45f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
        )
        if (norm > 0f) {
            drawRoundRect(
                color = if (clipping) HiViz else Steel,
                size = androidx.compose.ui.geometry.Size(size.width * norm, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2),
            )
        }
        drawLine(
            color = Brass,
            start = androidx.compose.ui.geometry.Offset(size.width * mark, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width * mark, h),
            strokeWidth = 2.5f,
        )
    }
}

/** How long to keep drawing after a string ends, so springs can settle. */
private const val SETTLE_NANOS = 700_000_000L

fun formatClock(sec: Double): String {
    val whole = floor(sec).toInt()
    val cs = ((sec - whole) * 100).toInt().coerceIn(0, 99)
    return "$whole.${cs.toString().padStart(2, '0')}"
}

fun fmt2(v: Double): String = String.format("%.2f", v)

fun drillSubtitle(d: Drill): String = buildString {
    append(if (d.shots > 0) "${d.shots} shots" else "open")
    if (d.par > 0) append("  par ${fmt2(d.par)}")
}

/** Unused-color guard so the palette imports stay referenced in previews. */
internal val previewPalette = listOf<Color>(HiViz, Brass, Steel, Bone, Good, Bad)
