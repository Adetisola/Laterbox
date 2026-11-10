package com.laterbox.app.media.domain.model

enum class FailureCode(val userMessage: String) {
    NETWORK_ERROR("Network connection lost. Please check your internet connection."),
    INSUFFICIENT_STORAGE("Not enough storage space available on device."),
    PRIVATE_CONTENT("This media is private or protected."),
    LOGIN_REQUIRED("Login required to access this media."),
    GEO_BLOCKED("Content is not available in your region."),
    RATE_LIMITED("Too many requests. Please try again later."),
    EXTRACTOR_BROKEN("Platform extractor failed. An update may be needed."),
    UNSUPPORTED_URL("No downloadable media found at this URL."),
    HTTP_ERROR("Server returned an error response."),
    CORRUPT_MEDIA("Downloaded media was corrupted or invalid."),
    UNKNOWN("An unexpected error occurred during download.");

    val isRetryable: Boolean
        get() = this == NETWORK_ERROR || this == RATE_LIMITED || this == HTTP_ERROR
}
