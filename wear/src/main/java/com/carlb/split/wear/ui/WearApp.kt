package com.carlb.split.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.carlb.split.core.DrillLibrary
import com.carlb.split.core.TimerConfig
import com.carlb.split.wear.timer.TimerEngine
import com.carlb.split.wear.timer.TimerPhase

private object Routes {
    const val TIMER = "timer"
    const val DRILLS = "drills"
    const val SETTINGS = "settings"
}

@Composable
fun WearApp(engine: TimerEngine, onConfigChange: (TimerConfig) -> Unit) {
    val nav = rememberSwipeDismissableNavController()
    SplitTheme {
        SwipeDismissableNavHost(navController = nav, startDestination = Routes.TIMER) {
            composable(Routes.TIMER) { TimerRoute(engine, nav) }
            composable(Routes.DRILLS) { DrillsRoute(engine, nav) }
            composable(Routes.SETTINGS) { SettingsRoute(engine, onConfigChange) }
        }
    }
}

@Composable
private fun TimerRoute(engine: TimerEngine, nav: NavHostController) {
    val state by engine.state.collectAsStateWithLifecycle()
    val phase = state.phase

    Box(Modifier.fillMaxSize()) {
        TimerFace(state = state, elapsedProvider = engine::elapsedSec)

        // Primary action sits at the bottom of the round face, thumb-reachable.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                is TimerPhase.Armed -> ActionButton("CANCEL", Brass) { engine.reset() }

                is TimerPhase.Running -> ActionButton("STOP", MaterialTheme.colorScheme.surfaceContainerHigh) {
                    engine.stop()
                }

                else -> ActionButton("START", HiViz) { engine.arm() }
            }
        }

        // Drill name doubles as the route into the drill picker.
        if (phase !is TimerPhase.Running && phase !is TimerPhase.Armed) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.drill.name.uppercase(),
                    color = Steel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.clickableNoRipple { nav.navigate(Routes.DRILLS) },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, container: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(0.62f),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DrillsRoute(engine: TimerEngine, nav: NavHostController) {
    val state by engine.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
    ) {
        item { ListHeader { Text("Drill") } }
        items(DrillLibrary.all) { drill ->
            val selected = drill.id == state.drill.id
            FilledTonalButton(
                onClick = {
                    engine.setDrill(drill)
                    nav.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = if (selected) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        drill.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) HiViz else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        drillSubtitle(drill),
                        fontSize = 9.sp,
                        color = Steel,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsRoute(engine: TimerEngine, onConfigChange: (TimerConfig) -> Unit) {
    val state by engine.state.collectAsStateWithLifecycle()
    val cfg = state.config

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
    ) {
        item { ListHeader { Text("Calibrate") } }

        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("Sensitivity  ${cfg.sensitivityDb} dB", fontSize = 11.sp, color = Bone)
                Spacer(Modifier.height(6.dp))
                LevelMeter(
                    levelDbfs = state.levelDbfs,
                    clipping = state.clipping,
                    thresholdDb = cfg.sensitivityDb,
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Source ${state.sourceName} @ ${state.sampleRate} Hz",
                    fontSize = 8.sp,
                    color = Steel,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        item {
            SettingRow("Less sensitive") {
                onConfigChange(cfg.copy(sensitivityDb = (cfg.sensitivityDb + 2).coerceAtMost(-6)))
            }
        }
        item {
            SettingRow("More sensitive") {
                onConfigChange(cfg.copy(sensitivityDb = (cfg.sensitivityDb - 2).coerceAtLeast(-52)))
            }
        }
        item {
            SettingRow(if (cfg.clipGate) "Clip gate: ON" else "Clip gate: OFF") {
                onConfigChange(cfg.copy(clipGate = !cfg.clipGate))
            }
        }
        item {
            SettingRow(if (cfg.recoilGate) "Recoil gate: ON" else "Recoil gate: OFF") {
                onConfigChange(cfg.copy(recoilGate = !cfg.recoilGate))
            }
        }
        item {
            SettingRow("Start: ${cfg.startSignal.replace('_', '+')}") {
                val i = TimerConfig.SIGNALS.indexOf(cfg.startSignal)
                onConfigChange(cfg.copy(startSignal = TimerConfig.SIGNALS[(i + 1) % TimerConfig.SIGNALS.size]))
            }
        }
        item {
            SettingRow("Delay: ${cfg.delayMode.replace('_', ' ')}") {
                val i = TimerConfig.DELAY_MODES.indexOf(cfg.delayMode)
                onConfigChange(cfg.copy(delayMode = TimerConfig.DELAY_MODES[(i + 1) % TimerConfig.DELAY_MODES.size]))
            }
        }
        item {
            SettingRow("Repeat: ${if (cfg.autoRepeatSec == 0) "off" else "${cfg.autoRepeatSec}s"}") {
                val next = when (cfg.autoRepeatSec) {
                    0 -> 5
                    5 -> 8
                    8 -> 12
                    else -> 0
                }
                onConfigChange(cfg.copy(autoRepeatSec = next))
            }
        }
        item {
            Text(
                if (state.phoneConnected) "Phone linked" else "Phone not in range - strings held on watch",
                fontSize = 8.sp,
                color = if (state.phoneConnected) Good else Steel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(label, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
    }
}

/** Tap target without a ripple — the drill label reads as a label, not a button. */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.padding(2.dp).clickable(
    interactionSource = null,
    indication = null,
    onClick = onClick,
)
