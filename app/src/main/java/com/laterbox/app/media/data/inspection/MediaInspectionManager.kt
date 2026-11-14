package com.laterbox.app.media.data.inspection

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.media.data.adapters.InspectionResultJsonAdapter
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpOutputParser
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.data.db.entity.MediaInspectionEntity
import com.laterbox.app.media.domain.adapter.PlatformResolver
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class MediaInspectionManager(
    private val appContext: Context,
    private val inspectionDao: MediaInspectionDao,
    private val platformResolver: PlatformResolver,
    private val warmServer: com.laterbox.app.media.data.adapters.ytdlp.WarmYtDlpServer? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val tag = "MediaInspectionManager"
    private val inspectionMutexes = ConcurrentHashMap<Long, Mutex>()

    companion object {
        val ACTIVE_STATUSES = setOf("PREPARING", "INSPECTING", "PROCESSING")
        val DEFERRED_STATUSES = setOf("PENDING", "QUEUED", "WAITING_FOR_NETWORK", "DEFERRED")
    }

    fun isInProcessActive(savedLinkId: Long): Boolean =
        activeInspections[savedLinkId]?.job?.isActive == true
    private val activeGenerations = ConcurrentHashMap<Long, Long>()

    private data class ActiveInspection(
        val generation: Long,
        val processId: String,
        val job: Job
    )
    private val activeInspections = ConcurrentHashMap<Long, ActiveInspection>()

    fun getInspectionResultForLink(savedLinkId: Long): Flow<InspectionResult?> {
        return inspectionDao.getInspectionForLink(savedLinkId).map { entity ->
            if (entity == null) return@map null
            when (entity.status) {
                "COMPLETED" -> {
                    entity.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                }
                "PARTIAL", "INSPECTING", "PROCESSING", "PREPARING" -> {
                    val fromJson = entity.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                    if (fromJson is InspectionResult.Available) {
                        fromJson.copy(isComplete = false)
                    } else {
                        fromJson
                    }
                }
                "FAILED" -> {
                    val fromJson = entity.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                    if (fromJson is InspectionResult.Failed) {
                        fromJson
                    } else {
                        val code = entity.errorCode?.let {
                            try { FailureCode.valueOf(it) } catch (_: Exception) { FailureCode.UNKNOWN }
                        } ?: FailureCode.UNKNOWN
                        InspectionResult.Failed(
                            code = code,
                            message = entity.errorMessage,
                            retryable = code.isRetryable
                        )
                    }
                }
                "NEEDS_AUTH" -> {
                    val fromJson = entity.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                    if (fromJson is InspectionResult.NeedsAuth) {
                        fromJson
                    } else {
                        InspectionResult.NeedsAuth(
                            reason = entity.errorMessage ?: "Authentication required"
                        )
                    }
                }
                "UNSUPPORTED" -> {
                    val fromJson = entity.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                    if (fromJson is InspectionResult.Unsupported) {
                        fromJson
                    } else {
                        InspectionResult.Unsupported(
                            reason = entity.errorMessage ?: "Unsupported media"
                        )
                    }
                }
                "WAITING_FOR_NETWORK" -> InspectionResult.Failed(
                    code = FailureCode.NETWORK_ERROR,
                    message = entity.errorMessage ?: "Waiting for internet connection. Tap Retry to check now.",
                    retryable = true
                )
                "DEFERRED", "QUEUED", "PENDING" -> InspectionResult.Failed(
                    code = FailureCode.UNKNOWN,
                    message = entity.errorMessage ?: "Media check queued in background. Tap Retry to run now.",
                    retryable = true
                )
                else -> null
            }
        }
    }

    fun isInspectingFlow(savedLinkId: Long): Flow<Boolean> {
        return inspectionDao.getInspectionForLink(savedLinkId).map { entity ->
            entity != null && entity.status in ACTIVE_STATUSES
        }
    }

    /**
     * Executes inspection directly. Used by MediaInspectionWorker and direct coroutine callers.
     * Handles cache TTL, URL invalidation, error capture, and OpenGraph fallbacks.
     */
    /**
     * Executes inspection directly. Used by MediaInspectionWorker and direct coroutine callers.
     * Handles cache TTL, URL invalidation, error capture, and OpenGraph fallbacks.
     */
    suspend fun executeInspection(
        savedLinkId: Long,
        url: String,
        previewImageUrl: String? = null,
        forceRefresh: Boolean = false,
        explicitGeneration: Long? = null,
        explicitProcessId: String? = null,
        notifyOnTerminalFailure: Boolean = false
    ): Result<InspectionResult> = withContext(Dispatchers.IO) {
        val running = activeInspections[savedLinkId]
        if (!forceRefresh && running != null && running.job.isActive && running.generation != explicitGeneration) {
            Log.i(tag, "Skipping duplicate inspection for link $savedLinkId while generation ${running.generation} is actively running")
            return@withContext Result.failure(kotlinx.coroutines.CancellationException("Already inspecting"))
        }

        val generation = explicitGeneration ?: System.currentTimeMillis()
        val inspectionProcessId = explicitProcessId ?: "inspect_${savedLinkId}_g$generation"
        activeGenerations[savedLinkId] = generation

        // When force-refreshing, terminate any prior in-flight process and job for this link
        if (forceRefresh) {
            val active = activeInspections[savedLinkId]
            if (active != null && active.generation != generation) {
                active.job.cancel()
                try {
                    YoutubeDL.getInstance().destroyProcessById(active.processId)
                } catch (_: Throwable) {}
                warmServer?.cancel(active.processId)
            }
            try {
                YoutubeDL.getInstance().destroyProcessById("inspect_$savedLinkId")
                YoutubeDL.getInstance().destroyProcessById(inspectionProcessId)
            } catch (_: Throwable) {}
        }

        val existing = inspectionDao.getEntityForLink(savedLinkId)
        val now = System.currentTimeMillis()

        // Invalidate cache if saved link URL has changed or forceRefresh is requested
        if (existing != null && existing.url != url) {
            com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.invalidate(appContext, existing.url, savedLinkId)
            com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.invalidate(appContext, url, savedLinkId)
            inspectionDao.deleteForLink(savedLinkId)
        } else if (forceRefresh) {
            com.laterbox.app.media.data.adapters.ytdlp.InfoJsonCache.invalidate(appContext, url, savedLinkId)
        } else if (existing != null && existing.status == "COMPLETED" && (now - existing.updatedAt < 2 * 3600 * 1000L)) {
            val cached = existing.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
            if (cached != null) return@withContext Result.success(cached)
        }

        // Lookup preview image at inspection time from database if not passed
        val resolvedPreviewImageUrl = previewImageUrl ?: lookupPreviewImage(savedLinkId)

        // IMMEDIATELY write INSPECTING row to Room before waiting for the mutex
        val inFlightEntity = MediaInspectionEntity(
            savedLinkId = savedLinkId,
            id = inspectionProcessId,
            generation = generation,
            url = url,
            status = "INSPECTING",
            resultJson = if (forceRefresh) null else existing?.resultJson,
            errorCode = null,
            errorMessage = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        inspectionDao.insert(inFlightEntity)

        val mutex = inspectionMutexes.computeIfAbsent(savedLinkId) { Mutex() }
        mutex.withLock {
            // Guard: if superseded while waiting for mutex lock, abort
            if (activeGenerations[savedLinkId] != generation) {
                Log.i(tag, "Superseded before lock acquisition for link $savedLinkId (current=${activeGenerations[savedLinkId]}, this=$generation)")
                return@withContext Result.failure(kotlinx.coroutines.CancellationException("Superseded by newer generation"))
            }
            val checkCancelledBeforeRun = inspectionDao.getEntityForLink(savedLinkId)
            if (checkCancelledBeforeRun != null && checkCancelledBeforeRun.status == "CANCELLED" && checkCancelledBeforeRun.generation >= generation) {
                return@withContext Result.failure(kotlinx.coroutines.CancellationException("Inspection was cancelled"))
            }

            // Stage 0: Instant oEmbed / OpenGraph inspection (< 1s)
            val stage0Fetcher = com.laterbox.app.media.data.adapters.ytdlp.Stage0OEmbedFetcher()
            try {
                val stage0Result = stage0Fetcher.fetch(url)?.let { stage0Fetcher.toInspectionResult(it) }
                if (stage0Result != null && activeGenerations[savedLinkId] == generation) {
                    val stage0Json = InspectionResultJsonAdapter.toJson(stage0Result)
                    inspectionDao.insert(
                        inFlightEntity.copy(
                            status = "INSPECTING",
                            resultJson = stage0Json,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (_: Throwable) {}

            val isYouTube = try {
                val host = java.net.URI(url).host?.lowercase() ?: ""
                host.contains("youtube.com") || host.contains("youtu.be")
            } catch (_: Throwable) { false }

            val ytdlpAdapter = platformResolver.registry.getAllAdapters()
                .filterIsInstance<com.laterbox.app.media.data.adapters.ytdlp.YtDlpAdapter>()
                .firstOrNull()

            try {
                val overallBudgetMs = 90_000L
                val overallStart = android.os.SystemClock.elapsedRealtime()
                val overallDeadline = overallStart + overallBudgetMs

                fun remainingBudget(): Long = (overallDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)

                var result: InspectionResult
                if (isYouTube && ytdlpAdapter != null) {
                    // Staged execution for YouTube:
                    // Stage 1: Fast format discovery (up to 15 seconds from overall budget)
                    val stage1Budget = minOf(15_000L, remainingBudget())
                    var stage1Result: InspectionResult? = null

                    if (stage1Budget > 1_000L) {
                        try {
                            stage1Result = ytdlpAdapter.inspectFast(url, inspectionProcessId, timeoutMs = stage1Budget)
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Log.w(tag, "Stage 1 fast format discovery failed: ${t.message}", t)
                        }
                    }

                    if (stage1Result is InspectionResult.Available && activeGenerations[savedLinkId] == generation) {
                        val currentCheck = inspectionDao.getEntityForLink(savedLinkId)
                        if (currentCheck == null || currentCheck.status != "CANCELLED" || currentCheck.generation < generation) {
                            val stage1WithPreview = YtDlpOutputParser.attachPreviewImageFallback(stage1Result, resolvedPreviewImageUrl)
                            inspectionDao.insert(
                                inFlightEntity.copy(
                                    status = "INSPECTING",
                                    resultJson = InspectionResultJsonAdapter.toJson(stage1WithPreview),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }

                    // Stage 2: Complete extraction using remaining overall time
                    val remainingForStage2 = remainingBudget()
                    if (remainingForStage2 > 1_000L) {
                        try {
                            val stage2Result = ytdlpAdapter.inspectComplete(
                                url = url,
                                processId = inspectionProcessId,
                                timeoutMs = remainingForStage2,
                                stage1Result = if (stage1Result is InspectionResult.Available) stage1Result else null
                            )
                            if (stage2Result is InspectionResult.Available) {
                                result = stage2Result
                            } else {
                                if (stage1Result is InspectionResult.Available) {
                                    // Retain Stage 1 candidates as genuinely partial results
                                    result = stage1Result.copy(isComplete = false)
                                } else {
                                    result = stage2Result
                                }
                            }
                        } catch (ce: kotlinx.coroutines.CancellationException) {
                            throw ce
                        } catch (t: Throwable) {
                            Log.w(tag, "Stage 2 complete extraction failed: ${t.message}", t)
                            if (stage1Result is InspectionResult.Available) {
                                result = stage1Result.copy(isComplete = false)
                            } else {
                                result = InspectionResult.Failed(
                                    code = FailureCode.EXTRACTION_TIMEOUT,
                                    message = "YouTube media extraction timed out after 90 seconds",
                                    retryable = true
                                )
                            }
                        }
                    } else {
                        // Overall deadline expired
                        if (stage1Result is InspectionResult.Available) {
                            result = stage1Result.copy(isComplete = false)
                        } else {
                            result = InspectionResult.Failed(
                                code = FailureCode.EXTRACTION_TIMEOUT,
                                message = "YouTube media extraction timed out after 90 seconds",
                                retryable = true
                            )
                        }
                    }
                } else {
                    result = platformResolver.resolveAndInspect(url, inspectionProcessId)
                }

                result = YtDlpOutputParser.attachPreviewImageFallback(result, resolvedPreviewImageUrl)

                // Guard completion writes: verify generation matches AND DB entity was not marked CANCELLED
                if (activeGenerations[savedLinkId] != generation) {
                    Log.i(tag, "Discarding stale inspection result for link $savedLinkId (current gen=${activeGenerations[savedLinkId]}, this gen=$generation)")
                    return@withContext Result.success(result)
                }
                val checkCancelled = inspectionDao.getEntityForLink(savedLinkId)
                if (checkCancelled != null && checkCancelled.status == "CANCELLED" && checkCancelled.generation >= generation) {
                    Log.i(tag, "Inspection for link $savedLinkId was cancelled; ignoring completion write")
                    return@withContext Result.success(result)
                }

                val json = InspectionResultJsonAdapter.toJson(result)
                val (status, errCode, errMsg) = when (result) {
                    is InspectionResult.Failed -> Triple("FAILED", result.code.name, result.message)
                    is InspectionResult.NeedsAuth -> Triple("NEEDS_AUTH", null, result.reason)
                    is InspectionResult.Unsupported -> Triple("UNSUPPORTED", null, result.reason)
                    is InspectionResult.Available -> {
                        if (!result.isComplete) {
                            Triple("PARTIAL", null, null)
                        } else {
                            Triple("COMPLETED", null, null)
                        }
                    }
                    else -> Triple("COMPLETED", null, null)
                }

                val completedEntity = MediaInspectionEntity(
                    savedLinkId = savedLinkId,
                    id = inspectionProcessId,
                    generation = generation,
                    url = url,
                    status = status,
                    resultJson = json,
                    errorCode = errCode,
                    errorMessage = errMsg,
                    createdAt = inFlightEntity.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                inspectionDao.insert(completedEntity)
                val app = appContext as? LaterboxApplication
                if (status == "COMPLETED") {
                    try {
                        app?.container?.activityEventRepository?.recordEvent(
                            type = com.laterbox.app.data.repository.ActivityEventType.INSPECTION_COMPLETED,
                            title = "Media inspected for link #$savedLinkId",
                            detail = url,
                            linkId = savedLinkId
                        )
                    } catch (_: Throwable) {}
                } else if (status in setOf("FAILED", "NEEDS_AUTH") && notifyOnTerminalFailure) {
                    try {
                        val linkTitle = app?.container?.database?.savedLinkDao()?.getEntityById(savedLinkId)?.title ?: url
                        app?.container?.activityEventRepository?.recordEvent(
                            type = com.laterbox.app.data.repository.ActivityEventType.INSPECTION_FAILED,
                            title = "Media inspection failed: $linkTitle",
                            detail = errMsg,
                            linkId = savedLinkId
                        )
                        app?.container?.contextualNotificationEngine?.notifyExtractionFailure(
                            savedLinkId = savedLinkId,
                            url = url,
                            linkTitle = linkTitle,
                            errorMessage = errMsg ?: "Media inspection failed",
                            dedupKey = "inspect_${savedLinkId}_g$generation"
                        )
                    } catch (_: Throwable) {}
                }
                Result.success(result)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                withContext(NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(inspectionProcessId)
                        warmServer?.cancel(inspectionProcessId)
                    } catch (_: Throwable) {}
                    if (activeGenerations[savedLinkId] == generation) {
                        val currentEntity = inspectionDao.getEntityForLink(savedLinkId)
                        if (currentEntity != null && currentEntity.status in setOf("INSPECTING", "PARTIAL")) {
                            inspectionDao.insert(
                                currentEntity.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis())
                            )
                        }
                    }
                }
                throw ce
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(inspectionProcessId)
                        warmServer?.cancel(inspectionProcessId)
                    } catch (_: Throwable) {}

                    if (activeGenerations[savedLinkId] != generation) {
                        return@withContext
                    }
                    val currentEntity = inspectionDao.getEntityForLink(savedLinkId)
                    if (currentEntity != null && currentEntity.status == "CANCELLED" && currentEntity.generation >= generation) {
                        return@withContext
                    }

                    val failedResult = InspectionResult.Failed(
                        code = FailureCode.UNKNOWN,
                        message = t.localizedMessage ?: "Failed to inspect media",
                        retryable = true
                    )
                    val fallbackResult = YtDlpOutputParser.attachPreviewImageFallback(failedResult, resolvedPreviewImageUrl)
                    val json = InspectionResultJsonAdapter.toJson(fallbackResult)
                    val (status, errCode, errMsg) = when (fallbackResult) {
                        is InspectionResult.Failed -> Triple("FAILED", fallbackResult.code.name, fallbackResult.message)
                        is InspectionResult.NeedsAuth -> Triple("NEEDS_AUTH", null, fallbackResult.reason)
                        is InspectionResult.Unsupported -> Triple("UNSUPPORTED", null, fallbackResult.reason)
                        else -> Triple("COMPLETED", null, null)
                    }

                    val entity = MediaInspectionEntity(
                        savedLinkId = savedLinkId,
                        id = inspectionProcessId,
                        generation = generation,
                        url = url,
                        status = status,
                        resultJson = json,
                        errorCode = errCode,
                        errorMessage = errMsg,
                        createdAt = inFlightEntity.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                    inspectionDao.insert(entity)
                    if (notifyOnTerminalFailure) {
                        try {
                            val app = appContext as? LaterboxApplication
                            val linkTitle = app?.container?.database?.savedLinkDao()?.getEntityById(savedLinkId)?.title ?: url
                            app?.container?.activityEventRepository?.recordEvent(
                                type = com.laterbox.app.data.repository.ActivityEventType.INSPECTION_FAILED,
                                title = "Media inspection failed: $linkTitle",
                                detail = errMsg,
                                linkId = savedLinkId
                            )
                            app?.container?.contextualNotificationEngine?.notifyExtractionFailure(
                                savedLinkId = savedLinkId,
                                url = url,
                                linkTitle = linkTitle,
                                errorMessage = errMsg ?: "Media inspection failed",
                                dedupKey = "inspect_${savedLinkId}_g$generation"
                            )
                        } catch (_: Throwable) {}
                    }
                }
                Result.failure(t)
            }
        }
    }

    private suspend fun lookupPreviewImage(savedLinkId: Long): String? {
        return try {
            val app = appContext as? LaterboxApplication
            val db = app?.container?.database ?: com.laterbox.app.data.local.LaterboxDatabase.getInstance(appContext)
            db.savedLinkDao().getEntityById(savedLinkId)?.previewImageUrl
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Marks an inspection as deferred/queued with a non-spinning status so the UI never hangs
     * on an infinite loading spinner while WorkManager waits for network, memory, or backoff.
     */
    suspend fun markInspectionDeferred(
        savedLinkId: Long,
        url: String,
        status: String = "QUEUED",
        reason: String? = null
    ) = withContext(Dispatchers.IO) {
        if (isInProcessActive(savedLinkId)) return@withContext
        val existing = inspectionDao.getEntityForLink(savedLinkId)
        val now = System.currentTimeMillis()
        val gen = activeGenerations[savedLinkId] ?: now
        val entity = MediaInspectionEntity(
            savedLinkId = savedLinkId,
            id = "inspect_${savedLinkId}_g$gen",
            generation = gen,
            url = url,
            status = status,
            resultJson = existing?.resultJson,
            errorCode = if (status == "WAITING_FOR_NETWORK") FailureCode.NETWORK_ERROR.name else null,
            errorMessage = reason,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        inspectionDao.insert(entity)
    }

    suspend fun markInspectionPending(savedLinkId: Long, url: String) =
        markInspectionDeferred(savedLinkId, url, status = "PENDING", reason = "Media check queued in background. Tap Retry to run now.")

    /**
     * Single coordinated entry point for saving / automatic / manual inspections.
     * Ensures at most one active inspection runs for a link and only hands off to WorkManager
     * if the immediate run ends in a retryable failure.
     */
    fun requestInspection(
        savedLinkId: Long,
        url: String,
        previewImageUrl: String? = null,
        forceRefresh: Boolean = false
    ): Job {
        if (!forceRefresh && isInProcessActive(savedLinkId)) {
            return activeInspections[savedLinkId]!!.job
        }
        try {
            WorkManager.getInstance(appContext).cancelUniqueWork("media_inspect_$savedLinkId")
        } catch (_: Throwable) {}
        return startInspection(
            savedLinkId = savedLinkId,
            url = url,
            previewImageUrl = previewImageUrl,
            forceRefresh = forceRefresh,
            scheduleBackupOnRetryable = true
        )
    }

    /**
     * Recovers an inspection row stuck in an active status when neither an in-process coroutine
     * nor a running WorkManager worker owns it anymore (e.g., after process death or killed task).
     */
    suspend fun recoverIfOrphaned(savedLinkId: Long) = withContext(Dispatchers.IO) {
        if (isInProcessActive(savedLinkId)) return@withContext
        val entity = inspectionDao.getEntityForLink(savedLinkId) ?: return@withContext
        if (entity.status !in ACTIVE_STATUSES) return@withContext

        val workerRunning = try {
            val infos = WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWork("media_inspect_$savedLinkId")
                .get()
            infos.any { it.state == androidx.work.WorkInfo.State.RUNNING }
        } catch (_: Throwable) {
            false
        }
        if (workerRunning) return@withContext

        Log.i(tag, "Recovering orphaned ${entity.status} inspection for link $savedLinkId")
        markInspectionDeferred(
            savedLinkId = savedLinkId,
            url = entity.url,
            status = "QUEUED",
            reason = "Previous media check was interrupted. Tap Retry to run now."
        )
    }

    /**
     * Enqueues durable background inspection via WorkManager.
     * Invalidates cache if saved link URL has changed.
     * Respects 2-hour cache TTL unless forceRefresh is true.
     */
    fun scheduleInspection(
        savedLinkId: Long,
        url: String,
        previewImageUrl: String? = null,
        forceRefresh: Boolean = false
    ): Job {
        return scope.launch {
            val existing = inspectionDao.getEntityForLink(savedLinkId)
            val now = System.currentTimeMillis()

            if (existing != null && existing.url != url) {
                inspectionDao.deleteForLink(savedLinkId)
            } else if (!forceRefresh && existing != null && existing.status == "COMPLETED" && (now - existing.updatedAt < 2 * 3600 * 1000L)) {
                return@launch
            }

            if (!forceRefresh && (isInProcessActive(savedLinkId) || (existing != null && existing.status in ACTIVE_STATUSES))) {
                return@launch
            }

            MediaInspectionWorker.schedule(
                context = appContext,
                savedLinkId = savedLinkId,
                url = url,
                previewImageUrl = previewImageUrl,
                forceRefresh = forceRefresh
            )
        }
    }

    /**
     * Starts inspection asynchronously on the manager's CoroutineScope.
     */
    fun startInspection(
        savedLinkId: Long,
        url: String,
        previewImageUrl: String? = null,
        forceRefresh: Boolean = false,
        scheduleBackupOnRetryable: Boolean = false
    ): Job {
        if (!forceRefresh) {
            val running = activeInspections[savedLinkId]
            if (running != null && running.job.isActive) return running.job
        } else {
            val prior = activeInspections.remove(savedLinkId)
            prior?.job?.cancel()
            prior?.processId?.let { pid ->
                try { YoutubeDL.getInstance().destroyProcessById(pid) } catch (_: Throwable) {}
                warmServer?.cancel(pid)
            }
            try { YoutubeDL.getInstance().destroyProcessById("inspect_$savedLinkId") } catch (_: Throwable) {}
        }

        val generation = System.currentTimeMillis()
        val inspectionProcessId = "inspect_${savedLinkId}_g$generation"
        activeGenerations[savedLinkId] = generation

        val job = scope.launch {
            val res = executeInspection(
                savedLinkId = savedLinkId,
                url = url,
                previewImageUrl = previewImageUrl,
                forceRefresh = forceRefresh,
                explicitGeneration = generation,
                explicitProcessId = inspectionProcessId,
                notifyOnTerminalFailure = forceRefresh
            )
            val resultValue = res.getOrNull()
            if (scheduleBackupOnRetryable &&
                activeGenerations[savedLinkId] == generation &&
                resultValue is InspectionResult.Failed &&
                resultValue.retryable
            ) {
                MediaInspectionWorker.schedule(
                    context = appContext,
                    savedLinkId = savedLinkId,
                    url = url,
                    previewImageUrl = previewImageUrl,
                    forceRefresh = false
                )
            }
        }
        val active = ActiveInspection(generation, inspectionProcessId, job)
        activeInspections[savedLinkId] = active
        job.invokeOnCompletion {
            activeInspections.remove(savedLinkId, active)
        }
        return job
    }

    /**
     * Cancels inspection for a given savedLinkId.
     * Cancels WorkManager work and terminates underlying yt-dlp process via destroyProcessById.
     */
    suspend fun cancelInspection(savedLinkId: Long) = withContext(Dispatchers.IO) {
        try {
            WorkManager.getInstance(appContext).cancelUniqueWork("media_inspect_$savedLinkId")
        } catch (_: Throwable) {}

        val active = activeInspections.remove(savedLinkId)
        active?.job?.cancel()

        val activePid = active?.processId
        if (activePid != null) {
            try { YoutubeDL.getInstance().destroyProcessById(activePid) } catch (_: Throwable) {}
            warmServer?.cancel(activePid)
        }
        val currentGen = activeGenerations[savedLinkId]
        if (currentGen != null) {
            try { YoutubeDL.getInstance().destroyProcessById("inspect_${savedLinkId}_g$currentGen") } catch (_: Throwable) {}
            warmServer?.cancel("inspect_${savedLinkId}_g$currentGen")
        }
        try { YoutubeDL.getInstance().destroyProcessById("inspect_$savedLinkId") } catch (_: Throwable) {}

        val entity = inspectionDao.getEntityForLink(savedLinkId)
        if (entity != null) {
            inspectionDao.insert(
                entity.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis())
            )
        }
    }

    /**
     * Reconciles inspections at app startup and on foreground lifecycle transitions.
     * Recovers interrupted/stale inspections, schedules uninspected links,
     * and invalidates cached inspections where the underlying link URL changed.
     */
    suspend fun reconcileInspections() = withContext(Dispatchers.IO) {
        try {
            val app = appContext as? LaterboxApplication
            val database = app?.container?.database ?: com.laterbox.app.data.local.LaterboxDatabase.getInstance(appContext)
            val userPrefsRepo = app?.container?.userPreferencesRepository
            val shouldAutoInspect = userPrefsRepo?.userPreferencesFlow?.first()?.autoInspectMediaOnSave ?: true

            // 1. Recover stale or interrupted inspections (> 5 minutes old in INSPECTING/PROCESSING state)
            val staleThreshold = System.currentTimeMillis() - (5 * 60 * 1000L)
            val staleInspections = inspectionDao.getStaleInspections(staleThreshold)
            for (stale in staleInspections) {
                if (isInProcessActive(stale.savedLinkId)) continue
                Log.i(tag, "Recovering stale inspection for link ${stale.savedLinkId}")
                markInspectionDeferred(stale.savedLinkId, stale.url, status = "QUEUED")
                scheduleInspection(
                    savedLinkId = stale.savedLinkId,
                    url = stale.url,
                    forceRefresh = true
                )
            }

            // 2. Discover uninspected links if auto-inspection is enabled
            if (shouldAutoInspect) {
                val linksNeedingInspection = database.savedLinkDao().getLinksNeedingInspection(limit = 20)
                for (link in linksNeedingInspection) {
                    scheduleInspection(
                        savedLinkId = link.id,
                        url = link.originalUrl,
                        previewImageUrl = link.previewImageUrl,
                        forceRefresh = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Inspection reconciliation warning: ${e.message}")
        }
    }
}
