package com.laterbox.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks for published updates by querying GitHub's public releases API.
 * Requires no API keys or personal access tokens.
 */
object GitHubUpdateChecker {

    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Adetisola/Laterbox/releases/latest"

    suspend fun checkForUpdate(
        currentVersionName: String,
        releasesUrl: String = LATEST_RELEASE_URL
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

            val assets = root.optJSONArray("assets") ?: org.json.JSONArray()
            var apkDownloadUrl = ""
            var apkSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name", "")
                if (assetName.equals("app-release.apk", ignoreCase = true) || assetName.endsWith(".apk", ignoreCase = true)) {
                    apkDownloadUrl = asset.optString("browser_download_url", "")
                    apkSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (apkDownloadUrl.isBlank()) {
                // Fallback to release page if no APK asset is attached directly
                apkDownloadUrl = releasePageUrl
            }

            val cleanVersion = AppUpdateInfo.cleanVersion(tagName)
            val isNewer = AppUpdateInfo.isNewerVersion(cleanVersion, currentVersionName)

            val info = AppUpdateInfo(
                versionName = cleanVersion,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                releasePageUrl = releasePageUrl,
                apkSize = apkSize,
                isNewer = isNewer
            )

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
