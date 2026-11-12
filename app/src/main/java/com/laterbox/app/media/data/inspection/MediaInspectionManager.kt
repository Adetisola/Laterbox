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
                "PARTIAL" -> {
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
                else -> null
            }
        }
    }

    fun isInspectingFlow(savedLinkId: Long): Flow<Boolean> {
        return inspectionDao.getInspectionForLink(savedLinkId).map { entity ->
            entity != null && entity.status in setOf("PENDING", "PREPARING", "INSPECTING", "PROCESSING")
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
        explicitProcessId: String? = null
    ): Result<InspectionResult> = withContext(Dispatchers.IO) {
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

        // Invalidate cache if saved link URL has changed
        if (existing != null && existing.url != url) {
            inspectionDao.deleteForLink(savedLinkId)
        } else if (!forceRefresh && existing != null && existing.status == "COMPLETED" && (now - existing.updatedAt < 2 * 3600 * 1000L)) {
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
                            status = "PARTIAL",
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
                var result: InspectionResult
                if (isYouTube && ytdlpAdapter != null) {
                    // Staged execution for YouTube:
                    // Stage 1: Fast inspection (visionos client)
                    val stage1Result = ytdlpAdapter.inspectFast(url, inspectionProcessId)
                    if (stage1Result is InspectionResult.Available && activeGenerations[savedLinkId] == generation) {
                        val currentCheck = inspectionDao.getEntityForLink(savedLinkId)
                        if (currentCheck == null || currentCheck.status != "CANCELLED" || currentCheck.generation < generation) {
                            val stage1WithPreview = YtDlpOutputParser.attachPreviewImageFallback(stage1Result, resolvedPreviewImageUrl)
                            inspectionDao.insert(
                                inFlightEntity.copy(
                                    status = "PARTIAL",
                                    resultJson = InspectionResultJsonAdapter.toJson(stage1WithPreview),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        // Stage 2: Complete inspection with QuickJS
                        val completeResult = ytdlpAdapter.inspectComplete(url, inspectionProcessId, stage1Result = stage1Result)
                        result = if (completeResult is InspectionResult.Available) {
                            completeResult
                        } else {
                            stage1Result.copy(isComplete = true)
                        }
                    } else {
                        result = stage1Result
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
     * Marks an inspection as pending between scheduled retries.
     */
    suspend fun markInspectionPending(savedLinkId: Long, url: String) = withContext(Dispatchers.IO) {
        val existing = inspectionDao.getEntityForLink(savedLinkId)
        val now = System.currentTimeMillis()
        val gen = activeGenerations[savedLinkId] ?: now
        val entity = MediaInspectionEntity(
            savedLinkId = savedLinkId,
            id = "inspect_${savedLinkId}_g$gen",
            generation = gen,
            url = url,
            status = "PENDING",
            resultJson = existing?.resultJson,
            errorCode = null,
            errorMessage = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        inspectionDao.insert(entity)
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

            if (!forceRefresh && existing != null && existing.status in setOf("PENDING", "PREPARING", "INSPECTING", "PROCESSING")) {
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
        forceRefresh: Boolean = false
    ): Job {
        if (forceRefresh) {
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
            executeInspection(savedLinkId, url, previewImageUrl, forceRefresh, generation, inspectionProcessId)
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
                Log.i(tag, "Recovering stale inspection for link ${stale.savedLinkId}")
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
