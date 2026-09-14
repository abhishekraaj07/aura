package com.example.engine

enum class IntentType {
    ALARM_CREATE,
    ALARM_DELETE,
    ALARM_LIST,
    ALARM_TOGGLE,

    TIMER_CREATE,
    TIMER_CANCEL,
    TIMER_PAUSE,
    TIMER_RESUME,
    TIMER_LIST,
    TIMER_QUERY,

    REMINDER_CREATE,
    REMINDER_DELETE,
    REMINDER_LIST,

    CALENDAR_CREATE,
    CALENDAR_UPDATE,
    CALENDAR_DELETE,
    CALENDAR_LIST,
    CALENDAR_TODAY,
    CALENDAR_TOMORROW,

    CALL_CONTACT,
    CALL_NUMBER,
    CONTACTS_LIST,

    TIME_QUERY,
    DATE_QUERY,

    HELP,
    UNKNOWN
}

data class ParsedIntent(
    val intent: IntentType,
    val confidence: Float = 1.0f,
    val rawQuery: String,
    val language: String = "en",

    // Time & Date slots
    val hour: Int? = null,
    val minute: Int? = null,
    val dateCategory: String? = null, // "today", "tomorrow", "tonight", "next_weekday", etc.
    val targetDateMillis: Long? = null,

    // Timer slots
    val durationSeconds: Long? = null,

    // Text & Identification slots
    val title: String? = null,
    val label: String? = null,
    val description: String? = null,
    val location: String? = null,
    val contactName: String? = null,
    val phoneNumber: String? = null,

    // Patterns
    val repeatPattern: String? = null, // "DAILY", "WEEKDAY", "CUSTOM", "NONE"
    val repeatDays: String? = null,

    // Conversation & Validation
    val missingSlots: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)
