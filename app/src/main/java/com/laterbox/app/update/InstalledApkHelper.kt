package com.laterbox.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Utility for safely inspecting the currently installed application package, its APK file,
 * cryptographic signing certificate digests, and version metadata.
 */
object InstalledApkHelper {

    private const val TAG = "InstalledApkHelper"

    /**
     * Retrieves the File handle for the currently installed application APK.
     * Note: Android mounts the installed APK in read-only mode at [Context.getApplicationInfo().sourceDir].
     * The original APK is never modified or moved.
     */
    fun getInstalledApkFile(context: Context): File? {
        return try {
            val sourceDir = context.applicationInfo?.sourceDir
            if (!sourceDir.isNullOrBlank()) {
                val file = File(sourceDir)
                if (file.exists() && file.isFile && file.canRead()) {
                    file
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve installed APK file: ${e.message}")
            null
        }
    }

    /**
     * Computes the SHA-256 digest of a file in 64 KB streaming chunks to avoid loading
     * entire files into memory.
     */
    @Throws(IOException::class)
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.lowercase()
    }

    /**
     * Computes the SHA-256 digest of the currently installed APK, or null if unreadable.
     */
    fun getInstalledApkSha256(context: Context): String? {
        val file = getInstalledApkFile(context) ?: return null
        return try {
            computeSha256(file)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute installed APK SHA-256: ${e.message}")
            null
        }
    }

    /**
     * Retrieves the installed application version code safely across all Android versions.
     */
    fun getInstalledVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Computes the cryptographic SHA-256 hex digest of an X.509 signing certificate byte array.
     */
    fun computeCertSha256(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(certBytes).joinToString("") { "%02x".format(it) }.lowercase()
    }

    /**
     * Extracts the set of cryptographic SHA-256 digests for all active signing certificates
     * of the currently running application.
     */
    fun getInstalledSignersSha256(context: Context): Set<String> {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                pInfo.signingInfo?.apkContentsSigners?.map { computeCertSha256(it.toByteArray()) }?.toSet() ?: emptySet()
            } else {
                @Suppress("DEPRECATION")
                val pInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pInfo.signatures?.map { computeCertSha256(it.toByteArray()) }?.toSet() ?: emptySet()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract installed signing certificates: ${e.message}")
            emptySet()
        }
    }

    /**
     * Extracts the set of cryptographic SHA-256 digests for all signing certificates of an uninstalled APK archive.
     */
    fun getArchiveSignersSha256(pm: PackageManager, archiveFile: File, archiveInfo: PackageInfo): Set<String> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && archiveInfo.signingInfo != null) {
                archiveInfo.signingInfo?.apkContentsSigners?.map { computeCertSha256(it.toByteArray()) }?.toSet() ?: emptySet()
            } else {
                @Suppress("DEPRECATION")
                val sigs = archiveInfo.signatures
                    ?: pm.getPackageArchiveInfo(archiveFile.absolutePath, PackageManager.GET_SIGNATURES)?.signatures
                @Suppress("DEPRECATION")
                sigs?.map { computeCertSha256(it.toByteArray()) }?.toSet() ?: emptySet()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract archive signing certificates: ${e.message}")
            emptySet()
        }
    }
}
