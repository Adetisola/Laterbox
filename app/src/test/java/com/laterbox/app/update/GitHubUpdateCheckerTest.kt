package com.laterbox.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitHubUpdateCheckerTest {

    private val sampleManifestJson = """
    {
      "schemaVersion": 1,
      "targetVersion": "1.0.35",
      "targetVersionCode": 135,
      "generatedAt": 1731420000000,
      "patches": [
        {
          "architecture": "arm64-v8a",
          "architectureLabel": "ARM64 (arm64-v8a)",
          "baseVersion": "1.0.34",
          "baseVersionCode": 134,
          "baseApkSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
          "targetApkFilename": "Laterbox-ARM64.apk",
          "targetApkSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
          "targetApkSize": 60000000,
          "patchFilename": "Laterbox-ARM64-1.0.34-to-1.0.35.patch",
          "patchSha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
          "patchSize": 4500000,
          "patchAlgorithm": "BSDIFF40"
        },
        {
          "architecture": "armeabi-v7a",
          "architectureLabel": "ARM32 (armeabi-v7a)",
          "baseVersion": "1.0.34",
          "baseVersionCode": 134,
          "baseApkSha256": "1111111111111111111111111111111111111111111111111111111111111111",
          "targetApkFilename": "Laterbox-ARM32.apk",
          "targetApkSha256": "2222222222222222222222222222222222222222222222222222222222222222",
          "targetApkSize": 53000000,
          "patchFilename": "Laterbox-ARM32-1.0.34-to-1.0.35.patch",
          "patchSha256": "3333333333333333333333333333333333333333333333333333333333333333",
          "patchSize": 4200000,
          "patchAlgorithm": "BSDIFF40"
        }
      ]
    }
    """.trimIndent()

    @Test
    fun `fromJson parses valid delta manifest correctly`() {
        val manifest = DeltaManifest.fromJson(sampleManifestJson)
        assertNotNull(manifest)
        assertEquals(1, manifest!!.schemaVersion)
        assertEquals("1.0.35", manifest.targetVersion)
        assertEquals(135L, manifest.targetVersionCode)
        assertEquals(2, manifest.patches.size)

        val arm64 = manifest.patches[0]
        assertEquals("arm64-v8a", arm64.architecture)
        assertEquals("1.0.34", arm64.baseVersion)
        assertEquals("Laterbox-ARM64-1.0.34-to-1.0.35.patch", arm64.patchFilename)
        assertEquals(4500000L, arm64.patchSize)
        assertEquals("BSDIFF40", arm64.patchAlgorithm)
    }

    @Test
    fun `fromJson handles corrupted JSON safely and returns null`() {
        val corrupted = "{ schemaVersion: 1, targetVersion: "
        val manifest = DeltaManifest.fromJson(corrupted)
        assertNull(manifest)
    }

    @Test
    fun `findCompatiblePatch matches installed APK SHA-256 and ABI`() {
        val manifest = DeltaManifest.fromJson(sampleManifestJson)!!
        val installedSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val supportedAbis = arrayOf("arm64-v8a", "armeabi-v7a")

        val match = manifest.findCompatiblePatch(installedSha, supportedAbis)
        assertNotNull(match)
        assertEquals("arm64-v8a", match!!.architecture)
        assertEquals("Laterbox-ARM64-1.0.34-to-1.0.35.patch", match.patchFilename)
        assertEquals(4500000L, match.patchSize)
    }

    @Test
    fun `findCompatiblePatch returns null on base APK SHA-256 mismatch`() {
        val manifest = DeltaManifest.fromJson(sampleManifestJson)!!
        val modifiedInstalledSha = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        val supportedAbis = arrayOf("arm64-v8a")

        val match = manifest.findCompatiblePatch(modifiedInstalledSha, supportedAbis)
        assertNull(match)
    }

    @Test
    fun `findCompatiblePatch returns null on unsupported ABI`() {
        val manifest = DeltaManifest.fromJson(sampleManifestJson)!!
        val arm64Sha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val x86Abis = arrayOf("x86_64", "x86")

        val match = manifest.findCompatiblePatch(arm64Sha, x86Abis)
        assertNull(match)
    }

    @Test
    fun `effective download size reflects delta patch when available and full APK otherwise`() {
        val patchInfo = DeltaPatchInfo(
            architecture = "arm64-v8a",
            architectureLabel = "ARM64 (arm64-v8a)",
            baseVersion = "1.0.34",
            baseVersionCode = 134L,
            baseApkSha256 = "base",
            targetApkFilename = "Laterbox-ARM64.apk",
            targetApkSha256 = "target",
            targetApkSize = 60_000_000L,
            patchFilename = "Laterbox-ARM64.patch",
            patchSha256 = "abc",
            patchSize = 4_500_000L,
            patchAlgorithm = "BSDIFF40",
            downloadUrl = "https://example.com/patch"
        )

        val deltaUpdate = AppUpdateInfo(
            versionName = "1.0.35",
            releaseTitle = "Laterbox v1.0.35",
            releaseNotes = "Notes",
            downloadUrl = "https://example.com/full.apk",
            releasePageUrl = "https://example.com/release",
            apkSize = 60_000_000L,
            targetAbiLabel = "ARM64",
            assetName = "Laterbox-ARM64.apk",
            deltaPatch = patchInfo
        )

        assertTrue(deltaUpdate.isDeltaAvailable)
        assertEquals(4_500_000L, deltaUpdate.effectiveDownloadSize)

        val fullOnlyUpdate = AppUpdateInfo(
            versionName = "1.0.35",
            releaseTitle = "Laterbox v1.0.35",
            releaseNotes = "Notes",
            downloadUrl = "https://example.com/full.apk",
            releasePageUrl = "https://example.com/release",
            apkSize = 60_000_000L,
            targetAbiLabel = "ARM64",
            assetName = "Laterbox-ARM64.apk",
            deltaPatch = null
        )

        assertFalse(fullOnlyUpdate.isDeltaAvailable)
        assertEquals(60_000_000L, fullOnlyUpdate.effectiveDownloadSize)
    }
}
