package com.laterbox.app.media.data.inspection

import android.content.Context
import com.laterbox.app.media.data.adapters.InspectionResultJsonAdapter
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpOutputParser
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.data.db.entity.MediaInspectionEntity
import com.laterbox.app.media.domain.adapter.PlatformResolver
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
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

    fun startInspection(
        savedLinkId: Long,
        url: String,
        previewImageUrl: String? = null,
        forceRefresh: Boolean = false
    ): Job {
        return scope.launch {
            val mutex = inspectionMutexes.computeIfAbsent(savedLinkId) { Mutex() }
            mutex.withLock {
                val existing = inspectionDao.getEntityForLink(savedLinkId)
                val now = System.currentTimeMillis()

                // Check cache: 2 hours TTL if completed
                if (!forceRefresh && existing != null && existing.status == "COMPLETED" && (now - existing.updatedAt < 2 * 3600 * 1000L)) {
                    return@launch
                }

                if (existing != null && existing.status in setOf("PENDING", "PREPARING", "INSPECTING", "PROCESSING")) {
                    return@launch
                }

                val inspectionId = "inspect_$savedLinkId"
                val inFlightEntity = MediaInspectionEntity(
                    id = inspectionId,
                    savedLinkId = savedLinkId,
                    url = url,
                    status = "INSPECTING",
                    resultJson = null,
                    errorCode = null,
                    errorMessage = null,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                inspectionDao.insert(inFlightEntity)

                try {
                    var result = platformResolver.resolveAndInspect(url)
                    // Attach OpenGraph fallback image if applicable
                    result = YtDlpOutputParser.attachPreviewImageFallback(result, previewImageUrl)

                    val json = InspectionResultJsonAdapter.toJson(result)
                    val (status, errCode, errMsg) = when (result) {
                        is InspectionResult.Failed -> Triple("FAILED", result.code.name, result.message)
                        else -> Triple("COMPLETED", null, null)
                    }

                    val completedEntity = MediaInspectionEntity(
                        id = inspectionId,
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
                } catch (t: Throwable) {
                    val failedResult = InspectionResult.Failed(
                        code = FailureCode.UNKNOWN,
                        message = t.localizedMessage ?: "Failed to inspect",
                        retryable = true
                    )
                    val fallbackResult = YtDlpOutputParser.attachPreviewImageFallback(failedResult, previewImageUrl)
                    val json = InspectionResultJsonAdapter.toJson(fallbackResult)
                    val (status, errCode, errMsg) = when (fallbackResult) {
                        is InspectionResult.Failed -> Triple("FAILED", fallbackResult.code.name, fallbackResult.message)
                        else -> Triple("COMPLETED", null, null)
                    }

                    val entity = MediaInspectionEntity(
                        id = inspectionId,
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
            }
        }
    }

    suspend fun cancelInspection(savedLinkId: Long) = withContext(Dispatchers.IO) {
        val entity = inspectionDao.getEntityForLink(savedLinkId)
        if (entity != null && entity.status in setOf("PENDING", "PREPARING", "INSPECTING", "PROCESSING")) {
            inspectionDao.insert(
                entity.copy(status = "CANCELLED", updatedAt = System.currentTimeMillis())
            )
        }
    }
}
