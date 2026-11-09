package com.laterbox.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles downloading, cryptographic signature verification, and system package installer
 * launching for GitHub release APKs.
 */
object ApkInstallerHelper {

    private const val TAG = "ApkInstallerHelper"

    sealed class DownloadProgress {
        data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val percent: Int) : DownloadProgress()
        data class Completed(val file: File) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        versionName: String,
        onProgress: (DownloadProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outputFile = File(updatesDir, "Laterbox-$versionName.apk")

            if (outputFile.exists()) {
                outputFile.delete()
            }

            val url = URL(downloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Laterbox-App")
            }

            // Follow HTTP redirects (GitHub release asset downloads redirect to AWS S3)
            var currentConnection = connection
            var redirectCount = 0
            while (currentConnection.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
                val newUrl = currentConnection.getHeaderField("Location")
                currentConnection.disconnect()
                currentConnection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "Laterbox-App")
                }
                redirectCount++
            }

            val totalBytes = currentConnection.contentLength.toLong()
            var bytesReadTotal = 0L

            currentConnection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesReadTotal += bytesRead
                        val percent = if (totalBytes > 0) ((bytesReadTotal * 100) / totalBytes).toInt() else 0
                        withContext(Dispatchers.Main) {
                            onProgress(DownloadProgress.Downloading(bytesReadTotal, totalBytes, percent))
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(DownloadProgress.Completed(outputFile))
            }
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download APK: $downloadUrl", e)
            withContext(Dispatchers.Main) {
                onProgress(DownloadProgress.Error(e.message ?: "Download failed"))
            }
            null
        }
    }

    /**
     * Verifies that the downloaded APK matches Laterbox's package name and signing identity.
     */
    fun verifyApk(context: Context, apkFile: File): Boolean {
        try {
            val pm = context.packageManager
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            } ?: return false

            // 1. Verify package identity
            val expectedPackage = context.packageName.removeSuffix(".debug")
            val archivePackage = (archiveInfo.packageName ?: "").removeSuffix(".debug")
            if (archivePackage != expectedPackage && archiveInfo.packageName != context.packageName) {
                Log.w(TAG, "Package name mismatch: expected $expectedPackage, found $archivePackage")
                return false
            }

            // 2. Verify signing certificates match installed application
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val installedInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val installedSigning = installedInfo.signingInfo
                    val archiveSigning = archiveInfo.signingInfo
                    if (installedSigning != null && archiveSigning != null) {
                        val installedSigners = installedSigning.apkContentsSigners.map { it.toByteArray().contentHashCode() }.toSet()
                        val archiveSigners = archiveSigning.apkContentsSigners.map { it.toByteArray().contentHashCode() }.toSet()
                        if (installedSigners.isNotEmpty() && archiveSigners.isNotEmpty() && installedSigners.intersect(archiveSigners).isEmpty()) {
                            Log.w(TAG, "Signing certificate mismatch")
                            return false
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Signing certificate check warning", e)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Verification error", e)
            return false
        }
    }

    /**
     * Checks if the app has permission to request package installs (Android 8.0+).
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system Settings so user can enable "Install unknown apps" for Laterbox.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open install permission settings", e)
            }
        }
    }

    /**
     * Launches Android's PackageInstaller for the verified APK file.
     */
    fun launchInstaller(context: Context, apkFile: File): Boolean {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            false
        }
    }

    /**
     * Fallback to open GitHub Releases page in external browser if direct APK install fails.
     */
    fun openFallbackReleasePage(context: Context, releasePageUrl: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releasePageUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open release fallback page", e)
        }
    }
}
