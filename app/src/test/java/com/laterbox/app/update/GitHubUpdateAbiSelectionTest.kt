package com.laterbox.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubUpdateAbiSelectionTest {

    private val sampleAssets = listOf(
        GitHubUpdateChecker.ReleaseAsset(
            name = "Laterbox-ARM32.apk",
            downloadUrl = "https://github.com/Adetisola/Laterbox/releases/download/v1.0.19/Laterbox-ARM32.apk",
            size = 52_000_000L
        ),
        GitHubUpdateChecker.ReleaseAsset(
            name = "Laterbox-ARM64.apk",
            downloadUrl = "https://github.com/Adetisola/Laterbox/releases/download/v1.0.19/Laterbox-ARM64.apk",
            size = 59_000_000L
        ),
        GitHubUpdateChecker.ReleaseAsset(
            name = "Laterbox-Universal.apk",
            downloadUrl = "https://github.com/Adetisola/Laterbox/releases/download/v1.0.19/Laterbox-Universal.apk",
            size = 103_000_000L
        )
    )

    @Test
    fun `64-bit device selects ARM64 APK`() {
        val supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a", "armeabi")
        val selected = GitHubUpdateChecker.selectBestApkAsset(sampleAssets, supportedAbis)

        assertNotNull(selected)
        assertEquals("Laterbox-ARM64.apk", selected!!.asset.name)
        assertEquals("ARM64 (arm64-v8a)", selected.targetAbiLabel)
        assertEquals(59_000_000L, selected.asset.size)
    }

    @Test
    fun `Samsung Galaxy A02s (32-bit ARM) selects ARM32 APK`() {
        // Physical Galaxy A02s reports only 32-bit ARM ABIs
        val supportedAbis = arrayOf("armeabi-v7a", "armeabi")
        val selected = GitHubUpdateChecker.selectBestApkAsset(sampleAssets, supportedAbis)

        assertNotNull(selected)
        assertEquals("Laterbox-ARM32.apk", selected!!.asset.name)
        assertEquals("ARM32 (armeabi-v7a)", selected.targetAbiLabel)
        assertEquals(52_000_000L, selected.asset.size)
    }

    @Test
    fun `falls back to Universal APK when ABI split is not available`() {
        val universalOnlyAssets = listOf(
            GitHubUpdateChecker.ReleaseAsset(
                name = "Laterbox-Universal.apk",
                downloadUrl = "https://example.com/Laterbox-Universal.apk",
                size = 103_000_000L
            )
        )

        val selected = GitHubUpdateChecker.selectBestApkAsset(
            universalOnlyAssets,
            arrayOf("armeabi-v7a", "armeabi")
        )

        assertNotNull(selected)
        assertEquals("Laterbox-Universal.apk", selected!!.asset.name)
        assertEquals("Universal", selected.targetAbiLabel)
    }

    @Test
    fun `legacy release with app-release apk is selected as Universal`() {
        val legacyAssets = listOf(
            GitHubUpdateChecker.ReleaseAsset(
                name = "app-release.apk",
                downloadUrl = "https://github.com/Adetisola/Laterbox/releases/download/v1.0.18/app-release.apk",
                size = 103_131_088L
            )
        )

        val selected = GitHubUpdateChecker.selectBestApkAsset(
            legacyAssets,
            arrayOf("arm64-v8a")
        )

        assertNotNull(selected)
        assertEquals("app-release.apk", selected!!.asset.name)
        assertEquals("Universal", selected.targetAbiLabel)
    }

    @Test
    fun `returns null when no APK assets exist`() {
        val nonApkAssets = listOf(
            GitHubUpdateChecker.ReleaseAsset(
                name = "source-code.zip",
                downloadUrl = "https://example.com/source.zip",
                size = 1000L
            )
        )

        val selected = GitHubUpdateChecker.selectBestApkAsset(nonApkAssets, arrayOf("arm64-v8a"))
        assertNull(selected)
    }
}
