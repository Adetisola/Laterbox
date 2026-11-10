package com.laterbox.app.update

import android.os.Build
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Distinct possible outcomes when checking for application updates.
 */
@Keep
sealed class UpdateCheckResult {
    @Keep data class UpdateAvailable(val updateInfo: AppUpdateInfo) : UpdateCheckResult()
    @Keep data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    @Keep data class NetworkError(val message: String, val cause: Throwable? = null) : UpdateCheckResult()
    @Keep data class ApiError(val statusCode: Int, val message: String) : UpdateCheckResult()
    @Keep data class InvalidRelease(val message: String) : UpdateCheckResult()
    @Keep data class NoCompatibleApk(val releaseVersion: String, val deviceAbis: List<String>) : UpdateCheckResult()

    val isUpdateAvailable: Boolean
        get() = this is UpdateAvailable

    val updateInfoOrNull: AppUpdateInfo?
        get() = (this as? UpdateAvailable)?.updateInfo
}

/**
 * Checks for published updates by querying GitHub's public releases API.
 * Detects device ABI support and selects the optimal ABI-specific release APK
 * (ARM32 for 32-bit devices like Samsung Galaxy A02s, ARM64 for 64-bit devices,
 * or Universal as fallback).
 */
object GitHubUpdateChecker {

    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Adetisola/Laterbox/releases/latest"

    data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val size: Long
    )

    data class SelectedApkAsset(
        val asset: ReleaseAsset,
        val targetAbiLabel: String
    )

    /**
     * Inspects device supported ABIs and selects the best matching APK asset from the release.
     */
    fun selectBestApkAsset(
        assets: List<ReleaseAsset>,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
    ): SelectedApkAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        val is64BitSupported = supportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
        val isArm32Supported = supportedAbis.any { it.startsWith("armeabi", ignoreCase = true) }

        // Find candidate APKs
        val arm64Apk = apkAssets.firstOrNull {
            it.name.contains("arm64", ignoreCase = true)
        }
        val arm32Apk = apkAssets.firstOrNull {
            it.name.contains("arm32", ignoreCase = true) || it.name.contains("armeabi-v7a", ignoreCase = true)
        }
        val universalApk = apkAssets.firstOrNull {
            it.name.contains("universal", ignoreCase = true) || it.name.equals("app-release.apk", ignoreCase = true)
        }

        return when {
            // 64-bit device: Prefer ARM64 split -> Universal -> Fallback to any non-conflicting
            is64BitSupported && arm64Apk != null ->
                SelectedApkAsset(arm64Apk, "ARM64 (arm64-v8a)")

            // 32-bit only device (e.g., Galaxy A02s): Prefer ARM32 split -> Universal -> any non-arm64
            !is64BitSupported && isArm32Supported && arm32Apk != null ->
                SelectedApkAsset(arm32Apk, "ARM32 (armeabi-v7a)")

            // Universal fallback
            universalApk != null ->
                SelectedApkAsset(universalApk, "Universal")

            // If 64-bit device and no specific split name matched, accept first available apk
            is64BitSupported ->
                SelectedApkAsset(apkAssets.first(), "Universal")

            // If 32-bit device: only accept non-arm64 APKs; NEVER return an arm64-only APK to a 32-bit device
            else -> {
                val non64Apk = apkAssets.firstOrNull { !it.name.contains("arm64", ignoreCase = true) }
                if (non64Apk != null) {
                    SelectedApkAsset(non64Apk, "Universal")
                } else {
                    // No 32-bit or universal APK available in this release
                    null
                }
            }
        }
    }

    suspend fun checkForUpdate(
        currentVersionName: String,
        releasesUrl: String = LATEST_RELEASE_URL,
        deviceSupportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(releasesUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Laterbox-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                return@withContext UpdateCheckResult.ApiError(404, "No published releases found on GitHub repository.")
            }
            if (responseCode == 403) {
                return@withContext UpdateCheckResult.ApiError(403, "GitHub API rate limit exceeded. Please try again later.")
            }
            if (responseCode !in 200..299) {
                return@withContext UpdateCheckResult.ApiError(responseCode, "GitHub server returned HTTP $responseCode")
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val root = try {
                JSONObject(responseText)
            } catch (e: Exception) {
                return@withContext UpdateCheckResult.InvalidRelease("Could not parse release JSON from GitHub: ${e.message}")
            }

            val isDraft = root.optBoolean("draft", false)
            val isPrerelease = root.optBoolean("prerelease", false)
            if (isDraft || isPrerelease) {
                return@withContext UpdateCheckResult.UpToDate(currentVersionName)
            }

            val tagName = root.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                return@withContext UpdateCheckResult.InvalidRelease("GitHub release is missing tag_name.")
            }

            val cleanVersion = AppUpdateInfo.cleanVersion(tagName)
            val isNewer = AppUpdateInfo.isNewerVersion(cleanVersion, currentVersionName)
            if (!isNewer) {
                return@withContext UpdateCheckResult.UpToDate(currentVersionName)
            }

            val releaseTitle = root.optString("name", tagName)
            val releaseNotes = root.optString("body", "")
            val releasePageUrl = root.optString("html_url", "https://github.com/Adetisola/Laterbox/releases")

            val jsonAssets = root.optJSONArray("assets") ?: org.json.JSONArray()
            val parsedAssets = mutableListOf<ReleaseAsset>()

            for (i in 0 until jsonAssets.length()) {
                val assetObj = jsonAssets.optJSONObject(i) ?: continue
                val name = assetObj.optString("name", "")
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val size = assetObj.optLong("size", 0L)
                if (name.isNotBlank() && downloadUrl.isNotBlank()) {
                    parsedAssets.add(ReleaseAsset(name, downloadUrl, size))
                }
            }

            val selected = selectBestApkAsset(parsedAssets, deviceSupportedAbis)
            if (selected == null) {
                return@withContext UpdateCheckResult.NoCompatibleApk(cleanVersion, deviceSupportedAbis.toList())
            }

            val apkDownloadUrl = selected.asset.downloadUrl.ifBlank { releasePageUrl }
            val info = AppUpdateInfo(
                versionName = cleanVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                releasePageUrl = releasePageUrl,
                apkSize = selected.asset.size,
                isNewer = true,
                targetAbiLabel = selected.targetAbiLabel,
                assetName = selected.asset.name
            )

            UpdateCheckResult.UpdateAvailable(info)
        } catch (e: UnknownHostException) {
            UpdateCheckResult.NetworkError("Could not resolve host. Please check your internet connection.", e)
        } catch (e: SocketTimeoutException) {
            UpdateCheckResult.NetworkError("Update check timed out while connecting to GitHub.", e)
        } catch (e: ConnectException) {
            UpdateCheckResult.NetworkError("Failed to connect to update server.", e)
        } catch (e: IOException) {
            UpdateCheckResult.NetworkError(e.message ?: "Network error during update check.", e)
        } catch (e: Exception) {
            UpdateCheckResult.InvalidRelease("Unexpected error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }
}
