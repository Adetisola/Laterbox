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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val tag = "MediaInspectionManager"
    private val inspectionMutexes = ConcurrentHashMap<Long, Mutex>()

    fun getInspectionResultForLink(savedLinkId: Long): Flow<InspectionResult?> {
        return inspectionDao.getInspectionForLink(savedLinkId).map { entity ->
            if (entity == null) return@map null
            when (entity.status) {
                "COMPLETED" -> {
                    entity.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                }
                "FAILED" -> {
                    val code = entity.errorCode?.let {
                        try { FailureCode.valueOf(it) } catch (_: Exception) { FailureCode.UNKNOWN }
                    } ?: FailureCode.UNKNOWN
                    InspectionResult.Failed(
                        code = code,
                        message = entity.errorMessage,
                        retryable = code.isRetryable
                    )
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
    suspend fun executeInspection(
        savedLinkId: Long,
        url: String,
        previewImageUrl: String? = null,
        forceRefresh: Boolean = false
    ): Result<InspectionResult> = withContext(Dispatchers.IO) {
        val mutex = inspectionMutexes.computeIfAbsent(savedLinkId) { Mutex() }
        mutex.withLock {
            val existing = inspectionDao.getEntityForLink(savedLinkId)
            val now = System.currentTimeMillis()

            // Invalidate cache if saved link URL has changed
            if (existing != null && existing.url != url) {
                inspectionDao.deleteForLink(savedLinkId)
            } else if (!forceRefresh && existing != null && existing.status == "COMPLETED" && (now - existing.updatedAt < 2 * 3600 * 1000L)) {
                val cached = existing.resultJson?.let { InspectionResultJsonAdapter.fromJson(it) }
                if (cached != null) return@withContext Result.success(cached)
            }

            val inspectionProcessId = "inspect_$savedLinkId"
            val inFlightEntity = MediaInspectionEntity(
                id = inspectionProcessId,
                savedLinkId = savedLinkId,
                url = url,
                status = "INSPECTING",
                resultJson = existing?.resultJson,
                errorCode = null,
                errorMessage = null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            inspectionDao.insert(inFlightEntity)

            try {
                var result = platformResolver.resolveAndInspect(url, inspectionProcessId)
                result = YtDlpOutputParser.attachPreviewImageFallback(result, previewImageUrl)

                val json = InspectionResultJsonAdapter.toJson(result)
                val (status, errCode, errMsg) = when (result) {
                    is InspectionResult.Failed -> Triple("FAILED", result.code.name, result.message)
                    else -> Triple("COMPLETED", null, null)
                }

                val completedEntity = MediaInspectionEntity(
                    id = inspectionProcessId,
                    savedLinkId = savedLinkId,
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
            } catch (t: Throwable) {
                withContext(NonCancellable) {
                    try {
                        YoutubeDL.getInstance().destroyProcessById(inspectionProcessId)
                    } catch (_: Throwable) {
                    }

                    val failedResult = InspectionResult.Failed(
                        code = FailureCode.UNKNOWN,
                        message = t.localizedMessage ?: "Failed to inspect media",
                        retryable = true
                    )
                    val fallbackResult = YtDlpOutputParser.attachPreviewImageFallback(failedResult, previewImageUrl)
                    val json = InspectionResultJsonAdapter.toJson(fallbackResult)
                    val (status, errCode, errMsg) = when (fallbackResult) {
                        is InspectionResult.Failed -> Triple("FAILED", fallbackResult.code.name, fallbackResult.message)
                        else -> Triple("COMPLETED", null, null)
                    }

                    val entity = MediaInspectionEntity(
                        id = inspectionProcessId,
                        savedLinkId = savedLinkId,
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
        return scope.launch {
            executeInspection(savedLinkId, url, previewImageUrl, forceRefresh)
        }
    }

    /**
     * Cancels inspection for a given savedLinkId.
     * Cancels WorkManager work and terminates underlying yt-dlp process via destroyProcessById.
     */
    suspend fun cancelInspection(savedLinkId: Long) = withContext(Dispatchers.IO) {
        try {
            WorkManager.getInstance(appContext).cancelUniqueWork("media_inspect_$savedLinkId")
        } catch (_: Throwable) {
        }

        try {
            YoutubeDL.getInstance().destroyProcessById("inspect_$savedLinkId")
        } catch (_: Throwable) {
        }

        val entity = inspectionDao.getEntityForLink(savedLinkId)
        if (entity != null && entity.status in setOf("PENDING", "PREPARING", "INSPECTING", "PROCESSING")) {
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
