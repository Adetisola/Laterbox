package com.laterbox.app.ai

enum class ReminderOrigin {
    MANUAL,
    SMART_DIGEST
}

data class SmartReminderCopy(
    val title: String,
    val body: String,
    val isAiGenerated: Boolean = false,
    val origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST
)

