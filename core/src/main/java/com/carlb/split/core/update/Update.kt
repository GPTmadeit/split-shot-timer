package com.carlb.split.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Checking GitHub for a newer build.
 *
 * The parsing and comparison here are deliberately free of Android and of any
 * network call, so the part that decides "is this an upgrade?" can be unit
 * tested. [UpdateClient] does the I/O.
 */

@Serializable
data class GhAsset(val name: String, @SerialName("browser_download_url") val downloadUrl: String, val size: Long = 0)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val pageUrl: String = "",
    val assets: List<GhAsset> = emptyList(),
)

data class AvailableUpdate(
    val version: String,
    val tag: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val pageUrl: String,
    val prerelease: Boolean,
)

sealed interface UpdateStatus {
    /** Nothing has been asked of the network yet. Distinct from [UpToDate] on
     *  purpose: showing "up to date" before a check has run would be a claim
     *  the app has not earned. */
    data object NotChecked : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val update: AvailableUpdate) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

/**
 * Dotted-numeric version ordering, with a pre-release suffix sorting *below*
 * the same numbers without one (1.0.0-rc1 < 1.0.0), per semver.
 */
object SemVer {

    fun compare(a: String, b: String): Int {
        val (aNums, aPre) = split(a)
        val (bNums, bPre) = split(b)

        val width = maxOf(aNums.size, bNums.size)
        for (i in 0 until width) {
            val x = aNums.getOrElse(i) { 0 }
            val y = bNums.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }

        // Equal numerically: a build with no suffix is the released one.
        return when {
            aPre == null && bPre == null -> 0
            aPre == null -> 1
            bPre == null -> -1
            else -> aPre.compareTo(bPre)
        }
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun split(raw: String): Pair<List<Int>, String?> {
        val v = raw.trim().removePrefix("v").removePrefix("V")
        val dash = v.indexOf('-')
        val core = if (dash >= 0) v.substring(0, dash) else v
        val pre = if (dash >= 0) v.substring(dash + 1).ifEmpty { null } else null
        val nums = core.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return nums to pre
    }
}

object UpdateCatalog {

    const val OWNER = "GPTmadeit"
    const val REPO = "split-shot-timer"

    /**
     * Deliberately /releases and not /releases/latest. Every release of this
     * project so far is a pre-release, and /releases/latest excludes those --
     * it would report "no updates" forever.
     */
    const val RELEASES_URL = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=20"
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases"

    /** Asset name prefixes, so each app finds its own APK. */
    const val ASSET_WEAR = "split-wear"
    const val ASSET_MOBILE = "split-mobile"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<GhRelease> =
        runCatching { json.decodeFromString<List<GhRelease>>(body) }.getOrDefault(emptyList())

    /**
     * Picks the newest usable release carrying an asset for this app.
     *
     * Drafts are skipped (not downloadable). Pre-releases are kept, because
     * that is all this project publishes; the flag is surfaced so the UI can
     * say so.
     */
    fun evaluate(releases: List<GhRelease>, currentVersion: String, assetPrefix: String): UpdateStatus {
        val candidates = releases
            .filterNot { it.draft }
            .mapNotNull { rel ->
                val asset = rel.assets.firstOrNull {
                    it.name.startsWith(assetPrefix) && it.name.endsWith(".apk")
                } ?: return@mapNotNull null
                AvailableUpdate(
                    version = rel.tag.removePrefix("v"),
                    tag = rel.tag,
                    notes = rel.body.orEmpty(),
                    assetName = asset.name,
                    downloadUrl = asset.downloadUrl,
                    sizeBytes = asset.size,
                    pageUrl = rel.pageUrl.ifEmpty { RELEASES_PAGE },
                    prerelease = rel.prerelease,
                )
            }

        if (candidates.isEmpty()) return UpdateStatus.Failed("No installable release found")

        val newest = candidates.maxWithOrNull { a, b -> SemVer.compare(a.version, b.version) }
            ?: return UpdateStatus.Failed("No installable release found")

        return if (SemVer.isNewer(newest.version, currentVersion)) {
            UpdateStatus.Available(newest)
        } else {
            UpdateStatus.UpToDate
        }
    }

    fun humanSize(bytes: Long): String = when {
        bytes <= 0 -> "unknown size"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
