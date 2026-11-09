package com.laterbox.app.capture.accessibility

class SelectionPromptDeduplicator {

    companion object {
        const val DISMISSED_COOLDOWN_MS = 60_000L // 1 minute
        const val SAVED_COOLDOWN_MS = 600_000L // 10 minutes

        private val SYSTEM_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.touchtype.swiftkey"
        )
    }

    private val dismissedCooldowns = mutableMapOf<String, Long>()
    private val savedCooldowns = mutableMapOf<String, Long>()

    var activeSourcePackage: String? = null
        private set

    var currentlyShownUrl: String? = null
        private set

    fun shouldShowPrompt(
        normalizedUrl: String,
        packageName: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (currentlyShownUrl == normalizedUrl) {
            return false
        }

        val dismissedAt = dismissedCooldowns[normalizedUrl]
        if (dismissedAt != null && nowMs - dismissedAt < DISMISSED_COOLDOWN_MS) {
            return false
        }

        val savedAt = savedCooldowns[normalizedUrl]
        if (savedAt != null && nowMs - savedAt < SAVED_COOLDOWN_MS) {
            return false
        }

        return true
    }

    fun markPromptShown(normalizedUrl: String, packageName: String) {
        currentlyShownUrl = normalizedUrl
        activeSourcePackage = packageName
    }

    fun markPromptDismissed(url: String, nowMs: Long = System.currentTimeMillis()) {
        dismissedCooldowns[url] = nowMs
        if (currentlyShownUrl == url) {
            currentlyShownUrl = null
        }
    }

    fun markPromptSaved(url: String, nowMs: Long = System.currentTimeMillis()) {
        savedCooldowns[url] = nowMs
        if (currentlyShownUrl == url) {
            currentlyShownUrl = null
        }
    }

    fun shouldDismissOnWindowStateChange(newPackageName: String?, ownPackageName: String? = null): Boolean {
        if (newPackageName == null) return false
        if (currentlyShownUrl == null) return false

        // Do not dismiss when system UI, keyboards, or temporary action mode popup appears
        if (SYSTEM_OVERLAY_PACKAGES.contains(newPackageName)) {
            return false
        }

        // Do not dismiss if the same app package is active or if Laterbox itself is showing
        if (newPackageName == activeSourcePackage || (ownPackageName != null && newPackageName == ownPackageName)) {
            return false
        }

        // The user switched away from the app where the selection occurred
        return true
    }

    fun clear() {
        currentlyShownUrl = null
        activeSourcePackage = null
    }
}
