package com.carlb.split.wear

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Text
import com.carlb.split.wear.timer.TimerService
import com.carlb.split.wear.ui.SplitTheme
import com.carlb.split.wear.ui.WearApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var service by mutableStateOf<TimerService?>(null)
    private var permissionGranted by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as TimerService.LocalBinder).service
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) startSession()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A range session is minutes of staring at the face between strings.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            val svc = service
            SplitTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        !permissionGranted -> Text(
                            "Grant microphone access to time live fire",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(24.dp),
                        )

                        svc == null -> Text("Starting", fontSize = 12.sp)

                        else -> WearApp(
                            engine = svc.engine,
                            onConfigChange = { cfg ->
                                svc.engine.setConfig(cfg)
                                lifecycleScope.launch { svc.store.saveConfig(cfg) }
                            },
                        )
                    }
                }
            }
        }

        if (permissionGranted) startSession() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    /**
     * The mic foreground service must be *started* from the foreground — that is
     * the Android 14 / Wear OS 5 while-in-use rule. Doing it here, on an explicit
     * user-visible launch, is exactly the sanctioned path.
     */
    private fun startSession() {
        val intent = Intent(this, TimerService::class.java).setAction(TimerService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        bindService(Intent(this, TimerService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        runCatching { unbindService(connection) }
        if (isFinishing) {
            startService(Intent(this, TimerService::class.java).setAction(TimerService.ACTION_STOP))
        }
        super.onDestroy()
    }
}
