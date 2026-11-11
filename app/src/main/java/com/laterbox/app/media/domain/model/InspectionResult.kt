package com.laterbox.app.media.domain.model

sealed interface InspectionResult {
    data class Available(
        val platform: String,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val candidates: List<MediaCandidate>,
        val artist: String? = null,
        val album: String? = null,
        val musicMatches: List<MusicMatchCandidate> = emptyList(),
        val spotifyTrackId: String? = null,
        val partialFailures: List<String> = emptyList(),
        val extractorVersion: String? = null,
        val isComplete: Boolean = true
    ) : InspectionResult

    data class MetadataOnly(
        val title: String?,
        val artist: String?,
        val artworkUrl: String?
    ) : InspectionResult

    data class NeedsAuth(
        val reason: String,
        val previewCandidate: MediaCandidate? = null
    ) : InspectionResult

    data class Unsupported(
        val reason: String,
        val previewCandidate: MediaCandidate? = null
    ) : InspectionResult

    data class Failed(
        val code: FailureCode,
        val message: String? = null,
        val retryable: Boolean = code.isRetryable,
        val previewCandidate: MediaCandidate? = null
    ) : InspectionResult
}
