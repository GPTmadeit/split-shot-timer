package com.carlb.split.mobile

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.carlb.split.mobile.data.PhoneStore
import com.carlb.split.mobile.sync.LiveBus
import com.carlb.split.mobile.ui.HomeScreen
import com.carlb.split.mobile.ui.SplitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var store: PhoneStore

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = PhoneStore(this)

        val stringsFlow = MutableStateFlow(emptyList<com.carlb.split.core.ShotString>())
        lifecycleScope.launch { store.strings().collect { stringsFlow.value = it } }

        setContent {
            val dark = isSystemInDarkTheme()
            SplitTheme(dark = dark) {
                val strings by stringsFlow.collectAsStateWithLifecycle()
                val live by LiveBus.state.collectAsStateWithLifecycle()

                Scaffold { inner ->
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize().padding(inner),
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
            }
        }
    }
}
