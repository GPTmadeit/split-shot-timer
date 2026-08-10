package com.carlb.split.wear.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.carlb.split.R
import com.carlb.split.core.ShotString
import com.carlb.split.wear.MainActivity
import com.carlb.split.wear.data.StringStore
import com.carlb.split.wear.sync.WearSync
import kotlinx.coroutines.launch

/**
 * Holds the microphone for the duration of a range session.
 *
 * Wear OS 5+ inherits the Android 14 rule that a microphone foreground service
 * can only be *started* while the app is already foregrounded — which is fine,
 * because you open the timer and press start. Once running, this keeps the mic
 * and the engine alive through ambient mode and screen-off, so the watch keeps
 * timing with the display dimmed on your wrist.
 */
class TimerService : LifecycleService() {

    inner class LocalBinder : Binder() {
        val service: TimerService get() = this@TimerService
    }

    private val binder = LocalBinder()

    lateinit var engine: TimerEngine
        private set
    lateinit var sync: WearSync
        private set
    lateinit var store: StringStore
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        store = StringStore(this)
        sync = WearSync(this, lifecycleScope)
        engine = TimerEngine(
            context = this,
            scope = lifecycleScope,
            onLive = { sync.sendLive(it) },
            onString = { persistAndPublish(it) },
        )
        lifecycleScope.launch {
            store.config().collect { engine.setConfig(it) }
        }
        lifecycleScope.launch {
            sync.connected.collect { engine.setPhoneConnected(it) }
        }
        sync.refreshNodes()
    }

    private fun persistAndPublish(s: ShotString) {
        lifecycleScope.launch {
            // Local first, always. The watch is the system of record; if the
            // phone never shows up, nothing is lost.
            store.append(s)
            sync.publishString(s)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_STOP -> stopSession()
        }
        // NOT_STICKY: a session is something the user explicitly starts by
        // opening the app. Re-creating this service unprompted after a kill
        // gave a crash here an infinite retry loop and made the app look like
        // it would not launch at all.
        return START_NOT_STICKY
    }

    private fun startSession() {
        // Foreground status has to be established first and unconditionally --
        // if anything below throws before startForeground(), the system kills
        // the process for not posting a notification in time, which reads to
        // the user as "the app does not open".
        runCatching {
            createChannel()
            startForeground(NOTIF_ID, buildNotification())
        }.onFailure {
            Log.e(TAG, "could not enter foreground", it)
            stopSelf()
            return
        }

        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "split:session").apply {
                    setReferenceCounted(false)
                    acquire(SESSION_WAKELOCK_MS)
                }
            }
        }.onFailure { Log.w(TAG, "wake lock unavailable", it) }

        // Each of these is individually non-fatal. The timer is still usable
        // without the phone link, and the UI surfaces a mic failure itself.
        runCatching { engine.openMic() }.onFailure { Log.e(TAG, "openMic failed", it) }
        runCatching { sync.refreshNodes() }.onFailure { Log.w(TAG, "node lookup failed", it) }
    }

    private fun stopSession() {
        engine.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Range session", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Range session")
            .setContentText("Timer listening")
            .setSmallIcon(R.drawable.ic_timer)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        engine.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.carlb.split.START_SESSION"
        const val ACTION_STOP = "com.carlb.split.STOP_SESSION"
        private const val TAG = "TimerService"
        private const val CHANNEL = "range_session"
        private const val NOTIF_ID = 42
        private const val SESSION_WAKELOCK_MS = 4L * 60 * 60 * 1000
    }
}
