package com.carlb.split.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlb.split.core.update.UpdateCatalog
import com.carlb.split.core.update.UpdateStatus
import com.carlb.split.core.update.UpdateUiState

/**
 * Overflow affordance. Drawn rather than pulled from an icon font so it matches
 * the watch's menu button exactly and adds no dependency.
 */
@Composable
fun MenuButton(hasBadge: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val badge by animateFloatAsState(
        if (hasBadge) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "badge",
    )
    Box(
        modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val cx = size.width / 2f
            val gap = size.height * 0.26f
            listOf(cx to size.height / 2f - gap, cx to size.height / 2f, cx to size.height / 2f + gap)
                .forEach { (x, y) -> drawCircle(cs.onSurfaceVariant, size.minDimension * 0.09f, Offset(x, y)) }
            if (badge > 0.01f) {
                drawCircle(
                    cs.primary.copy(alpha = badge),
                    radius = size.minDimension * 0.15f * badge,
                    center = Offset(size.width * 0.86f, size.height * 0.16f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    version: String,
    update: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "MENU",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = cs.onSurfaceVariant,
            )

            UpdateCard(version, update, onCheck, onDownload, onInstall)

            Surface(
                color = cs.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Settings", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text(
                        "Timer settings live on the watch, under its own menu, because the " +
                            "watch owns the microphone and the clock.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Text(
                "SPLIT v$version",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateCard(
    version: String,
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val available = state.status as? UpdateStatus.Available

    Surface(
        color = if (available != null) cs.primaryContainer else cs.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Updates",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (available != null) cs.onPrimaryContainer else cs.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "installed v$version",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurfaceVariant,
                )
            }

            when (val s = state.status) {
                is UpdateStatus.NotChecked ->
                    Text("Not checked yet.", fontSize = 13.sp, color = cs.onSurfaceVariant)

                is UpdateStatus.Checking ->
                    Text("Checking…", fontSize = 13.sp, color = cs.onSurfaceVariant)

                is UpdateStatus.UpToDate ->
                    Text("You are on the latest release.", fontSize = 13.sp, color = Good)

                is UpdateStatus.Failed ->
                    Column {
                        Text("Check failed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Bad)
                        Text(s.reason, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }

                is UpdateStatus.Available ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "v${s.update.version} available",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = cs.primary,
                        )
                        Text(
                            UpdateCatalog.humanSize(s.update.sizeBytes) +
                                if (s.update.prerelease) "  ·  pre-release" else "",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = cs.onSurfaceVariant,
                        )
                    }
            }

            AnimatedVisibility(visible = state.downloading, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(state.progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.apkReady -> Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    ) { Text("Install") }

                    available != null && !state.downloading -> Button(onClick = onDownload) {
                        Text("Download")
                    }

                    else -> Unit
                }
                if (state.idle) {
                    OutlinedButton(onClick = onCheck) {
                        Text(if (state.status is UpdateStatus.UpToDate) "Check again" else "Check")
                    }
                }
            }

            Text(
                "Downloads the APK from this project's GitHub releases. The system asks you " +
                    "to confirm before anything installs. Nothing else is sent.",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** Keeps the shared palette referenced from this file. */
internal val menuGuard = listOf(Good, Bad)
