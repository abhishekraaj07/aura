package com.example.engine

data class PendingAction(
    val intent: IntentType,
    val partialIntent: ParsedIntent,
    val awaitingSlot: String? = null, // "time", "task", "duration", "contactName", "confirmation"
    val awaitingConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class ConversationManager {
    private var pendingAction: PendingAction? = null

    fun hasPendingAction(): Boolean {
        // Expire after 2 minutes of inactivity
        val current = pendingAction ?: return false
        if (System.currentTimeMillis() - current.createdAt > 120_000) {
            pendingAction = null
            return false
        }
        return true
    }

    fun getPendingAction(): PendingAction? {
        if (!hasPendingAction()) return null
        return pendingAction
    }

    fun setPendingAction(action: PendingAction?) {
        pendingAction = action
    }

    fun clear() {
        pendingAction = null
    }

    /**
     * Attempts to resolve user input against the pending conversation context.
     * Returns the completed ParsedIntent if fully resolved, or updated PendingAction if more slots needed, or null if unrelated.
     */
    fun processFollowUp(userInput: String): Pair<ParsedIntent?, String?> {
        val pending = getPendingAction() ?: return Pair(null, null)
        val lower = userInput.trim().lowercase()

        // 1. If awaiting confirmation (Yes/No/Confirm/Cancel/Haan/Nahi)
        if (pending.awaitingConfirmation) {
            val isAffirmative = lower in listOf("yes", "yeah", "sure", "ok", "confirm", "yep", "do it", "haan", "theek hai", "karo")
            val isNegative = lower in listOf("no", "nope", "cancel", "don't", "stop", "nahi", "mat karo", "band karo")

            if (isAffirmative) {
                clear()
                return Pair(pending.partialIntent.copy(requiresConfirmation = false), null)
            } else if (isNegative) {
                clear()
                return Pair(null, "Action cancelled.")
            }
        }

        // 2. If awaiting missing slot
        when (pending.awaitingSlot) {
            "time" -> {
                val time = NlpEngine.extractTime(userInput)
                if (time != null) {
                    val updated = pending.partialIntent.copy(
                        hour = time.first,
                        minute = time.second,
                        missingSlots = emptyList()
                    )
                    clear()
                    return Pair(updated, null)
                } else {
                    return Pair(null, "I didn't quite catch the time. Could you specify it like '7 AM' or '8:30 PM'?")
                }
            }
            "task" -> {
                val task = userInput.trim().replaceFirstChar { it.uppercase() }
                if (task.isNotEmpty()) {
                    // Check if the input also specified a time e.g., "submit assignment at 8 PM"
                    val time = NlpEngine.extractTime(userInput)
                    val updated = if (time != null) {
                        pending.partialIntent.copy(
                            title = task.replace(Regex("at\\s+\\d+.*", RegexOption.IGNORE_CASE), "").trim(),
                            hour = time.first,
                            minute = time.second,
                            missingSlots = emptyList()
                        )
                    } else if (pending.partialIntent.hour != null) {
                        pending.partialIntent.copy(title = task, missingSlots = emptyList())
                    } else {
                        // Still need time
                        val p = pending.partialIntent.copy(title = task, missingSlots = listOf("time"))
                        setPendingAction(PendingAction(p.intent, p, awaitingSlot = "time"))
                        return Pair(null, "What time would you like me to remind you?")
                    }
                    clear()
                    return Pair(updated, null)
                }
            }
            "duration" -> {
                val duration = NlpEngine.extractDuration(userInput)
                if (duration != null) {
                    val updated = pending.partialIntent.copy(
                        durationSeconds = duration,
                        missingSlots = emptyList()
                    )
                    clear()
                    return Pair(updated, null)
                } else {
                    return Pair(null, "Please tell me the duration, like '15 minutes' or '1 hour'.")
                }
            }
            "contactName" -> {
                val text = userInput.trim()
                if (text.isNotEmpty()) {
                    val digitCount = text.count { it.isDigit() }
                    val updated = if (digitCount >= 3) {
                        val cleanPhone = text.filter { it.isDigit() || it == '+' }
                        pending.partialIntent.copy(
                            intent = IntentType.CALL_NUMBER,
                            phoneNumber = cleanPhone,
                            missingSlots = emptyList(),
                            requiresConfirmation = false
                        )
                    } else {
                        pending.partialIntent.copy(
                            contactName = text,
                            missingSlots = emptyList(),
                            requiresConfirmation = false
                        )
                    }
                    clear()
                    return Pair(updated, null)
                }
            }
        }

        return Pair(null, null)
    }
}
