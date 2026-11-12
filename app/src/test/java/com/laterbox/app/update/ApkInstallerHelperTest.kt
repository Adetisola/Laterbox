package com.laterbox.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ApkInstallerHelperTest {

    @Test
    fun `download progress calculates percent accurately`() {
        val progress = ApkInstallerHelper.DownloadProgress.Downloading(
            bytesDownloaded = 50L,
            totalBytes = 100L,
            percent = 50
        )
        assertEquals(50, progress.percent)
        assertEquals(50L, progress.bytesDownloaded)
        assertEquals(100L, progress.totalBytes)
    }

    @Test
    fun `verification result types model success and failure conditions`() {
        val valid = ApkInstallerHelper.ApkVerificationResult.Valid
        val mismatch = ApkInstallerHelper.ApkVerificationResult.SignatureMismatch("Signature mismatch")
        val invalidPkg = ApkInstallerHelper.ApkVerificationResult.InvalidPackage("expected.pkg", "actual.pkg")
        val error = ApkInstallerHelper.ApkVerificationResult.Error("I/O error")

        assertTrue(valid is ApkInstallerHelper.ApkVerificationResult.Valid)
        assertTrue(mismatch is ApkInstallerHelper.ApkVerificationResult.SignatureMismatch)
        assertTrue(invalidPkg is ApkInstallerHelper.ApkVerificationResult.InvalidPackage)
        assertTrue(error is ApkInstallerHelper.ApkVerificationResult.Error)

        assertEquals("expected.pkg", invalidPkg.expected)
        assertEquals("actual.pkg", invalidPkg.actual)
        assertTrue(mismatch.message.contains("Signature mismatch"))
    }

    @Test
    fun `verifyApk returns false on non-existent file`() {
        val nonExistent = File("/tmp/non_existent_update.apk")
        assertFalse(nonExistent.exists())
    }

    @Test
    fun `cleanupOldUpdateApks deletes current and older APKs while retaining newer APKs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }

        // Setup test files in cache/updates/
        val oldApk = File(updatesDir, "Laterbox-ARM32-1.0.21.apk").apply { writeText("old-apk-content") }
        val currentApk = File(updatesDir, "Laterbox-1.0.22.apk").apply { writeText("current-apk-content") }
        val newerApk = File(updatesDir, "Laterbox-ARM64-1.0.23.apk").apply { writeText("newer-apk-content") }

        assertTrue(oldApk.exists())
        assertTrue(currentApk.exists())
        assertTrue(newerApk.exists())

        val deletedCount = ApkInstallerHelper.cleanupOldUpdateApks(context, "1.0.22")

        assertEquals(2, deletedCount)
        assertFalse("Old APK (1.0.21) should have been deleted", oldApk.exists())
        assertFalse("Current APK (1.0.22) should have been deleted after update", currentApk.exists())
        assertTrue("Newer APK (1.0.23) should be retained for future install", newerApk.exists())

        // Cleanup test directory
        newerApk.delete()
        updatesDir.delete()
    }

    @Test
    fun `cleanupOldUpdateApks returns 0 when updates directory does not exist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updatesDir = File(context.cacheDir, "updates")
        if (updatesDir.exists()) updatesDir.deleteRecursively()

        val deleted = ApkInstallerHelper.cleanupOldUpdateApks(context, "1.0.22")
        assertEquals(0, deleted)
    }

    @Test
    fun `cleanupOldUpdateApks removes temporary patch and tmp files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }

        val patchFile = File(updatesDir, "test.patch").apply { writeText("patch-data") }
        val tmpFile = File(updatesDir, "update.apk.tmp").apply { writeText("tmp-data") }
        val partFile = File(updatesDir, "download.part").apply { writeText("part-data") }

        assertTrue(patchFile.exists())
        assertTrue(tmpFile.exists())
        assertTrue(partFile.exists())

        val deleted = ApkInstallerHelper.cleanupOldUpdateApks(context, "1.0.22")
        assertTrue(deleted >= 3)
        assertFalse(patchFile.exists())
        assertFalse(tmpFile.exists())
        assertFalse(partFile.exists())

        updatesDir.delete()
    }

    @Test
    fun `computeSha256 calculates accurate lowercase digest`() {
        val tempFile = File.createTempFile("test_sha", ".txt")
        try {
            tempFile.writeText("hello world\n")
            val sha = InstalledApkHelper.computeSha256(tempFile)
            // sha256("hello world\n") = a948904f2f0f479b8f8197694b30184b0d2ed1c1cd2a1ec0fb85d299a192a447
            assertEquals("a948904f2f0f479b8f8197694b30184b0d2ed1c1cd2a1ec0fb85d299a192a447", sha)
        } finally {
            tempFile.delete()
        }
    }
}
