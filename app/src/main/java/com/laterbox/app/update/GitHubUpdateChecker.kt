package com.laterbox.app.update

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
            // 64-bit device: Prefer ARM64 split -> Universal -> Fallback
            is64BitSupported && arm64Apk != null ->
                SelectedApkAsset(arm64Apk, "ARM64 (arm64-v8a)")

            // 32-bit only device (e.g., Galaxy A02s): Prefer ARM32 split -> Universal
            !is64BitSupported && isArm32Supported && arm32Apk != null ->
                SelectedApkAsset(arm32Apk, "ARM32 (armeabi-v7a)")

            // Universal fallback
            universalApk != null ->
                SelectedApkAsset(universalApk, "Universal")

            // If 64-bit device and no specific match found, pick first available
            is64BitSupported ->
                SelectedApkAsset(apkAssets.first(), "Universal")

            // If 32-bit device, pick any non-arm64 APK to avoid incompatible binary
            else -> {
                val non64Apk = apkAssets.firstOrNull { !it.name.contains("arm64", ignoreCase = true) }
                if (non64Apk != null) {
                    SelectedApkAsset(non64Apk, "Universal")
                } else {
                    SelectedApkAsset(apkAssets.first(), "Universal")
                }
            }
        }
    }

    suspend fun checkForUpdate(
        currentVersionName: String,
        releasesUrl: String = LATEST_RELEASE_URL,
        deviceSupportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray()
    ): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(releasesUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Laterbox-App")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("GitHub API returned HTTP $responseCode")
                )
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)

            val isDraft = root.optBoolean("draft", false)
            val isPrerelease = root.optBoolean("prerelease", false)
            if (isDraft || isPrerelease) {
                return@withContext Result.success(null)
            }

            val tagName = root.optString("tag_name", "")
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
            val apkDownloadUrl = selected?.asset?.downloadUrl?.ifBlank { releasePageUrl } ?: releasePageUrl
            val apkSize = selected?.asset?.size ?: 0L
            val assetName = selected?.asset?.name ?: ""
            val targetAbiLabel = selected?.targetAbiLabel ?: "Universal"

            val cleanVersion = AppUpdateInfo.cleanVersion(tagName)
            val isNewer = AppUpdateInfo.isNewerVersion(cleanVersion, currentVersionName)

            val info = AppUpdateInfo(
                versionName = cleanVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                releasePageUrl = releasePageUrl,
                apkSize = apkSize,
                isNewer = isNewer,
                targetAbiLabel = targetAbiLabel,
                assetName = assetName
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
