package com.laterbox.app.media.domain.model

data class MediaCandidate(
    val candidateId: String,
    val mediaType: MediaType,
    val formatLabel: String,
    val estimatedBytes: Long?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val source: AcquisitionSource
)
