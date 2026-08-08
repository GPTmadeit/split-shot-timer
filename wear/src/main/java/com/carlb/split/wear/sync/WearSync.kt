package com.carlb.split.wear.sync

import android.content.Context
import android.util.Log
import com.carlb.split.core.LiveEvent
import com.carlb.split.core.ShotString
import com.carlb.split.core.Wire
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Watch -> phone.
 *
 * Live events go out over [MessageClient]: cheap, low latency, and dropped
 * silently if the phone is not there. That is the correct behaviour for a
 * mirror — the phone catching up mid-string is worthless.
 *
 * Completed strings go out over [DataClient], which replicates. Write it once
 * and the platform delivers it whenever the link returns, which is the whole
 * point: at a range the phone is in a bag on the bench and Bluetooth drops
 * constantly. The watch is the system of record; the phone is a replica.
 */
class WearSync(context: Context, private val scope: CoroutineScope) {

    private val app = context.applicationContext
    private val messageClient: MessageClient = Wearable.getMessageClient(app)
    private val dataClient: DataClient = Wearable.getDataClient(app)
    private val capabilityClient: CapabilityClient = Wearable.getCapabilityClient(app)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var phoneNodeId: String? = null

    fun refreshNodes() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val info = capabilityClient
                    .getCapability(Wire.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await()
                phoneNodeId = info.nodes.firstOrNull { it.isNearby }?.id
                    ?: info.nodes.firstOrNull()?.id
                _connected.value = phoneNodeId != null
            }.onFailure {
                _connected.value = false
            }
        }
    }

    /** Best effort. Never awaited, never blocks the timer. */
    fun sendLive(event: LiveEvent) {
        val node = phoneNodeId ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                messageClient.sendMessage(node, Wire.PATH_LIVE, Wire.encodeLive(event)).await()
            }.onFailure { Log.d(TAG, "live drop: ${it.message}") }
        }
    }

    /** Durable. Survives the phone being out of range for the whole session. */
    fun publishString(s: ShotString) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val req = PutDataMapRequest.create(Wire.stringPath(s.id)).apply {
                    dataMap.putString(Wire.KEY_PAYLOAD, Wire.encodeString(s))
                    dataMap.putLong(Wire.KEY_UPDATED_AT, System.currentTimeMillis())
                }
                dataClient.putDataItem(req.asPutDataRequest().setUrgent()).await()
            }.onFailure { Log.w(TAG, "string publish failed, retained locally: ${it.message}") }
        }
    }

    private companion object { const val TAG = "WearSync" }
}
