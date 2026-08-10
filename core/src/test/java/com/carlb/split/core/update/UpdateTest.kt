package com.carlb.split.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun `orders by numeric components`() {
        assertTrue(SemVer.isNewer("0.3.0", "0.2.1"))
        assertTrue(SemVer.isNewer("0.2.2", "0.2.1"))
        assertTrue(SemVer.isNewer("1.0.0", "0.99.99"))
        assertFalse(SemVer.isNewer("0.2.1", "0.3.0"))
    }

    @Test
    fun `equal versions are not newer`() {
        assertFalse(SemVer.isNewer("0.2.1", "0.2.1"))
        assertEquals(0, SemVer.compare("0.2.1", "0.2.1"))
    }

    @Test
    fun `a v prefix is ignored`() {
        assertEquals(0, SemVer.compare("v0.2.1", "0.2.1"))
        assertTrue(SemVer.isNewer("v0.3.0", "0.2.1"))
    }

    @Test
    fun `10 sorts above 9 rather than lexically below it`() {
        // The bug every hand-rolled string comparison has.
        assertTrue(SemVer.isNewer("0.10.0", "0.9.0"))
        assertTrue(SemVer.isNewer("1.0.10", "1.0.9"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, SemVer.compare("1.2", "1.2.0"))
        assertTrue(SemVer.isNewer("1.3", "1.2.9"))
    }

    @Test
    fun `a prerelease sorts below the same release`() {
        assertTrue(SemVer.isNewer("1.0.0", "1.0.0-rc1"))
        assertFalse(SemVer.isNewer("1.0.0-rc1", "1.0.0"))
        assertTrue(SemVer.isNewer("1.0.0-rc2", "1.0.0-rc1"))
    }

    @Test
    fun `garbage does not throw`() {
        SemVer.compare("", "")
        SemVer.compare("not-a-version", "0.1.0")
        assertFalse(SemVer.isNewer("", "0.1.0"))
    }
}

class UpdateCatalogTest {

    private fun release(tag: String, assets: List<String>, prerelease: Boolean = true, draft: Boolean = false) =
        GhRelease(
            tag = tag,
            body = "notes for $tag",
            draft = draft,
            prerelease = prerelease,
            pageUrl = "https://github.com/x/y/releases/tag/$tag",
            assets = assets.map { GhAsset(it, "https://dl/$tag/$it", 1_000_000) },
        )

    private val feed = listOf(
        release("v0.3.0", listOf("split-wear-v0.3.0-debug.apk", "split-mobile-v0.3.0-debug.apk")),
        release("v0.2.1", listOf("split-wear-v0.2.1-debug.apk", "split-mobile-v0.2.1-debug.apk")),
        release("v0.2.0", listOf("split-wear-v0.2.0-debug.apk", "split-mobile-v0.2.0-debug.apk")),
    )

    @Test
    fun `finds a newer release`() {
        val s = UpdateCatalog.evaluate(feed, "0.2.1", UpdateCatalog.ASSET_WEAR)
        assertTrue(s is UpdateStatus.Available)
        assertEquals("0.3.0", (s as UpdateStatus.Available).update.version)
        assertEquals("split-wear-v0.3.0-debug.apk", s.update.assetName)
    }

    @Test
    fun `each app is offered its own asset`() {
        val wear = UpdateCatalog.evaluate(feed, "0.1.0", UpdateCatalog.ASSET_WEAR)
        val mobile = UpdateCatalog.evaluate(feed, "0.1.0", UpdateCatalog.ASSET_MOBILE)
        assertTrue((wear as UpdateStatus.Available).update.assetName.startsWith("split-wear"))
        assertTrue((mobile as UpdateStatus.Available).update.assetName.startsWith("split-mobile"))
    }

    @Test
    fun `running the newest reports up to date`() {
        assertTrue(UpdateCatalog.evaluate(feed, "0.3.0", UpdateCatalog.ASSET_WEAR) is UpdateStatus.UpToDate)
    }

    @Test
    fun `a newer local build is not a downgrade prompt`() {
        assertTrue(UpdateCatalog.evaluate(feed, "0.9.0", UpdateCatalog.ASSET_WEAR) is UpdateStatus.UpToDate)
    }

    @Test
    fun `prereleases are offered because that is all this project ships`() {
        // /releases/latest would exclude every one of these and report nothing
        // forever, which is exactly why the catalog reads the full list.
        assertTrue(feed.all { it.prerelease })
        assertTrue(UpdateCatalog.evaluate(feed, "0.2.1", UpdateCatalog.ASSET_WEAR) is UpdateStatus.Available)
    }

    @Test
    fun `drafts are skipped`() {
        val withDraft = listOf(
            release("v0.4.0", listOf("split-wear-v0.4.0-debug.apk"), draft = true),
        ) + feed
        val s = UpdateCatalog.evaluate(withDraft, "0.2.1", UpdateCatalog.ASSET_WEAR)
        assertEquals("0.3.0", (s as UpdateStatus.Available).update.version)
    }

    @Test
    fun `releases without a matching asset are skipped`() {
        val docsOnly = listOf(release("v0.5.0", listOf("SHA256SUMS.txt"))) + feed
        val s = UpdateCatalog.evaluate(docsOnly, "0.2.1", UpdateCatalog.ASSET_WEAR)
        assertEquals("0.3.0", (s as UpdateStatus.Available).update.version)
    }

    @Test
    fun `an empty feed fails rather than claiming up to date`() {
        // Reporting "up to date" on a failed lookup would hide a broken updater.
        assertTrue(UpdateCatalog.evaluate(emptyList(), "0.2.1", UpdateCatalog.ASSET_WEAR) is UpdateStatus.Failed)
    }

    @Test
    fun `parses a realistic github payload`() {
        val body = """
            [{
              "tag_name": "v0.3.0",
              "name": "SPLIT v0.3.0",
              "body": "release notes",
              "draft": false,
              "prerelease": true,
              "html_url": "https://github.com/GPTmadeit/split-shot-timer/releases/tag/v0.3.0",
              "unknown_future_field": 123,
              "assets": [
                {"name":"split-wear-v0.3.0-debug.apk",
                 "browser_download_url":"https://github.com/dl/split-wear-v0.3.0-debug.apk",
                 "size": 38423053,
                 "download_count": 7}
              ]
            }]
        """.trimIndent()
        val parsed = UpdateCatalog.parse(body)
        assertEquals(1, parsed.size)
        assertEquals("v0.3.0", parsed[0].tag)
        assertEquals(38_423_053L, parsed[0].assets[0].size)

        val s = UpdateCatalog.evaluate(parsed, "0.2.1", UpdateCatalog.ASSET_WEAR)
        assertTrue(s is UpdateStatus.Available)
        assertEquals(
            "https://github.com/dl/split-wear-v0.3.0-debug.apk",
            (s as UpdateStatus.Available).update.downloadUrl,
        )
    }

    @Test
    fun `malformed json yields an empty list rather than throwing`() {
        assertTrue(UpdateCatalog.parse("not json").isEmpty())
        assertTrue(UpdateCatalog.parse("").isEmpty())
    }

    @Test
    fun `human readable sizes`() {
        assertEquals("unknown size", UpdateCatalog.humanSize(0))
        assertEquals("512 KB", UpdateCatalog.humanSize(512L * 1024))
        assertEquals("36.6 MB", UpdateCatalog.humanSize(38_423_053))
    }
}
