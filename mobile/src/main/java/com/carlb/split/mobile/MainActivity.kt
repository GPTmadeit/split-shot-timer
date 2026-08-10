package com.carlb.split.mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.carlb.split.core.ShotString
import com.carlb.split.core.update.UpdateCatalog
import com.carlb.split.core.update.UpdateController
import com.carlb.split.core.update.UpdateStatus
import com.carlb.split.mobile.data.PhoneStore
import com.carlb.split.mobile.sync.LiveBus
import com.carlb.split.mobile.sync.LiveMirror
import com.carlb.split.mobile.ui.HomeScreen
import com.carlb.split.mobile.ui.LivePulse
import com.carlb.split.mobile.ui.MenuButton
import com.carlb.split.mobile.ui.MenuSheet
import com.carlb.split.mobile.ui.SplitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var store: PhoneStore

    private val updates by lazy {
        UpdateController(
            context = this,
            scope = lifecycleScope,
            currentVersion = BuildConfig.VERSION_NAME,
            assetPrefix = UpdateCatalog.ASSET_MOBILE,
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)

        val stringsFlow = MutableStateFlow(emptyList<ShotString>())
        lifecycleScope.launch { store.strings().collect { stringsFlow.value = it } }

        setContent {
            val dark = isSystemInDarkTheme()
            SplitTheme(dark = dark) {
                val strings by stringsFlow.collectAsStateWithLifecycle()
                val live by LiveBus.state.collectAsStateWithLifecycle()
                val update by updates.state.collectAsStateWithLifecycle()
                var menuOpen by remember { mutableStateOf(false) }

                // Stock Android structure: a real TopAppBar that reacts to
                // scroll, and Scaffold owning the window insets.
                val appBarState = rememberTopAppBarState()
                val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(appBarState)
                val connected = live !is LiveMirror.Offline

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "SPLIT",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp,
                                )
                            },
                            actions = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (connected) {
                                        LivePulse(
                                            MaterialTheme.colorScheme.primary,
                                            Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        if (connected) "LIVE" else "IDLE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        color = if (connected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    MenuButton(
                                        hasBadge = update.status is UpdateStatus.Available,
                                        onClick = { menuOpen = true },
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { inner ->
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(inner),
                    ) {
                        HomeScreen(
                            strings = strings,
                            live = live,
                            onScore = { id, hf, pf ->
                                lifecycleScope.launch { store.setHitFactor(id, hf, pf) }
                            },
                        )
                    }
                }

                if (menuOpen) {
                    MenuSheet(
                        version = BuildConfig.VERSION_NAME,
                        update = update,
                        onCheck = updates::check,
                        onDownload = updates::download,
                        onInstall = ::launchInstaller,
                        onDismiss = { menuOpen = false },
                    )
                }
            }
        }
    }

    /**
     * Offers the downloaded APK to the platform installer. The system asks the
     * user to confirm; there is no silent install path here.
     */
    private fun launchInstaller() {
        val intent = updates.installIntent() ?: return
        runCatching { startActivity(intent) }
            .onFailure { Log.w("MainActivity", "no package installer available", it) }
    }
}
