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
    private val registry: AdapterRegistry
) {
    suspend fun resolveAndInspect(url: String): InspectionResult = withContext(Dispatchers.IO) {
        val adapter = registry.findAdapter(url)
            ?: return@withContext InspectionResult.Unsupported("No adapter found to handle $url")

        try {
            val result = withTimeoutOrNull(30_000L) {
                adapter.inspect(url)
            }
            result ?: InspectionResult.Failed(
                code = FailureCode.NETWORK_ERROR,
                message = "Media inspection timed out after 30 seconds",
                retryable = true
            )
        } catch (t: Throwable) {
            InspectionResult.Failed(
                code = FailureCode.UNKNOWN,
                message = t.localizedMessage ?: "Inspection failed",
                retryable = true
            )
        }
    }
}
