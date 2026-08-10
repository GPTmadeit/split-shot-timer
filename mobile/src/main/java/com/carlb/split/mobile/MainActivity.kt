package com.carlb.split.mobile

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.carlb.split.core.ShotString
import com.carlb.split.core.update.UpdateCatalog
import com.carlb.split.core.update.UpdateController
import com.carlb.split.core.update.UpdateStatus
import com.carlb.split.mobile.data.PhoneStore
import com.carlb.split.mobile.sync.LiveBus
import com.carlb.split.mobile.ui.HomeScreen
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

                Scaffold { inner ->
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
                            updateAvailable = update.status is UpdateStatus.Available,
                            onOpenMenu = { menuOpen = true },
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
