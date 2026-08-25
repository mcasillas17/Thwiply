package com.elopenmike.thwiply.ui.today

enum class TaskSource(val displayName: String, val iconName: String) {
    WHATSAPP("WhatsApp", "chat"),
    SLACK("Slack", "forum"),
    GMAIL("Gmail", "mail"),
    MESSAGES("Messages", "sms"),
    SCREENSHOT("Screenshot", "camera"),
    MANUAL("Manual", "edit")
}

data class TaskItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val source: TaskSource,
    val sourceSender: String,
    val dueTime: String? = null,
    val isCompleted: Boolean = false,
    val isHighPriority: Boolean = false,
    val aiSnippet: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
