package com.carlb.split.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlb.split.core.DrillLibrary
import com.carlb.split.core.Score
import com.carlb.split.core.ShotString
import com.carlb.split.mobile.sync.LiveMirror
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(strings: List<ShotString>, live: LiveMirror, onScore: (String, Double, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bestDraw = strings.mapNotNull { it.first }.minOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp, 28.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(live) }

        item {
            AnimatedVisibility(
                visible = live !is LiveMirror.Offline,
                enter = fadeIn() + expandVertically(spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut() + shrinkVertically(),
            ) { LiveCard(live) }
        }

        item { SessionStats(strings) }

        if (strings.size >= 2) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Label("Draw trend")
                        Spacer(Modifier.height(10.dp))
                        TrendChart(
                            values = strings.mapNotNull { it.first }.reversed(),
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            line = cs.primary,
                            fill = cs.primary,
                            grid = cs.outlineVariant,
                        )
                    }
                }
            }
        }

        item {
            Label(if (strings.isEmpty()) "No strings yet" else "Strings")
        }

        if (strings.isEmpty()) {
            item {
                Surface(
                    color = cs.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Run a string on the watch. Completed strings sync here even if the phone was out of range at the time.",
                        color = cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        }

        items(strings, key = { it.id }) { s ->
            StringCard(
                string = s,
                isBest = bestDraw != null && s.first == bestDraw,
                onScore = onScore,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun Header(live: LiveMirror) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "SPLIT",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 5.sp,
            color = cs.onBackground,
        )
        Spacer(Modifier.weight(1f))
        val connected = live !is LiveMirror.Offline
        val dot by animateColorAsState(
            if (connected) cs.primary else cs.outline,
            label = "dot",
        )
        if (connected) {
            LivePulse(cs.primary, Modifier.size(14.dp))
        } else {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(dot))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (connected) "WATCH LIVE" else "WATCH IDLE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = if (connected) cs.primary else cs.onSurfaceVariant,
        )
    }
}

/** Mirrors the running string. Best-effort by design — it is a view, not a record. */
@Composable
private fun LiveCard(live: LiveMirror) {
    val cs = MaterialTheme.colorScheme
    var nowMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        while (live is LiveMirror.Running) {
            withFrameMillis { nowMs = System.currentTimeMillis() }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            val label = when (live) {
                is LiveMirror.Standby -> "STANDBY"
                is LiveMirror.Running -> "RUNNING"
                is LiveMirror.Settling -> "SYNCING"
                LiveMirror.Offline -> ""
            }
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = cs.primary)
            Spacer(Modifier.height(6.dp))

            when (live) {
                is LiveMirror.Running -> {
                    val elapsed = ((nowMs - live.startedAtEpochMs).coerceAtLeast(0)) / 1000.0
                    Text(
                        clock(elapsed),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onPrimaryContainer,
                        letterSpacing = (-2).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Stat("SHOTS", "${live.shots.size}")
                        Stat("DRAW", live.shots.firstOrNull()?.let { f2(it) } ?: "--")
                        Stat(
                            "LAST SPLIT",
                            live.shots.zipWithNext { a, b -> b - a }.lastOrNull()?.let { f2(it) } ?: "--",
                        )
                    }
                }

                is LiveMirror.Standby -> Text(
                    DrillLibrary.byId(live.drillId).name,
                    fontSize = 20.sp,
                    color = cs.onPrimaryContainer,
                )

                else -> Text("Waiting for the string to land", fontSize = 13.sp, color = cs.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SessionStats(strings: List<ShotString>) {
    val cs = MaterialTheme.colorScheme
    val draws = strings.mapNotNull { it.first }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("STRINGS", "${strings.size}", Modifier.weight(1f))
        StatTile("BEST DRAW", draws.minOrNull()?.let { f2(it) } ?: "--", Modifier.weight(1f), cs.primary)
        StatTile("AVG DRAW", draws.takeIf { it.isNotEmpty() }?.average()?.let { f2(it) } ?: "--", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tint ?: cs.onSurface,
            )
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StringCard(
    string: ShotString,
    isBest: Boolean,
    onScore: (String, Double, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(Score()) }
    var major by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphBadge(isBest, if (isBest) cs.primary else cs.outline, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(string.drillName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text(
                        stamp(string.epochMillis),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurfaceVariant,
                    )
                }
                string.hitFactor?.let {
                    Text(
                        f2(it),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = cs.primary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat("DRAW", string.first?.let { f2(it) } ?: "--")
                Stat("TOTAL", string.total?.let { f2(it) } ?: "--")
                Stat("SHOTS", "${string.count}")
                Stat("FASTEST", string.fastestSplit?.let { f2(it) } ?: "--")
                string.splitSigma?.let { Stat("SIGMA", "±" + String.format(Locale.US, "%.3f", it)) }
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    SplitBars(
                        draw = string.first,
                        splits = string.splits,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((string.count * 15).dp.coerceAtLeast(30.dp)),
                        surface = cs.surfaceContainerHigh,
                        accent = cs.onSurfaceVariant,
                    )

                    if (string.rejectedByRecoil > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${string.rejectedByRecoil} report(s) rejected — no matching recoil impulse",
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Label("Score")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ZoneButton("A", score.a, Modifier.weight(1f)) { score = score.copy(a = it) }
                        ZoneButton("C", score.c, Modifier.weight(1f)) { score = score.copy(c = it) }
                        ZoneButton("D", score.d, Modifier.weight(1f)) { score = score.copy(d = it) }
                        ZoneButton("M", score.m, Modifier.weight(1f)) { score = score.copy(m = it) }
                        ZoneButton("NS", score.ns, Modifier.weight(1f)) { score = score.copy(ns = it) }
                    }
                    Spacer(Modifier.height(10.dp))
                    val hf = score.hitFactor(string.total ?: 0.0, major)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { major = !major }) {
                            Text(if (major) "Major" else "Minor", fontSize = 12.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("HF ", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text(
                            f2(hf),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = { onScore(string.id, hf, if (major) "major" else "minor") },
                            enabled = hf > 0.0,
                        ) { Text("Save", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneButton(label: String, count: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (count > 0) cs.primaryContainer else cs.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable { onChange(count + 1) },
    ) {
        Column(
            Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
            Text(
                "$count",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (count > 0) cs.primary else cs.onSurface,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = cs.onSurface,
        )
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
private fun clock(sec: Double) = String.format(Locale.US, "%.2f", sec)
private fun stamp(ms: Long) = SimpleDateFormat("MMM d  HH:mm", Locale.getDefault()).format(Date(ms))
