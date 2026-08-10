package com.carlb.split.wear.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.carlb.split.core.update.UpdateCatalog
import com.carlb.split.core.update.UpdateStatus
import com.carlb.split.core.update.UpdateUiState

@Composable
fun UpdateScreen(state: UpdateUiState, onCheck: () -> Unit, onDownload: () -> Unit, onInstall: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Updates") } }

        item {
            Text(
                "Installed  v${state.currentVersion}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Steel,
            )
        }

        when (val s = state.status) {
            is UpdateStatus.NotChecked -> item {
                Text("Not checked yet", fontSize = 12.sp, color = Steel)
            }

            is UpdateStatus.Checking -> item {
                Text("Checking…", fontSize = 12.sp, color = Brass)
            }

            is UpdateStatus.UpToDate -> item {
                Text("Up to date", fontSize = 13.sp, color = Good, fontWeight = FontWeight.SemiBold)
            }

            is UpdateStatus.Failed -> item {
                Column {
                    Text("Check failed", fontSize = 12.sp, color = Bad, fontWeight = FontWeight.SemiBold)
                    Text(s.reason, fontSize = 10.sp, color = Steel)
                }
            }

            is UpdateStatus.Available -> {
                item {
                    Column {
                        Text(
                            "v${s.update.version} available",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = HiViz,
                        )
                        Text(
                            UpdateCatalog.humanSize(s.update.sizeBytes) +
                                if (s.update.prerelease) "  ·  pre-release" else "",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Steel,
                        )
                    }
                }

                if (state.downloading) {
                    item {
                        Column {
                            ProgressBar(state.progress, Modifier.fillMaxWidth().height(8.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Steel,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(2.dp)) }

        // The installer itself always asks for confirmation, so this button
        // only ever *offers* the APK to the system.
        if (state.apkReady) {
            item {
                Action("Install", HiViz, onInstall)
            }
        } else if (state.status is UpdateStatus.Available && !state.downloading) {
            item {
                Action("Download", HiViz, onDownload)
            }
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
                "Downloads from the project's GitHub releases. Nothing else is sent.",
                fontSize = 8.sp,
                color = SteelDim,
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        }
    }
}

@Composable
private fun Action(label: String, container: androidx.compose.ui.graphics.Color?, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (container != null) {
            ButtonDefaults.buttonColors(containerColor = container)
        } else {
            ButtonDefaults.buttonColors()
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
            drawRoundRect(
                HiViz,
                size = Size(size.width * p, h),
                cornerRadius = CornerRadius(h / 2),
            )
        }
    }
}
