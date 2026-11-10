package com.laterbox.app.media.domain.model

enum class MatchConfidence {
    STRONG,           // Composite score >= 0.85 -> One-tap download eligible
    POSSIBLE,         // 0.65 <= score < 0.85 -> Download with optional candidate review
    WEAK,             // score < 0.65 -> Requires user review
    VERSION_MISMATCH, // Modifiers indicate a live, acoustic, or cover mismatch
    UNAVAILABLE       // No matching candidates discovered
}

data class MusicMatchCandidate(
    val candidateId: String,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val score: Double,
    val confidence: MatchConfidence,
    val versionTag: String? = null,
    val downloadCandidate: MediaCandidate,
    val sourceDescription: String = "Authorized Audio Source"
)
