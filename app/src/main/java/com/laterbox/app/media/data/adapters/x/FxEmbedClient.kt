package com.laterbox.app.media.data.adapters.x

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class FxPhoto(
    val url: String,
    val width: Int?,
    val height: Int?,
    val altText: String? = null
)

data class FxVideoFormat(
    val url: String,
    val width: Int?,
    val height: Int?,
    val bitrate: Long?
)

data class FxVideo(
    val url: String,
    val thumbnailUrl: String?,
    val width: Int?,
    val height: Int?,
    val duration: Double?,
    val format: String?,
    val formats: List<FxVideoFormat>
)

data class FxTweet(
    val id: String,
    val url: String,
    val text: String?,
    val authorName: String?,
    val authorScreenName: String?,
    val authorAvatarUrl: String?,
    val photos: List<FxPhoto>,
    val videos: List<FxVideo>
)

sealed class FxResult {
    data class Success(val tweet: FxTweet) : FxResult()
    data class NotFound(val message: String) : FxResult()
    data class PrivateContent(val message: String) : FxResult()
    data class Error(val message: String, val cause: Throwable? = null) : FxResult()
}

open class FxEmbedClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    open suspend fun getTweet(statusId: String): FxResult = withContext(Dispatchers.IO) {
        val endpoint = "https://api.fxtwitter.com/2/status/$statusId"
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", "Laterbox/1.0.27 (+https://github.com/Adetisola/Laterbox)")
            .header("Accept", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.code == 404) {
                    return@withContext FxResult.NotFound("Post was not found or has been deleted.")
                }
                if (response.code == 401 || response.code == 403) {
                    return@withContext FxResult.PrivateContent("Post is protected or requires authorization.")
                }
                if (!response.isSuccessful) {
                    return@withContext FxResult.Error("FxEmbed API HTTP error: ${response.code} ${response.message}")
                }

                if (bodyStr.isBlank()) {
                    return@withContext FxResult.Error("Empty response from FxEmbed API")
                }

                val json = JSONObject(bodyStr)
                val code = json.optInt("code", 200)
                if (code == 404) {
                    return@withContext FxResult.NotFound(json.optString("message", "Post not found"))
                }
                if (code == 401 || code == 403) {
                    return@withContext FxResult.PrivateContent(json.optString("message", "Protected post"))
                }
                if (code != 200) {
                    return@withContext FxResult.Error("FxEmbed error code $code: ${json.optString("message")}")
                }

                val tweetObj = json.optJSONObject("tweet") ?: return@withContext FxResult.Error("Missing 'tweet' object in FxEmbed response")
                val tweet = parseTweet(statusId, tweetObj)
                FxResult.Success(tweet)
            }
        } catch (e: IOException) {
            FxResult.Error("Network error contacting FxEmbed: ${e.message}", e)
        } catch (e: Exception) {
            FxResult.Error("Failed to parse FxEmbed response: ${e.message}", e)
        }
    }

    private fun parseTweet(statusId: String, obj: JSONObject): FxTweet {
        val url = obj.optString("url", "https://x.com/i/status/$statusId")
        val text = obj.optString("text").takeIf { it.isNotBlank() }
        val authorObj = obj.optJSONObject("author")
        val authorName = authorObj?.optString("name")?.takeIf { it.isNotBlank() }
        val authorScreenName = authorObj?.optString("screen_name")?.takeIf { it.isNotBlank() }
        val authorAvatar = authorObj?.optString("avatar_url")?.takeIf { it.isNotBlank() }

        val mediaObj = obj.optJSONObject("media")
        val photos = mutableListOf<FxPhoto>()
        val videos = mutableListOf<FxVideo>()

        if (mediaObj != null) {
            val photosArray = mediaObj.optJSONArray("photos")
            if (photosArray != null) {
                for (i in 0 until photosArray.length()) {
                    val p = photosArray.optJSONObject(i) ?: continue
                    val photoUrl = p.optString("url")
                    if (photoUrl.isNotBlank()) {
                        photos.add(
                            FxPhoto(
                                url = photoUrl,
                                width = p.optInt("width").takeIf { it > 0 },
                                height = p.optInt("height").takeIf { it > 0 },
                                altText = p.optString("altText").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
            }

            val videosArray = mediaObj.optJSONArray("videos")
            if (videosArray != null) {
                for (i in 0 until videosArray.length()) {
                    val v = videosArray.optJSONObject(i) ?: continue
                    val videoUrl = v.optString("url")
                    if (videoUrl.isNotBlank()) {
                        val formatList = mutableListOf<FxVideoFormat>()
                        val formatsArr = v.optJSONArray("formats")
                        if (formatsArr != null) {
                            for (j in 0 until formatsArr.length()) {
                                val fmt = formatsArr.optJSONObject(j) ?: continue
                                val fmtUrl = fmt.optString("url")
                                if (fmtUrl.isNotBlank()) {
                                    formatList.add(
                                        FxVideoFormat(
                                            url = fmtUrl,
                                            width = fmt.optInt("width").takeIf { it > 0 },
                                            height = fmt.optInt("height").takeIf { it > 0 },
                                            bitrate = fmt.optLong("bitrate").takeIf { it > 0 }
                                        )
                                    )
                                }
                            }
                        }

                        // Ensure primary video URL is included in formats if not already present
                        if (formatList.none { it.url == videoUrl }) {
                            formatList.add(
                                0,
                                FxVideoFormat(
                                    url = videoUrl,
                                    width = v.optInt("width").takeIf { it > 0 },
                                    height = v.optInt("height").takeIf { it > 0 },
                                    bitrate = null
                                )
                            )
                        }

                        // Sort formats by height desc, then bitrate desc
                        formatList.sortWith(
                            compareByDescending<FxVideoFormat> { it.height ?: 0 }
                                .thenByDescending { it.bitrate ?: 0 }
                        )

                        videos.add(
                            FxVideo(
                                url = videoUrl,
                                thumbnailUrl = v.optString("thumbnail_url").takeIf { it.isNotBlank() },
                                width = v.optInt("width").takeIf { it > 0 },
                                height = v.optInt("height").takeIf { it > 0 },
                                duration = v.optDouble("duration").takeIf { !it.isNaN() && it > 0 },
                                format = v.optString("format").takeIf { it.isNotBlank() },
                                formats = formatList
                            )
                        )
                    }
                }
            }
        }

        return FxTweet(
            id = statusId,
            url = url,
            text = text,
            authorName = authorName,
            authorScreenName = authorScreenName,
            authorAvatarUrl = authorAvatar,
            photos = photos,
            videos = videos
        )
    }
}
