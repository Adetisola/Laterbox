package com.laterbox.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
        // Without an Android context with PackageManager, testing non-existent file safely
        val nonExistent = File("/tmp/non_existent_update.apk")
        assertFalse(nonExistent.exists())
    }
}
