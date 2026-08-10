package com.carlb.split.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.carlb.split.core.DrillLibrary
import com.carlb.split.core.TimerConfig
import com.carlb.split.core.update.UpdateController
import com.carlb.split.core.update.UpdateStatus
import com.carlb.split.wear.timer.TimerEngine
import com.carlb.split.wear.timer.TimerPhase

private object Routes {
    const val TIMER = "timer"
    const val MENU = "menu"
    const val DRILLS = "drills"
    const val SETTINGS = "settings"
    const val UPDATES = "updates"
}

/**
 * AppScaffold is the outermost Wear OS structure: it owns TimeText — the clock
 * curved along the top bezel — and coordinates screen transitions, so every
 * screen inherits the platform's own chrome rather than reinventing it.
 */
@Composable
fun WearApp(
    engine: TimerEngine,
    onConfigChange: (TimerConfig) -> Unit,
    updates: UpdateController,
    onInstall: () -> Unit,
    appVersion: String,
) {
    val nav = rememberSwipeDismissableNavController()
    SplitTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = nav, startDestination = Routes.TIMER) {
                composable(Routes.TIMER) { TimerRoute(engine, nav, updates) }
                composable(Routes.MENU) { MenuRoute(engine, nav, updates, appVersion) }
                composable(Routes.DRILLS) { DrillsRoute(engine, nav) }
                composable(Routes.SETTINGS) { SettingsRoute(engine, onConfigChange) }
                composable(Routes.UPDATES) { UpdatesRoute(updates, onInstall) }
            }
        }
    }
}

/**
 * The timer face.
 *
 * EdgeButton is the signature Wear OS 6 control: it hugs the bottom curve of a
 * round display, which matches the platform and puts the largest possible
 * target where a thumb naturally lands.
 */
@Composable
private fun TimerRoute(engine: TimerEngine, nav: NavHostController, updates: UpdateController) {
    val state by engine.state.collectAsStateWithLifecycle()
    val up by updates.state.collectAsStateWithLifecycle()
    val phase = state.phase

    // No ScreenScaffold here on purpose: its job is the scroll indicator, and
    // the face does not scroll. AppScaffold still supplies TimeText above, and
    // EdgeButton carries the Wear OS 6 shape on its own.
    Box(Modifier.fillMaxSize()) {
        // The edge button eats the bottom of a round display, so the
        // instrument is inset to clear it. Without this the readout sits
        // behind the button and cannot be read while the clock runs.
        TimerFace(
            state = state,
            elapsedProvider = engine::elapsedSec,
            // Bottom only: clears the edge button. The top chip is compact
            // enough that the readout clears it without shrinking the ring.
            modifier = Modifier.padding(bottom = 40.dp),
        )

        // One compact chip instead of a menu button stacked above a drill
        // label: two elements collided with the readout on a 227 dp round
        // face, and the drill is one tap away inside the menu anyway.
        if (phase !is TimerPhase.Running && phase !is TimerPhase.Armed) {
            DrillChip(
                drill = state.drill.name,
                hasBadge = up.status is UpdateStatus.Available,
                onClick = { nav.navigate(Routes.MENU) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp),
            )
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            when (phase) {
                is TimerPhase.Armed -> EdgeButton(
                    onClick = { engine.reset() },
                    buttonSize = EdgeButtonSize.Medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brass,
                        contentColor = OnInstrument,
                    ),
                ) { EdgeLabel("CANCEL") }

                is TimerPhase.Running -> EdgeButton(
                    onClick = { engine.stop() },
                    buttonSize = EdgeButtonSize.Medium,
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) { EdgeLabel("STOP") }

                else -> EdgeButton(
                    onClick = { engine.arm() },
                    buttonSize = EdgeButtonSize.Medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HiViz,
                        contentColor = OnInstrument,
                    ),
                ) { EdgeLabel("START") }
            }
        }
    }
}

@Composable
private fun EdgeLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
}

@Composable
private fun MenuRoute(engine: TimerEngine, nav: NavHostController, updates: UpdateController, appVersion: String) {
    val state by engine.state.collectAsStateWithLifecycle()
    val up by updates.state.collectAsStateWithLifecycle()

    MenuScreen(
        version = appVersion,
        entries = listOf(
            MenuEntry("Drill", state.drill.name) { nav.navigate(Routes.DRILLS) },
            MenuEntry("Settings", "Sensitivity, gates, start signal") { nav.navigate(Routes.SETTINGS) },
            MenuEntry(
                label = "Updates",
                detail = when (val s = up.status) {
                    is UpdateStatus.Available -> "v${s.update.version} available"
                    else -> "Installed v$appVersion"
                },
                badge = up.status is UpdateStatus.Available,
            ) { nav.navigate(Routes.UPDATES) },
        ),
    )
}

/**
 * TransformingLazyColumn is the Wear OS list: rows scale and fade as they
 * approach the curved edges of the display, which keeps the focused row
 * readable and is instantly recognisable as the platform's own list.
 */
@Composable
private fun DrillsRoute(engine: TimerEngine, nav: NavHostController) {
    val state by engine.state.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("Drill") } }
            items(DrillLibrary.all.size) { i ->
                val drill = DrillLibrary.all[i]
                WearListButton(
                    title = drill.name,
                    subtitle = drillSubtitle(drill),
                    selected = drill.id == state.drill.id,
                    onClick = {
                        engine.setDrill(drill)
                        nav.popBackStack()
                    },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun UpdatesRoute(updates: UpdateController, onInstall: () -> Unit) {
    val s by updates.state.collectAsStateWithLifecycle()
    UpdateScreen(
        state = s,
        onCheck = updates::check,
        onDownload = updates::download,
        onInstall = onInstall,
    )
}

@Composable
private fun SettingsRoute(engine: TimerEngine, onConfigChange: (TimerConfig) -> Unit) {
    val state by engine.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onConfigChange = onConfigChange)
}

/** Shared row style for the Wear lists, so every screen matches. */
@Composable
fun WearListButton(title: String, subtitle: String?, selected: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
