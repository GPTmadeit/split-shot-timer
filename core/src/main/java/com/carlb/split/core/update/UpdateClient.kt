package com.carlb.split.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The only networking in this project.
 *
 * Scope is deliberately tiny and fixed at compile time: it talks to
 * api.github.com and objects.githubusercontent.com for this one repository and
 * nothing else. There is no telemetry, no analytics, and no user data of any
 * kind leaves the device -- a check is an unauthenticated GET that sends only
 * what any HTTP request must.
 *
 * Nothing here runs unless the user asks for it from the Updates screen.
 */
class UpdateClient(private val context: Context) {

    suspend fun check(currentVersion: String, assetPrefix: String): UpdateStatus = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(UpdateCatalog.RELEASES_URL)
            val releases = UpdateCatalog.parse(body)
            if (releases.isEmpty()) {
                UpdateStatus.Failed("Could not read the release list")
            } else {
                UpdateCatalog.evaluate(releases, currentVersion, assetPrefix)
            }
        }.getOrElse { e ->
            Log.w(TAG, "update check failed", e)
            UpdateStatus.Failed(friendly(e))
        }
    }

    /**
     * Streams the APK into cache. Reports 0..1 progress so a 38 MB download on
     * a watch does not look like a hang.
     */
    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val out = File(dir, update.assetName)

                val conn = open(update.downloadUrl)
                conn.inputStream.use { input ->
                    out.outputStream().use { sink ->
                        val total = if (update.sizeBytes > 0) update.sizeBytes else conn.contentLengthLong
                        val buf = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            written += n
                            if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                conn.disconnect()

                if (out.length() <= 0) error("Downloaded file was empty")
                onProgress(1f)
                out
            }.onFailure { Log.w(TAG, "download failed", it) }
        }

    /**
     * Hands the APK to the system package installer.
     *
     * The install itself is always the user's decision, made in the platform's
     * own confirmation UI -- this cannot and should not install silently. The
     * new build must be signed with the same key as the installed one, which is
     * why the signing key is pinned in this repository.
     */
    fun installIntent(apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun get(url: String): String {
        val conn = open(url)
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        // GitHub asset URLs redirect to a CDN host, and HttpURLConnection will
        // not follow a redirect across protocols on its own.
        repeat(MAX_REDIRECTS) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "split-shot-timer")
            }
            when (val code = conn.responseCode) {
                in 200..299 -> return conn

                301, 302, 303, 307, 308 -> {
                    val next = conn.getHeaderField("Location")
                    conn.disconnect()
                    current = next ?: error("Redirect without a location")
                }

                403 -> {
                    conn.disconnect()
                    error("GitHub rate limit reached, try again later")
                }

                404 -> {
                    conn.disconnect()
                    error("Release not found")
                }

                else -> {
                    conn.disconnect()
                    error("Server returned $code")
                }
            }
        }
        error("Too many redirects")
    }

    private fun friendly(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException -> "No internet connection"
            e is java.net.SocketTimeoutException -> "Connection timed out"
            m.isNotBlank() -> m
            else -> "Update check failed"
        }
    }

    private companion object {
        const val TAG = "UpdateClient"
        const val MAX_REDIRECTS = 5
    }
}
