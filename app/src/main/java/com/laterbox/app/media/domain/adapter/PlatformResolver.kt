package com.laterbox.app.media.domain.adapter

import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AdapterRegistry(
    private val adapters: List<MediaPlatformAdapter>
) {
    fun findAdapter(url: String): MediaPlatformAdapter? {
        return adapters.firstOrNull { it.canHandle(url) }
    }

    fun getAllAdapters(): List<MediaPlatformAdapter> = adapters
}

class PlatformResolver(
    val registry: AdapterRegistry,
    private val fallbackTimeoutMs: Long = 90_000L
) {
    suspend fun resolveAndInspect(url: String, processId: String? = null): InspectionResult = withContext(Dispatchers.IO) {
        val adapter = registry.findAdapter(url)
            ?: return@withContext InspectionResult.Unsupported("No adapter found to handle $url")

        val deadlineMs = adapter.inspectionTimeoutMs.takeIf { it > 0 } ?: fallbackTimeoutMs
        try {
            val result = withTimeoutOrNull(deadlineMs) {
                adapter.inspect(url, processId)
            }
            result ?: InspectionResult.Failed(
                code = FailureCode.EXTRACTION_TIMEOUT,
                message = "Media inspection timed out after ${deadlineMs / 1000} seconds",
                retryable = true
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = t.localizedMessage ?: "Inspection failed",
                retryable = true
            )
        }
    }
}
