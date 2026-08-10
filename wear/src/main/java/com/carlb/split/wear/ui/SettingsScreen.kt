package com.carlb.split.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.carlb.split.core.TimerConfig
import com.carlb.split.wear.timer.TimerUiState

/**
 * Settings, built from the platform's own controls rather than rows of buttons
 * that fake them: Wear's Slider for a continuous value, SwitchButton for a
 * boolean. Both carry the system's touch targets and rotary-crown behaviour,
 * which hand-rolled equivalents do not.
 */
@Composable
fun SettingsScreen(state: TimerUiState, onConfigChange: (TimerConfig) -> Unit) {
    val cfg = state.config
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("Calibrate") } }

            // Live level against the threshold marker. Watch this with the
            // range active before the first string of the day.
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        "Sensitivity  ${cfg.sensitivityDb} dB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    LevelMeter(
                        levelDbfs = state.levelDbfs,
                        clipping = state.clipping,
                        thresholdDb = cfg.sensitivityDb,
                        modifier = Modifier.fillMaxWidth().height(10.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.sourceName} @ ${state.sampleRate} Hz",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // -52..-6 dB in 2 dB steps: 23 positions, which the crown can walk.
            item {
                Slider(
                    value = cfg.sensitivityDb.toFloat(),
                    onValueChange = { onConfigChange(cfg.copy(sensitivityDb = it.toInt())) },
                    valueRange = -52f..-6f,
                    steps = 22,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            item { ListHeader { Text("Detection") } }

            item {
                SwitchButton(
                    checked = cfg.clipGate,
                    onCheckedChange = { onConfigChange(cfg.copy(clipGate = it)) },
                    label = { Text("Clip gate", fontSize = 13.sp) },
                    secondaryLabel = { Text("Rejects the next bay", fontSize = 9.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                SwitchButton(
                    checked = cfg.recoilGate,
                    onCheckedChange = { onConfigChange(cfg.copy(recoilGate = it)) },
                    label = { Text("Recoil gate", fontSize = 13.sp) },
                    secondaryLabel = { Text("Needs a wrist impulse", fontSize = 9.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                WearListButton(
                    title = "Echo blanking",
                    subtitle = "${cfg.blankingMs} ms",
                    onClick = {
                        val next = when (cfg.blankingMs) {
                            40 -> 60
                            60 -> 90
                            90 -> 130
                            else -> 40
                        }
                        onConfigChange(cfg.copy(blankingMs = next))
                    },
                )
            }

            item { ListHeader { Text("Start") } }

            item {
                WearListButton(
                    title = "Start signal",
                    subtitle = cfg.startSignal.replace('_', ' '),
                    onClick = {
                        val i = TimerConfig.SIGNALS.indexOf(cfg.startSignal)
                        onConfigChange(
                            cfg.copy(startSignal = TimerConfig.SIGNALS[(i + 1) % TimerConfig.SIGNALS.size]),
                        )
                    },
                )
            }

            item {
                WearListButton(
                    title = "Delay",
                    subtitle = cfg.delayMode.replace('_', ' '),
                    onClick = {
                        val i = TimerConfig.DELAY_MODES.indexOf(cfg.delayMode)
                        onConfigChange(
                            cfg.copy(delayMode = TimerConfig.DELAY_MODES[(i + 1) % TimerConfig.DELAY_MODES.size]),
                        )
                    },
                )
            }

            item {
                SwitchButton(
                    checked = cfg.parTone,
                    onCheckedChange = { onConfigChange(cfg.copy(parTone = it)) },
                    label = { Text("Par tone", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                WearListButton(
                    title = "Auto-repeat",
                    subtitle = if (cfg.autoRepeatSec == 0) "off" else "${cfg.autoRepeatSec} s",
                    onClick = {
                        val next = when (cfg.autoRepeatSec) {
                            0 -> 5
                            5 -> 8
                            8 -> 12
                            else -> 0
                        }
                        onConfigChange(cfg.copy(autoRepeatSec = next))
                    },
                )
            }

            item {
                Text(
                    if (state.phoneConnected) "Phone linked" else "Phone not in range",
                    fontSize = 9.sp,
                    color = if (state.phoneConnected) Good else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
