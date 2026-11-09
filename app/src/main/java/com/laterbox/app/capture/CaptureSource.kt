package com.laterbox.app.capture

/**
 * Identifies the origin through which a link was captured in Laterbox.
 */
enum class CaptureSource {
    IN_APP,
    SHARE_SHEET,
    QUICK_SETTINGS_TILE,
    PROCESS_TEXT,
    ACCESSIBILITY_SELECTION,
    CLIPBOARD_SUGGESTION
}
