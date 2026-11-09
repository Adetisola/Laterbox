package com.laterbox.app.capture.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

sealed interface SelectionEventOutcome {
    data class CandidateText(
        val text: String,
        val boundsInScreen: Rect?,
        val packageName: String
    ) : SelectionEventOutcome

    data class SelectionCleared(
        val packageName: String
    ) : SelectionEventOutcome

    data object Ignored : SelectionEventOutcome
}

object SelectionTextExtractor {

    private val SYSTEM_IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey"
    )

    fun inspectEvent(
        event: AccessibilityEvent,
        ownPackageName: String,
        excludedPackages: Set<String>
    ): SelectionEventOutcome {
        val packageName = event.packageName?.toString() ?: return SelectionEventOutcome.Ignored

        if (packageName == ownPackageName ||
            packageName.startsWith("$ownPackageName.") ||
            SYSTEM_IGNORED_PACKAGES.contains(packageName) ||
            excludedPackages.contains(packageName)
        ) {
            return SelectionEventOutcome.Ignored
        }

        if (event.isPassword) {
            return SelectionEventOutcome.Ignored
        }

        val sourceNode = try {
            event.source
        } catch (_: Exception) {
            null
        }

        if (sourceNode?.isPassword == true) {
            try {
                sourceNode.recycle()
            } catch (_: Exception) {}
            return SelectionEventOutcome.Ignored
        }

        val bounds = Rect()
        var hasValidBounds = false
        if (sourceNode != null) {
            try {
                sourceNode.getBoundsInScreen(bounds)
                if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                    hasValidBounds = true
                }
            } catch (_: Exception) {
                // Ignore bounds error
            }
        }

        val nodeText = sourceNode?.text?.toString()
        val hintText = sourceNode?.hintText?.toString()
        val selStart = sourceNode?.textSelectionStart ?: -1
        val selEnd = sourceNode?.textSelectionEnd ?: -1
        val eventText = if (event.text.isNotEmpty()) event.text.joinToString(" ") else null
        val fromIndex = event.fromIndex
        val toIndex = event.toIndex

        try {
            return extractFromRawSelection(
                nodeText = nodeText,
                hintText = hintText,
                selStart = selStart,
                selEnd = selEnd,
                eventText = eventText,
                fromIndex = fromIndex,
                toIndex = toIndex,
                isPassword = false,
                packageName = packageName,
                ownPackage = ownPackageName,
                excludedPackages = excludedPackages,
                bounds = if (hasValidBounds) bounds else null
            )
        } finally {
            try {
                sourceNode?.recycle()
            } catch (_: Exception) {
                // Ignore recycle error
            }
        }
    }

    fun extractFromRawSelection(
        nodeText: String?,
        hintText: String?,
        selStart: Int,
        selEnd: Int,
        eventText: String?,
        fromIndex: Int,
        toIndex: Int,
        isPassword: Boolean,
        packageName: String,
        ownPackage: String,
        excludedPackages: Set<String>,
        bounds: Rect?
    ): SelectionEventOutcome {
        if (isPassword) return SelectionEventOutcome.Ignored
        if (packageName == ownPackage ||
            packageName.startsWith("$ownPackage.") ||
            SYSTEM_IGNORED_PACKAGES.contains(packageName) ||
            excludedPackages.contains(packageName)
        ) {
            return SelectionEventOutcome.Ignored
        }

        // Selection cleared check
        if (selStart >= 0 && selStart == selEnd) {
            return SelectionEventOutcome.SelectionCleared(packageName)
        }
        if (fromIndex >= 0 && fromIndex == toIndex && selStart < 0) {
            return SelectionEventOutcome.SelectionCleared(packageName)
        }

        // Strategy A: node text substring between selStart and selEnd
        if (!nodeText.isNullOrEmpty() && nodeText != hintText) {
            if (selStart in 0 until selEnd && selEnd <= nodeText.length) {
                val candidate = nodeText.substring(selStart, selEnd).trim()
                if (candidate.isNotEmpty()) {
                    return SelectionEventOutcome.CandidateText(candidate, bounds, packageName)
                }
            }
        }

        // Strategy B: event text between fromIndex and toIndex
        if (!eventText.isNullOrEmpty()) {
            if (fromIndex in 0 until toIndex && toIndex <= eventText.length) {
                val candidate = eventText.substring(fromIndex, toIndex).trim()
                if (candidate.isNotEmpty()) {
                    return SelectionEventOutcome.CandidateText(candidate, bounds, packageName)
                }
            } else if (fromIndex < 0 && toIndex < 0) {
                // Entire event text was selected
                val candidate = eventText.trim()
                if (candidate.isNotEmpty()) {
                    return SelectionEventOutcome.CandidateText(candidate, bounds, packageName)
                }
            }
        }

        // Strategy C: nodeText itself if selStart == 0 and selEnd == nodeText.length
        if (!nodeText.isNullOrEmpty() && nodeText != hintText) {
            if (selStart == 0 && selEnd == nodeText.length) {
                val candidate = nodeText.trim()
                if (candidate.isNotEmpty()) {
                    return SelectionEventOutcome.CandidateText(candidate, bounds, packageName)
                }
            }
        }

        return SelectionEventOutcome.Ignored
    }
}
