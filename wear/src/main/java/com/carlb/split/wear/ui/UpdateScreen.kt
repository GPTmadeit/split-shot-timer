package com.carlb.split.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
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
import com.carlb.split.core.update.UpdateCatalog
import com.carlb.split.core.update.UpdateStatus
import com.carlb.split.core.update.UpdateUiState

@Composable
fun UpdateScreen(state: UpdateUiState, onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("Updates") } }

            item {
                Text(
                    "Installed  v${state.currentVersion}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { StatusLine(state) }

            if (state.downloading) {
                item {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        ProgressBar(state.progress, Modifier.fillMaxWidth().height(8.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // The platform installer always confirms, so this only ever offers
            // the APK to the system.
            if (state.apkReady) {
                item { Action("Install", HiViz, onInstall) }
            } else if (state.status is UpdateStatus.Available && !state.downloading) {
                item { Action("Download", HiViz, onDownload) }
            }

            if (state.idle) {
                item {
                    Action(
                        if (state.status is UpdateStatus.UpToDate) "Check again" else "Check",
                        null,
                        onCheck,
                    )
                }
            }

            item {
                Text(
                    "Downloads from this project's GitHub releases. Nothing is sent.",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun StatusLine(state: UpdateUiState) {
    when (val s = state.status) {
        is UpdateStatus.NotChecked -> Text(
            "Not checked yet",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        is UpdateStatus.Checking -> Text("Checking...", fontSize = 12.sp, color = Brass)

        is UpdateStatus.UpToDate -> Text(
            "Up to date",
            fontSize = 13.sp,
            color = Good,
            fontWeight = FontWeight.SemiBold,
        )

        is UpdateStatus.Failed -> Column {
            Text("Check failed", fontSize = 12.sp, color = Bad, fontWeight = FontWeight.SemiBold)
            Text(s.reason, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is UpdateStatus.Available -> Column {
            Text(
                "v${s.update.version} available",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HiViz,
            )
            Text(
                UpdateCatalog.humanSize(s.update.sizeBytes) +
                    if (s.update.prerelease) "  .  pre-release" else "",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Action(label: String, container: Color?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (container != null) {
            ButtonDefaults.buttonColors(containerColor = container)
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress, tween(180, easing = LinearEasing), label = "dl")
    Canvas(modifier) {
        val h = size.height
        drawRoundRect(SteelDim.copy(alpha = 0.5f), cornerRadius = CornerRadius(h / 2))
        if (p > 0f) {
            drawRoundRect(HiViz, size = Size(size.width * p, h), cornerRadius = CornerRadius(h / 2))
        }
    }
}
