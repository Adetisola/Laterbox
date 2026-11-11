package com.laterbox.app.media.data.adapters.spotify

import android.content.Context
import android.util.Log
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import com.laterbox.app.media.domain.matching.MusicDiscoveryProvider
import com.laterbox.app.media.domain.matching.RawMusicCandidate
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MusicDiscoveryProviderImpl(
    private val appContext: Context,
    private val runtimeManager: YtDlpRuntimeManager = (appContext.applicationContext as? LaterboxApplication)?.container?.ytDlpRuntimeManager ?: YtDlpRuntimeManager(appContext),
    private val customExecutor: ((YoutubeDLRequest) -> YoutubeDLResponse)? = null
) : MusicDiscoveryProvider {

    override suspend fun discoverCandidates(spotify: SpotifyTrackMetadata): List<RawMusicCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<RawMusicCandidate>()

        // 1. If 30s official public preview URL is present from Spotify embed, include it
        if (!spotify.audioPreviewUrl.isNullOrBlank()) {
            candidates.add(
                RawMusicCandidate(
                    id = "spotify_preview_${spotify.trackId}",
                    title = "${spotify.title} (Official 30s Preview)",
                    artist = spotify.primaryArtist,
                    durationMs = 30000L,
                    downloadCandidate = MediaCandidate(
                        candidateId = "spotify_preview_${spotify.trackId}",
                        mediaType = MediaType.AUDIO,
                        formatLabel = "Preview Audio (mp3)",
                        estimatedBytes = 480000L,
                        durationMs = 30000L,
                        width = null,
                        height = null,
                        source = AcquisitionSource.DirectHttp(spotify.audioPreviewUrl)
                    ),
                    sourceDescription = "Spotify Public Preview Stream"
                )
            )
        }

        // 2. Discover audio recordings from open audio repositories via search
        try {
            if (customExecutor == null) {
                val initResult = runtimeManager.ensureYtDlpInitialized()
                if (initResult.isFailure) {
                    Log.w("MusicDiscoveryProvider", "yt-dlp not available for discovery: ${initResult.exceptionOrNull()?.message}")
                    return@withContext candidates
                }
            }

            val sanitizedArtist = spotify.primaryArtist.replace("\"", "").replace("'", "")
            val sanitizedTitle = spotify.title.replace("\"", "").replace("'", "")
            val query = "ytsearch5:$sanitizedArtist $sanitizedTitle".trim()

            val searchId = "music_search_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}"
            val request = YoutubeDLRequest(query).apply {
                addOption("--dump-single-json")
                addOption("--flat-playlist")
                addOption("--no-playlist")
                addOption("--socket-timeout", "15")
                addOption("--no-warnings")
                addOption("--extractor-args", "youtube:player_client=android,web,web_creator")
            }

            val response = try {
                kotlinx.coroutines.withTimeout(25_000L) {
                    customExecutor?.invoke(request) ?: YoutubeDL.getInstance().execute(request, searchId)
                }
            } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(searchId)
                    } catch (_: Exception) {}
                }
                Log.w("MusicDiscoveryProvider", "Music candidate discovery timed out")
                return@withContext candidates
            } catch (ce: kotlinx.coroutines.CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(searchId)
                    } catch (_: Exception) {}
                }
                throw ce
            }
            val jsonStr = response.out
            if (!jsonStr.isNullOrBlank()) {
                val root = JSONObject(jsonStr)
                val entries = root.optJSONArray("entries")
                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val entry = entries.optJSONObject(i) ?: continue
                        val id = entry.optString("id").ifBlank { null } ?: continue
                        val title = entry.optString("title").ifBlank { "${spotify.primaryArtist} - ${spotify.title}" }
                        val uploader = entry.optString("uploader").ifBlank {
                            entry.optString("channel").ifBlank { spotify.primaryArtist }
                        }
                        val durationSec = entry.optDouble("duration", 0.0)
                        val durationMs = if (durationSec > 0) (durationSec * 1000).toLong() else null

                        val candidateUrl = entry.optString("url").ifBlank {
                            "https://www.youtube.com/watch?v=$id"
                        }

                        candidates.add(
                            RawMusicCandidate(
                                id = "stream_$id",
                                title = title,
                                artist = uploader,
                                durationMs = durationMs,
                                downloadCandidate = MediaCandidate(
                                    candidateId = "stream_audio_$id",
                                    mediaType = MediaType.AUDIO,
                                    formatLabel = "Audio (m4a)",
                                    estimatedBytes = null,
                                    durationMs = durationMs,
                                    width = null,
                                    height = null,
                                    source = AcquisitionSource.YtDlp(
                                        url = candidateUrl,
                                        formatSelector = "bestaudio[ext=m4a]/bestaudio/best",
                                        expectedExt = "m4a"
                                    )
                                ),
                                sourceDescription = "Open Audio Stream"
                            )
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w("MusicDiscoveryProvider", "Failed to query candidates: ${t.message}", t)
        }

        candidates
    }
}
