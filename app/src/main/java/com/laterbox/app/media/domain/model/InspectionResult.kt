package com.laterbox.app.media.domain.model

sealed interface InspectionResult {
    data class Available(
        val platform: String,
        val title: String?,
        val thumbnailUrl: String?,
        val candidates: List<MediaCandidate>
    ) : InspectionResult

    data class MetadataOnly(
        val title: String?,
        val artist: String?,
        val artworkUrl: String?
    ) : InspectionResult

    data class NeedsAuth(
        val reason: String
    ) : InspectionResult

    data class Unsupported(
        val reason: String
    ) : InspectionResult

    data class Failed(
        val code: FailureCode,
        val message: String? = null,
        val retryable: Boolean = code.isRetryable
    ) : InspectionResult
}
