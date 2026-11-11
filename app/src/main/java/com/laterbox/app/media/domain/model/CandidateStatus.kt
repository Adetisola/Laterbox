package com.laterbox.app.media.domain.model

enum class CandidateStatus {
    AVAILABLE,
    METADATA_DISCOVERED,
    PREVIEW_ONLY,
    REQUIRES_AUTH,
    REQUIRES_PERMISSION,
    UNSUPPORTED_ACQUISITION,
    TEMPORARILY_UNAVAILABLE,
    RESTRICTED,
    FAILED
}
