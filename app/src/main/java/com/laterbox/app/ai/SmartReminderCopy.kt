package com.laterbox.app.ai

data class SmartReminderCopy(
    val title: String,
    val body: String,
    val isAiGenerated: Boolean = false
)
