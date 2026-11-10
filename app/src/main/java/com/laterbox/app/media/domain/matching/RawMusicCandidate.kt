package com.laterbox.app.media.domain.matching

import com.laterbox.app.media.domain.model.MediaCandidate

data class RawMusicCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val downloadCandidate: MediaCandidate,
    val sourceDescription: String = "Authorized Music Source"
)
