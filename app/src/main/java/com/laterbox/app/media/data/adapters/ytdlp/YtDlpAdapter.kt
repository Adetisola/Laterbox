package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import android.util.Log
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.data.adapters.tiktok.TikTokUrlResolver
import com.laterbox.app.media.data.diagnostics.InspectionDiagnosticEntry
import com.laterbox.app.media.data.diagnostics.InspectionDiagnosticsBuffer
import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class YtDlpAdapter(
    private val appContext: Context,
    private val runtimeManager: YtDlpRuntimeManager = (appContext.applicationContext as? LaterboxApplication)?.container?.ytDlpRuntimeManager ?: YtDlpRuntimeManager(appContext),
    private val customExecutor: ((YoutubeDLRequest) -> YoutubeDLResponse)? = null,
    private val timeoutMs: Long = DEFAULT_INSPECTION_TIMEOUT_MS,
    private val diagnosticsBuffer: InspectionDiagnosticsBuffer? = null,
    private val warmServer: WarmYtDlpServer? = null,
    private val tikTokResolver: TikTokUrlResolver = TikTokUrlResolver(),
    private val stage0Fetcher: Stage0OEmbedFetcher = Stage0OEmbedFetcher()
) : MediaPlatformAdapter {

    override val id: String = "ytdlp"
    override val inspectionTimeoutMs: Long = timeoutMs

    override fun canHandle(url: String): Boolean {
        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }

        if (host.isBlank()) return false
        // Spotify is handled by MetadataOnlyAdapter / SpotifyMusicAdapter per spec
        if (host.contains("spotify.com") || host.contains("spotify.link")) {
            return false
        }

        val scheme = try {
            URI(url).scheme?.lowercase() ?: ""
        } catch (_: Exception) {
            ""
        }

        return scheme == "http" || scheme == "https"
    }

    override suspend fun inspect(url: String): InspectionResult = inspect(url, null)

    override suspend fun inspect(url: String, processId: String?): InspectionResult =
        inspectInternal(url, processId, timeoutMs = null, stage = "single")

    suspend fun inspect(url: String, processId: String?, timeoutMs: Long?): InspectionResult =
        inspectInternal(url, processId, timeoutMs, stage = "single")

    suspend fun inspectStage0(url: String): InspectionResult.Available? = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = if (TikTokUrlResolver.isTikTokShortLink(url)) {
                tikTokResolver.resolveCanonicalUrl(url)
            } else url
            stage0Fetcher.fetch(resolvedUrl)?.let { stage0Fetcher.toInspectionResult(it) }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun inspectFast(
        url: String,
        processId: String? = null,
        timeoutMs: Long? = 15_000L
    ): InspectionResult =
        inspectInternal(url, processId, timeoutMs, stage = "stage_1_fast")

    suspend fun inspectComplete(
        url: String,
        processId: String? = null,
        timeoutMs: Long? = 60_000L,
        stage1Result: InspectionResult.Available? = null
    ): InspectionResult = withContext(Dispatchers.IO) {
        val result = inspectInternal(url, processId, timeoutMs, stage = "stage_2_complete")
        if (stage1Result != null && result is InspectionResult.Available) {
            YtDlpOutputParser.mergeResults(stage1Result, result)
        } else {
            result
        }
    }

    private suspend fun inspectInternal(
        url: String,
        processId: String?,
        timeoutMs: Long?,
        stage: String
    ): InspectionResult = withContext(Dispatchers.IO) {
        val inspectionId = processId ?: "inspect_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val startTotal = System.currentTimeMillis()

        // Resolve TikTok short link if applicable
        val canonicalUrl = if (TikTokUrlResolver.isTikTokUrl(url)) {
            tikTokResolver.resolveCanonicalUrl(url)
        } else {
            url
        }

        try {
            var runtimeReadyMs = 0L
            if (customExecutor == null) {
                val initStart = System.currentTimeMillis()
                val initResult = runtimeManager.ensureYtDlpInitialized()
                runtimeReadyMs = System.currentTimeMillis() - initStart
                if (initResult.isFailure) {
                    val ex = initResult.exceptionOrNull()
                    val errorMessage = ex?.message ?: "yt-dlp engine could not be initialized on this device"
                    diagnosticsBuffer?.record(
                        InspectionDiagnosticEntry(
                            id = inspectionId,
                            url = canonicalUrl,
                            adapterId = id,
                            stage = stage,
                            runtimeReadyMs = runtimeReadyMs,
                            totalMs = System.currentTimeMillis() - startTotal,
                            success = false,
                            error = errorMessage
                        )
                    )
                    return@withContext InspectionResult.Failed(
                        code = FailureCode.UNKNOWN,
                        message = errorMessage,
                        retryable = true
                    )
                }
            }

            val host = try { URI(canonicalUrl).host?.lowercase() ?: "" } catch (_: Exception) { "" }
            val isInstagram = host.contains("instagram.com")
            val isYouTube = host.contains("youtube.com") || host.contains("youtu.be")

            // Determine player_client based on stage
            val playerClient = when {
                !isYouTube -> null
                stage == "stage_1_fast" || stage == "single" -> "visionos;skip=hls"
                else -> "default"
            }

            val options = mutableListOf<String>().apply {
                add("--dump-single-json")
                if (!isInstagram) {
                    add("--no-playlist")
                } else {
                    add("--yes-playlist")
                }
                add("--socket-timeout=30")
                add("--cache-dir=${YtDlpOptimizer.getCacheDir(appContext).absolutePath}")
                if (playerClient != null) {
                    add("--extractor-args=youtube:player_client=$playerClient")
                }
                if (stage == "stage_2_complete" || !isYouTube) {
                    QuickJsHelper.getJsRuntimeArgument(appContext)?.let {
                        add("--js-runtimes=$it")
                    }
                }
            }

            // Execute either via warm engine daemon or standard process spawn
            var jsonOutput: String? = null
            var errorOutput: String? = null
            var warmWarnings: String? = null
            var usedWarm = false
            val execStart = System.currentTimeMillis()

            if (customExecutor == null && warmServer != null) {
                try {
                    val warmResp = warmServer.execute(
                        url = canonicalUrl,
                        options = options,
                        processId = inspectionId,
                        timeoutMs = timeoutMs ?: inspectionTimeoutMs
                    )
                    if (warmResp != null) {
                        jsonOutput = warmResp.output
                        warmWarnings = warmResp.warnings
                        usedWarm = true
                    }
                } catch (ye: YoutubeDLException) {
                    errorOutput = ye.message
                } catch (ste: java.net.SocketTimeoutException) {
                    diagnosticsBuffer?.record(
                        InspectionDiagnosticEntry(
                            id = inspectionId,
                            url = canonicalUrl,
                            adapterId = id,
                            stage = stage,
                            playerClient = playerClient,
                            runtimeReadyMs = runtimeReadyMs,
                            totalMs = System.currentTimeMillis() - startTotal,
                            isWarmProcess = true,
                            success = false,
                            error = "EXTRACTION_TIMEOUT"
                        )
                    )
                    return@withContext InspectionResult.Failed(
                        code = FailureCode.EXTRACTION_TIMEOUT,
                        message = "Media inspection timed out after ${(timeoutMs ?: inspectionTimeoutMs) / 1000} seconds.",
                        retryable = true
                    )
                } catch (te: TimeoutCancellationException) {
                    diagnosticsBuffer?.record(
                        InspectionDiagnosticEntry(
                            id = inspectionId,
                            url = canonicalUrl,
                            adapterId = id,
                            stage = stage,
                            playerClient = playerClient,
                            runtimeReadyMs = runtimeReadyMs,
                            totalMs = System.currentTimeMillis() - startTotal,
                            isWarmProcess = true,
                            success = false,
                            error = "EXTRACTION_TIMEOUT"
                        )
                    )
                    return@withContext InspectionResult.Failed(
                        code = FailureCode.EXTRACTION_TIMEOUT,
                        message = "Media inspection timed out after ${(timeoutMs ?: inspectionTimeoutMs) / 1000} seconds.",
                        retryable = true
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w("YtDlpAdapter", "Warm server execution failed: ${t.message}", t)
                }
            }

            if (jsonOutput == null && errorOutput == null) {
                // Fallback to standard process spawn
                val request = YoutubeDLRequest(canonicalUrl).apply {
                    addOption("--dump-single-json")
                    if (!isInstagram) addOption("--no-playlist") else addOption("--yes-playlist")
                    addOption("--socket-timeout", "30")
                    addOption("--cache-dir", YtDlpOptimizer.getCacheDir(appContext).absolutePath)
                    if (playerClient != null) {
                        addOption("--extractor-args", "youtube:player_client=$playerClient")
                    }
                    if (stage == "stage_2_complete" || !isYouTube) {
                        QuickJsHelper.getJsRuntimeArgument(appContext)?.let {
                            addOption("--js-runtimes", it)
                        }
                    }
                }

                val response = try {
                    executeCancellable(request, inspectionId, timeoutMs = timeoutMs)
                } catch (te: TimeoutCancellationException) {
                    diagnosticsBuffer?.record(
                        InspectionDiagnosticEntry(
                            id = inspectionId,
                            url = canonicalUrl,
                            adapterId = id,
                            stage = stage,
                            playerClient = playerClient,
                            runtimeReadyMs = runtimeReadyMs,
                            totalMs = System.currentTimeMillis() - startTotal,
                            isWarmProcess = false,
                            success = false,
                            error = "EXTRACTION_TIMEOUT"
                        )
                    )
                    return@withContext InspectionResult.Failed(
                        code = FailureCode.EXTRACTION_TIMEOUT,
                        message = "Media inspection timed out after ${(timeoutMs ?: inspectionTimeoutMs) / 1000} seconds.",
                        retryable = true
                    )
                }

                jsonOutput = response.out
                errorOutput = response.err
            }

            val ytdlpElapsedMs = System.currentTimeMillis() - execStart

            if (jsonOutput.isNullOrBlank()) {
                val err = errorOutput ?: "No metadata returned from yt-dlp"
                diagnosticsBuffer?.record(
                    InspectionDiagnosticEntry(
                        id = inspectionId,
                        url = canonicalUrl,
                        adapterId = id,
                        stage = stage,
                        playerClient = playerClient,
                        runtimeReadyMs = runtimeReadyMs,
                        ytdlpElapsedMs = ytdlpElapsedMs,
                        totalMs = System.currentTimeMillis() - startTotal,
                        isWarmProcess = usedWarm,
                        success = false,
                        error = err,
                        warnings = InspectionDiagnosticEntry.sanitizeWarnings(errorOutput)
                    )
                )
                return@withContext YtDlpOutputParser.parseError(err)
            }

            val parseStart = System.currentTimeMillis()
            var parsed = YtDlpOutputParser.parse(canonicalUrl, jsonOutput)
            val parseMs = System.currentTimeMillis() - parseStart

            if (stage == "stage_1_fast" && parsed is InspectionResult.Available) {
                parsed = parsed.copy(isComplete = false)
            }

            val videoCount = if (parsed is InspectionResult.Available) parsed.candidates.count { it.mediaType == com.laterbox.app.media.domain.model.MediaType.VIDEO } else 0
            val audioCount = if (parsed is InspectionResult.Available) parsed.candidates.count { it.mediaType == com.laterbox.app.media.domain.model.MediaType.AUDIO } else 0
            val thumbCount = if (parsed is InspectionResult.Available) parsed.candidates.count { it.isThumbnail } else 0

            diagnosticsBuffer?.record(
                InspectionDiagnosticEntry(
                    id = inspectionId,
                    url = canonicalUrl,
                    adapterId = id,
                    stage = stage,
                    playerClient = playerClient,
                    runtimeReadyMs = runtimeReadyMs,
                    processSpawnToOutputMs = ytdlpElapsedMs,
                    ytdlpElapsedMs = ytdlpElapsedMs,
                    jsonSizeBytes = jsonOutput.toByteArray().size,
                    parseMs = parseMs,
                    totalMs = System.currentTimeMillis() - startTotal,
                    videoFormatCount = videoCount,
                    audioFormatCount = audioCount,
                    thumbnailCount = thumbCount,
                    warnings = InspectionDiagnosticEntry.sanitizeWarnings(warmWarnings ?: errorOutput),
                    isWarmProcess = usedWarm,
                    success = true
                )
            )

            InfoJsonCache.save(appContext, canonicalUrl, jsonOutput)

            parsed
        } catch (e: YoutubeDLException) {
            val err = e.message ?: "yt-dlp execution error"
            diagnosticsBuffer?.record(
                InspectionDiagnosticEntry(
                    id = inspectionId,
                    url = canonicalUrl,
                    adapterId = id,
                    stage = stage,
                    totalMs = System.currentTimeMillis() - startTotal,
                    success = false,
                    error = err
                )
            )
            YtDlpOutputParser.parseError(err)
        } catch (ce: CancellationException) {
            warmServer?.cancel(inspectionId)
            throw ce
        } catch (t: Throwable) {
            diagnosticsBuffer?.record(
                InspectionDiagnosticEntry(
                    id = inspectionId,
                    url = canonicalUrl,
                    adapterId = id,
                    stage = stage,
                    totalMs = System.currentTimeMillis() - startTotal,
                    success = false,
                    error = t.message
                )
            )
            InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = t.localizedMessage ?: "Failed to inspect URL",
                retryable = true
            )
        }
    }

    private suspend fun executeCancellable(
        request: YoutubeDLRequest,
        processId: String,
        timeoutMs: Long? = null
    ): YoutubeDLResponse = coroutineScope {
        val isCancelled = AtomicBoolean(false)
        val deferred = async(Dispatchers.IO) {
            if (isCancelled.get() || cancelledProcessIds.contains(processId)) {
                throw CancellationException("Process $processId was cancelled prior to execution")
            }
            if (customExecutor != null) {
                return@async customExecutor.invoke(request)
            }
            try {
                YoutubeDL.getInstance().execute(request, processId)
            } catch (ce: YoutubeDL.CanceledException) {
                throw CancellationException("yt-dlp process was cancelled", ce)
            }
        }

        val cleanup = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                isCancelled.set(true)
                cancelledProcessIds.add(processId)
                try {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                } catch (_: Throwable) {
                }
            }
        }

        try {
            if (timeoutMs != null) {
                withTimeout(timeoutMs) {
                    deferred.await()
                }
            } else {
                deferred.await()
            }
        } catch (t: Throwable) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Throwable) {
            }
            deferred.cancel()
            throw t
        } finally {
            cleanup?.dispose()
            cancelledProcessIds.remove(processId)
        }
    }

    companion object {
        const val DEFAULT_INSPECTION_TIMEOUT_MS = 90_000L
        private val cancelledProcessIds = ConcurrentHashMap.newKeySet<String>()
    }
}
