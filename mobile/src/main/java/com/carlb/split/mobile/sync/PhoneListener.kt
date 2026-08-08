package com.carlb.split.mobile.sync

import com.carlb.split.core.LiveEvent
import com.carlb.split.core.Wire
import com.carlb.split.mobile.data.PhoneStore
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * In-process bus for the live mirror. Deliberately not persisted: a live event
 * that arrives late is worse than one that never arrives, so nothing here
 * outlives the string it describes.
 */
object LiveBus {
    private val _state = MutableStateFlow<LiveMirror>(LiveMirror.Offline)
    val state: StateFlow<LiveMirror> = _state.asStateFlow()

    fun push(e: LiveEvent) {
        _state.value = when (e) {
            is LiveEvent.Armed -> LiveMirror.Standby(e.drillId)
            is LiveEvent.Started -> LiveMirror.Running(e.drillId, e.startedAtEpochMs, emptyList())
            is LiveEvent.Shot -> {
                val cur = _state.value
                if (cur is LiveMirror.Running) cur.copy(shots = cur.shots + e.atSec) else cur
            }
            is LiveEvent.Ended -> LiveMirror.Settling(e.stringId)
            LiveEvent.Cancelled -> LiveMirror.Offline
        }
    }

    fun clear() { _state.value = LiveMirror.Offline }
}

sealed interface LiveMirror {
    data object Offline : LiveMirror
    data class Standby(val drillId: String) : LiveMirror
    data class Running(val drillId: String, val startedAtEpochMs: Long, val shots: List<Double>) : LiveMirror
    /** String finished; waiting for the durable copy to land. */
    data class Settling(val stringId: String) : LiveMirror
}

/**
 * Watch -> phone receiver.
 *
 * Note this is a [WearableListenerService], not something bound to the UI: data
 * items are delivered even when the phone app has never been opened, so a whole
 * session's strings land in the log while the phone sits in a range bag.
 */
class PhoneListener : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != Wire.PATH_LIVE) return
        runCatching { Wire.decodeLive(event.data) }.onSuccess { LiveBus.push(it) }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val store = PhoneStore(applicationContext)
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val uri = event.dataItem.uri
            if (!uri.path.orEmpty().startsWith(Wire.PATH_STRING)) continue

            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val raw = map.getString(Wire.KEY_PAYLOAD) ?: continue
            runCatching { Wire.decodeString(raw) }.onSuccess { s ->
                scope.launch {
                    store.upsert(s)
                    LiveBus.clear()
                }
            }
        }
    }
}
