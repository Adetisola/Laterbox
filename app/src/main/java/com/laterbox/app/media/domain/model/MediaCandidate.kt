package com.laterbox.app.media.domain.model

data class MediaCandidate(
    val candidateId: String,
    val mediaType: MediaType,
    val formatLabel: String,
    val estimatedBytes: Long?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val source: AcquisitionSource,
    val isOgFallback: Boolean = false,
    val formatId: String? = null,
    val ext: String? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val fps: Double? = null,
    val bitrate: Double? = null,
    val hasVideo: Boolean = mediaType == MediaType.VIDEO,
    val hasAudio: Boolean = mediaType == MediaType.AUDIO || (mediaType == MediaType.VIDEO && acodec != null),
    val isBest: Boolean = false,
    val isThumbnail: Boolean = false,
    val qualityLabel: String? = null,
    val requiresMerge: Boolean = false,
    val audioTrackMissing: Boolean = false,
    val status: CandidateStatus = CandidateStatus.AVAILABLE,
    val statusReason: String? = null,
    val extractor: String? = null
)
