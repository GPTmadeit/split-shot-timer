package com.carlb.split.core.update

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Updates screen on both apps, so the watch and the phone behave
 * identically and there is only one place for this logic to be wrong.
 */
class UpdateController(
    context: Context,
    private val scope: CoroutineScope,
    private val currentVersion: String,
    private val assetPrefix: String,
) {
    private val client = UpdateClient(context)

    private val _state = MutableStateFlow(UpdateUiState(currentVersion = currentVersion))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun check() {
        if (_state.value.busy) return
        job?.cancel()
        _state.value = _state.value.copy(status = UpdateStatus.Checking, progress = 0f, apkReady = false)
        job = scope.launch {
            _state.value = _state.value.copy(status = client.check(currentVersion, assetPrefix))
        }
    }

    fun download() {
        val available = (_state.value.status as? UpdateStatus.Available)?.update ?: return
        if (_state.value.busy) return
        job?.cancel()
        _state.value = _state.value.copy(downloading = true, progress = 0f, apkReady = false)
        job = scope.launch {
            client.download(available) { p -> _state.value = _state.value.copy(progress = p) }
                .onSuccess {
                    _state.value = _state.value.copy(downloading = false, apkReady = true, progress = 1f)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        downloading = false,
                        progress = 0f,
                        status = UpdateStatus.Failed(e.message ?: "Download failed"),
                    )
                }
        }
    }

    /** Returns the install intent once [UpdateUiState.apkReady], else null. */
    fun installIntent(): android.content.Intent? {
        val update = (_state.value.status as? UpdateStatus.Available)?.update ?: return null
        val apk = java.io.File(java.io.File(cacheDirOf(), "updates"), update.assetName)
        return if (apk.exists()) client.installIntent(apk) else null
    }

    private val appContext = context.applicationContext
    private fun cacheDirOf() = appContext.cacheDir

    fun reset() {
        job?.cancel()
        _state.value = UpdateUiState(currentVersion = currentVersion)
    }
}

data class UpdateUiState(
    val currentVersion: String,
    val status: UpdateStatus = UpdateStatus.NotChecked,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val apkReady: Boolean = false,
) {
    val busy: Boolean get() = status is UpdateStatus.Checking || downloading
    val idle: Boolean get() = !busy
}
