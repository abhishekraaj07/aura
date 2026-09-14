package com.example.engine

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

object NlpEngine {

    fun parse(input: String): ParsedIntent {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        val isHindi = containsHindiWords(lower)
        val language = if (isHindi) "hinglish" else "en"

        // 1. HELP
        if (matchesPattern(lower, listOf("help", "what can you do", "kya kar sakte ho", "madad", "options", "commands"))) {
            return ParsedIntent(
                intent = IntentType.HELP,
                rawQuery = trimmed,
                language = language
            )
        }

        // 2. TIME & DATE QUERIES
        if (matchesPattern(lower, listOf("what time is it", "current time", "what's the time", "kitne baje", "samay kya hai", "tell me the time"))) {
            return ParsedIntent(
                intent = IntentType.TIME_QUERY,
                rawQuery = trimmed,
                language = language
            )
        }
        if (matchesPattern(lower, listOf("what's the date", "what is today's date", "today's date", "aaj ki tareekh", "what day is it", "konsa din hai", "aaj konsi date hai"))) {
            return ParsedIntent(
                intent = IntentType.DATE_QUERY,
                rawQuery = trimmed,
                language = language
            )
        }

        // 3. CALL INTENTS
        val callIntent = parseCall(trimmed, lower, language)
        if (callIntent != null) return callIntent

        // 4. TIMER INTENTS
        val timerIntent = parseTimer(trimmed, lower, language)
        if (timerIntent != null) return timerIntent

        // 5. ALARM INTENTS
        val alarmIntent = parseAlarm(trimmed, lower, language)
        if (alarmIntent != null) return alarmIntent

        // 6. CALENDAR INTENTS
        val calendarIntent = parseCalendar(trimmed, lower, language)
        if (calendarIntent != null) return calendarIntent

        // 7. REMINDER INTENTS
        val reminderIntent = parseReminder(trimmed, lower, language)
        if (reminderIntent != null) return reminderIntent

        // Fallback: Check if user just provided a standalone time or duration or answer to follow-up
        val standaloneTime = extractTime(lower)
        if (standaloneTime != null) {
            return ParsedIntent(
                intent = IntentType.UNKNOWN,
                rawQuery = trimmed,
                language = language,
                hour = standaloneTime.first,
                minute = standaloneTime.second
            )
        }

        return ParsedIntent(
            intent = IntentType.UNKNOWN,
            rawQuery = trimmed,
            language = language
        )
    }

    private fun containsHindiWords(text: String): Boolean {
        val hindiKeywords = listOf(
            "kal", "aaj", "parso", "subah", "shaam", "raat", "dopahar", "baje",
            "laga do", "karo", "yaad dilana", "ghante", "ghanta", "khol", "hatao",
            "dikhao", "batao", "tareekh", "samay", "ko call", "call karo"
        )
        return hindiKeywords.any { text.contains(it) }
    }

    private fun matchesPattern(text: String, patterns: List<String>): Boolean {
        return patterns.any { text.contains(it) }
    }

    // ==========================================
    // CALL PARSING
    // ==========================================
    private fun parseCall(original: String, lower: String, lang: String): ParsedIntent? {
        // Contacts list query
        if (matchesPattern(lower, listOf("show contacts", "my contacts", "open contacts", "contacts dikhao", "contacts list", "view contacts", "all contacts", "mere contacts"))) {
            return ParsedIntent(
                intent = IntentType.CONTACTS_LIST,
                rawQuery = original,
                language = lang
            )
        }

        // Avoid false triggers on call logs/history
        if (lower.contains("history") || lower.contains("log")) return null

        val callTriggers = listOf(
            "call", "dial", "phone", "ring", "ko call", "ko phone",
            "call lagao", "call karo", "call milao", "phone lagao", "phone karo", "phone milao"
        )
        val hasTrigger = callTriggers.any { lower.contains(it) }
        if (!hasTrigger) return null

        // Check for phone number (digits >= 3, handles emergency 911/100/112 and standard numbers)
        val numberPattern = Pattern.compile("(?:\\+?[0-9][0-9\\-\\s\\(\\)]{1,14}[0-9])")
        val numMatcher = numberPattern.matcher(original)
        if (numMatcher.find()) {
            val phoneCandidate = numMatcher.group(0).trim()
            val digitCount = phoneCandidate.count { it.isDigit() }
            if (digitCount >= 3) {
                val cleanPhone = phoneCandidate.filter { it.isDigit() || it == '+' }
                return ParsedIntent(
                    intent = IntentType.CALL_NUMBER,
                    rawQuery = original,
                    language = lang,
                    phoneNumber = cleanPhone,
                    requiresConfirmation = false,
                    confirmationPrompt = "Do you want to call $cleanPhone?"
                )
            }
        }

        var contactName: String? = null

        // Pattern 1: Hindi/Hinglish "<Name> ko call/phone [lagao/karo/milao/kijiye/karna]"
        // E.g. "Rahul ko call karo", "Rahul Sharma ko call lagao", "Mom ko phone lagao", "Dad ko call"
        val hindiKoPattern = Pattern.compile("([a-zA-Z\\s\\.]+?)\\s+ko\\s+(?:call|phone|dial)(?:\\s+(?:karo|lagao|kijiye|milao|karna))?", Pattern.CASE_INSENSITIVE)
        val hindiKoMatcher = hindiKoPattern.matcher(original)
        if (hindiKoMatcher.find()) {
            contactName = hindiKoMatcher.group(1).trim()
        }

        // Pattern 2: Hindi/Hinglish "call/phone/dial [lagao/karo/milao/kijiye] <Name> [ko]"
        // E.g. "Call lagao Rahul ko", "Phone lagao Mom", "Call karo Papa"
        if (contactName.isNullOrBlank()) {
            val hindiPrefixPattern = Pattern.compile("(?:call|phone|dial)\\s+(?:lagao|karo|milao|kijiye)\\s+([a-zA-Z\\s\\.]+?)(?:\\s+ko)?$", Pattern.CASE_INSENSITIVE)
            val matcher = hindiPrefixPattern.matcher(original)
            if (matcher.find()) {
                contactName = matcher.group(1).trim()
            }
        }

        // Pattern 3: English standard patterns:
        // E.g. "Call Rahul", "Call Rahul Sharma", "Please call Mom", "Can you call Alex", "Make a call to Rahul", "Call to Mom"
        if (contactName.isNullOrBlank()) {
            val englishCallPattern = Pattern.compile("(?:please\\s+)?(?:can\\s+you\\s+)?(?:make\\s+a\\s+)?(?:phone\\s+)?call\\s+(?:to\\s+)?([a-zA-Z\\s\\.]+?)(?:\\s+(?:please|on\\s+phone|on\\s+mobile|now))?$", Pattern.CASE_INSENSITIVE)
            val matcher = englishCallPattern.matcher(original)
            if (matcher.find()) {
                contactName = matcher.group(1).trim()
            }
        }

        // Pattern 4: "Dial <Name>", "Ring <Name>", "Phone <Name>", "Give a call to <Name>"
        if (contactName.isNullOrBlank()) {
            val dialPattern = Pattern.compile("(?:please\\s+)?(?:dial|ring|phone|give\\s+a\\s+call\\s+to)\\s+([a-zA-Z\\s\\.]+?)(?:\\s+please)?$", Pattern.CASE_INSENSITIVE)
            val matcher = dialPattern.matcher(original)
            if (matcher.find()) {
                contactName = matcher.group(1).trim()
            }
        }

        // Clean and validate contact candidate
        if (!contactName.isNullOrBlank()) {
            var cleaned = contactName.trim()
            if (cleaned.lowercase(Locale.ROOT).startsWith("to ")) {
                cleaned = cleaned.substring(3).trim()
            }
            cleaned = cleaned.replace(Regex("(?i)\\s+(?:ko|please|on\\s+phone|on\\s+mobile|now)$"), "").trim()
            val invalidKeywords = setOf("me", "someone", "him", "her", "them", "lagao", "karo", "milao", "call", "phone", "dial", "kijiye", "karna", "")
            if (cleaned.lowercase(Locale.ROOT) in invalidKeywords) {
                contactName = null
            } else {
                contactName = cleaned
            }
        }

        if (contactName.isNullOrBlank()) {
            return ParsedIntent(
                intent = IntentType.CALL_CONTACT,
                rawQuery = original,
                language = lang,
                missingSlots = listOf("contactName")
            )
        }

        return ParsedIntent(
            intent = IntentType.CALL_CONTACT,
            rawQuery = original,
            language = lang,
            contactName = contactName,
            requiresConfirmation = false,
            confirmationPrompt = "Do you want to call $contactName?"
        )
    }

    // ==========================================
    // TIMER PARSING
    // ==========================================
    private fun parseTimer(original: String, lower: String, lang: String): ParsedIntent? {
        val hasTimerWord = lower.contains("timer")
        if (!hasTimerWord && !lower.contains("minute ka") && !lower.contains("ghante ka")) return null

        // Cancel / Stop / Reset
        if (lower.contains("cancel") || lower.contains("stop") || lower.contains("band karo") || lower.contains("hatao")) {
            return ParsedIntent(
                intent = IntentType.TIMER_CANCEL,
                rawQuery = original,
                language = lang
            )
        }

        // Pause
        if (lower.contains("pause") || lower.contains("rok do")) {
            return ParsedIntent(
                intent = IntentType.TIMER_PAUSE,
                rawQuery = original,
                language = lang
            )
        }

        // Resume
        if (lower.contains("resume") || lower.contains("continue") || lower.contains("phir se shuru")) {
            return ParsedIntent(
                intent = IntentType.TIMER_RESUME,
                rawQuery = original,
                language = lang
            )
        }

        // Query status
        if (lower.contains("how much time") || lower.contains("time is left") || lower.contains("timer status") || lower.contains("kitna time bacha")) {
            return ParsedIntent(
                intent = IntentType.TIMER_QUERY,
                rawQuery = original,
                language = lang
            )
        }

        // List timers
        if (lower.contains("show") || lower.contains("list") || lower.contains("all timers") || lower.contains("dikhao")) {
            return ParsedIntent(
                intent = IntentType.TIMER_LIST,
                rawQuery = original,
                language = lang
            )
        }

        // Create timer: Extract duration
        val durationSeconds = extractDuration(lower)
        if (durationSeconds != null) {
            // Optional label e.g., "for tea", "for workout"
            var label = "Timer"
            val labelPattern = Pattern.compile("for\\s+([a-zA-Z\\s]+?)(?:\\s+timer|$)", Pattern.CASE_INSENSITIVE)
            val matcher = labelPattern.matcher(original)
            if (matcher.find()) {
                val extracted = matcher.group(1).trim()
                if (!extracted.contains("minute") && !extracted.contains("second") && !extracted.contains("hour")) {
                    label = extracted.replaceFirstChar { it.uppercase() }
                }
            }

            return ParsedIntent(
                intent = IntentType.TIMER_CREATE,
                rawQuery = original,
                language = lang,
                durationSeconds = durationSeconds,
                label = label
            )
        }

        // Timer requested but duration missing
        if (lower.contains("set") || lower.contains("start") || lower.contains("laga do") || lower.contains("chalu karo")) {
            return ParsedIntent(
                intent = IntentType.TIMER_CREATE,
                rawQuery = original,
                language = lang,
                missingSlots = listOf("duration")
            )
        }

        return null
    }

    // ==========================================
    // ALARM PARSING
    // ==========================================
    private fun parseAlarm(original: String, lower: String, lang: String): ParsedIntent? {
        val isAlarmTrigger = lower.contains("alarm") || lower.contains("wake me") || lower.contains("jaga dena") || lower.contains("uthana")
        if (!isAlarmTrigger) return null

        // List / Show alarms
        if (lower.contains("show") || lower.contains("list") || lower.contains("what are my alarms") || lower.contains("dikhao")) {
            return ParsedIntent(
                intent = IntentType.ALARM_LIST,
                rawQuery = original,
                language = lang
            )
        }

        // Delete / Cancel alarm
        if (lower.contains("cancel") || lower.contains("delete") || lower.contains("remove") || lower.contains("band karo") || lower.contains("hatao")) {
            val time = extractTime(lower)
            return ParsedIntent(
                intent = IntentType.ALARM_DELETE,
                rawQuery = original,
                language = lang,
                hour = time?.first,
                minute = time?.second,
                requiresConfirmation = true,
                confirmationPrompt = if (time != null) "Cancel your alarm for ${formatTime(time.first, time.second)}?" else "Cancel all alarms?"
            )
        }

        // Create alarm
        val time = extractTime(lower)
        if (time != null) {
            val isWeekday = lower.contains("weekday") || lower.contains("weekdays") || lower.contains("somvaar se shukravaar")
            val isDaily = lower.contains("every day") || lower.contains("daily") || lower.contains("har din") || lower.contains("roz")

            val repeatPattern = when {
                isWeekday -> "WEEKDAY"
                isDaily -> "DAILY"
                else -> "NONE"
            }

            val dateCategory = extractDateCategory(lower)

            // Extract label e.g., "for work", "for gym"
            var label = "Alarm"
            val labelMatcher = Pattern.compile("(?:for|named|label)\\s+([a-zA-Z\\s]+?)(?:\\s+at|\\s+tomorrow|$)", Pattern.CASE_INSENSITIVE).matcher(original)
            if (labelMatcher.find()) {
                val candidate = labelMatcher.group(1).trim()
                if (!candidate.contains("morning") && !candidate.contains("evening") && !candidate.contains("weekday")) {
                    label = candidate.replaceFirstChar { it.uppercase() }
                }
            }

            return ParsedIntent(
                intent = IntentType.ALARM_CREATE,
                rawQuery = original,
                language = lang,
                hour = time.first,
                minute = time.second,
                repeatPattern = repeatPattern,
                dateCategory = dateCategory,
                label = label
            )
        }

        // Alarm requested without time
        if (lower.contains("set") || lower.contains("wake") || lower.contains("laga do") || lower.contains("uthana")) {
            return ParsedIntent(
                intent = IntentType.ALARM_CREATE,
                rawQuery = original,
                language = lang,
                missingSlots = listOf("time")
            )
        }

        return null
    }

    // ==========================================
    // CALENDAR PARSING
    // ==========================================
    private fun parseCalendar(original: String, lower: String, lang: String): ParsedIntent? {
        val calendarTriggers = listOf("calendar", "meeting", "event", "schedule", "class", "appointment", "dentist", "doctor")
        val isCalendar = calendarTriggers.any { lower.contains(it) }
        if (!isCalendar) return null

        // Today query: "What's on my calendar today", "Do I have anything today"
        if (lower.contains("today") && (lower.contains("what") || lower.contains("show") || lower.contains("anything") || lower.contains("list") || lower.contains("aaj kya hai"))) {
            return ParsedIntent(
                intent = IntentType.CALENDAR_TODAY,
                rawQuery = original,
                language = lang
            )
        }

        // Tomorrow query: "What's on my calendar tomorrow", "Do I have anything tomorrow"
        if (lower.contains("tomorrow") && (lower.contains("what") || lower.contains("show") || lower.contains("anything") || lower.contains("list") || lower.contains("kal kya hai"))) {
            return ParsedIntent(
                intent = IntentType.CALENDAR_TOMORROW,
                rawQuery = original,
                language = lang
            )
        }

        // List calendar
        if (lower.contains("show") || lower.contains("list") || lower.contains("events") || lower.contains("dikhao")) {
            return ParsedIntent(
                intent = IntentType.CALENDAR_LIST,
                rawQuery = original,
                language = lang
            )
        }

        // Delete / cancel event
        if (lower.contains("cancel") || lower.contains("delete") || lower.contains("remove") || lower.contains("hatao")) {
            val title = extractMeetingTitle(original, lower)
            return ParsedIntent(
                intent = IntentType.CALENDAR_DELETE,
                rawQuery = original,
                language = lang,
                title = title,
                requiresConfirmation = true,
                confirmationPrompt = "Are you sure you want to cancel the event${if (title != null) " \"$title\"" else ""}?"
            )
        }

        // Create calendar event
        val time = extractTime(lower)
        val dateMillis = extractDateEpochMillis(lower)
        val title = extractMeetingTitle(original, lower)

        val missing = mutableListOf<String>()
        if (time == null) missing.add("time")

        return ParsedIntent(
            intent = IntentType.CALENDAR_CREATE,
            rawQuery = original,
            language = lang,
            hour = time?.first,
            minute = time?.second,
            targetDateMillis = dateMillis,
            dateCategory = extractDateCategory(lower),
            title = title ?: "Meeting",
            missingSlots = missing,
            requiresConfirmation = missing.isEmpty(),
            confirmationPrompt = if (missing.isEmpty() && time != null) {
                "Schedule \"${title ?: "Meeting"}\" for ${extractDateCategory(lower) ?: "tomorrow"} at ${formatTime(time.first, time.second)}?"
            } else null
        )
    }

    // ==========================================
    // REMINDER PARSING
    // ==========================================
    private fun parseReminder(original: String, lower: String, lang: String): ParsedIntent? {
        val isReminderTrigger = lower.contains("remind") || lower.contains("reminder") || lower.contains("yaad dilana") || lower.contains("yaad rakhna")
        if (!isReminderTrigger) return null

        // List reminders
        if (lower.contains("show") || lower.contains("list") || lower.contains("what are my reminders") || lower.contains("dikhao")) {
            return ParsedIntent(
                intent = IntentType.REMINDER_LIST,
                rawQuery = original,
                language = lang
            )
        }

        // Delete reminder
        if (lower.contains("delete") || lower.contains("remove") || lower.contains("cancel") || lower.contains("hatao")) {
            val title = extractReminderTask(original, lower)
            return ParsedIntent(
                intent = IntentType.REMINDER_DELETE,
                rawQuery = original,
                language = lang,
                title = title,
                requiresConfirmation = true,
                confirmationPrompt = "Are you sure you want to delete ${if (title != null) "\"$title\" reminder" else "this reminder"}?"
            )
        }

        // Create reminder
        val time = extractTime(lower)
        val dateMillis = extractDateEpochMillis(lower)
        val task = extractReminderTask(original, lower)

        val missing = mutableListOf<String>()
        if (task.isNullOrBlank()) missing.add("task")
        if (time == null) missing.add("time")

        return ParsedIntent(
            intent = IntentType.REMINDER_CREATE,
            rawQuery = original,
            language = lang,
            hour = time?.first,
            minute = time?.second,
            targetDateMillis = dateMillis,
            dateCategory = extractDateCategory(lower),
            title = task ?: "Reminder",
            missingSlots = missing
        )
    }

    // ==========================================
    // EXTRACTION HELPERS
    // ==========================================

    fun extractTime(text: String): Pair<Int, Int>? {
        // 1. Check Hindi formats e.g. "7 baje", "shaam 8 baje", "subah 6:30", "dopahar 2 baje"
        val hindiPattern = Pattern.compile("(subah|shaam|raat|dopahar)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*baje", Pattern.CASE_INSENSITIVE)
        val hindiMatcher = hindiPattern.matcher(text)
        if (hindiMatcher.find()) {
            val period = hindiMatcher.group(1)?.lowercase() ?: ""
            var h = hindiMatcher.group(2)?.toIntOrNull() ?: 0
            val m = hindiMatcher.group(3)?.toIntOrNull() ?: 0
            if (period.contains("shaam") || period.contains("raat") || period.contains("dopahar")) {
                if (h < 12) h += 12
            }
            return Pair(h, m)
        }

        // 2. Standard 12-hour AM/PM: e.g. "7 AM", "6:30 pm", "11:45am", "7:00 morning"
        val amPmPattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|in the morning|in the evening|in the afternoon|at night|morning|evening)", Pattern.CASE_INSENSITIVE)
        val amPmMatcher = amPmPattern.matcher(text)
        if (amPmMatcher.find()) {
            var h = amPmMatcher.group(1)?.toIntOrNull() ?: 0
            val m = amPmMatcher.group(2)?.toIntOrNull() ?: 0
            val suffix = amPmMatcher.group(3)?.lowercase() ?: ""

            if (suffix.contains("pm") || suffix.contains("evening") || suffix.contains("night") || suffix.contains("afternoon")) {
                if (h < 12) h += 12
            } else if (suffix.contains("am") || suffix.contains("morning")) {
                if (h == 12) h = 0
            }
            return Pair(h, m)
        }

        // 3. "at 7" or "at 6:30" or "for 7"
        val atPattern = Pattern.compile("(?:at|for|around)\\s+(\\d{1,2})(?::(\\d{2}))?(?!\\s*(?:minutes?|hours?|seconds?|min|sec|days?))", Pattern.CASE_INSENSITIVE)
        val atMatcher = atPattern.matcher(text)
        if (atMatcher.find()) {
            var h = atMatcher.group(1)?.toIntOrNull() ?: 0
            val m = atMatcher.group(2)?.toIntOrNull() ?: 0

            // Contextual infer: if "evening" or "tonight" appears in string, adjust to PM
            if (text.contains("evening") || text.contains("tonight") || text.contains("shaam") || text.contains("raat")) {
                if (h < 12) h += 12
            }
            return Pair(h, m)
        }

        // 4. Relative time: "in 20 minutes", "after 2 hours"
        val relMin = Pattern.compile("(?:in|after)\\s+(\\d+)\\s*(?:minutes?|mins?)", Pattern.CASE_INSENSITIVE).matcher(text)
        if (relMin.find()) {
            val minsToAdd = relMin.group(1).toIntOrNull() ?: 0
            val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minsToAdd) }
            return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        val relHour = Pattern.compile("(?:in|after)\\s+(\\d+)\\s*(?:hours?|hrs?)", Pattern.CASE_INSENSITIVE).matcher(text)
        if (relHour.find()) {
            val hoursToAdd = relHour.group(1).toIntOrNull() ?: 0
            val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, hoursToAdd) }
            return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }

        return null
    }

    fun extractDuration(text: String): Long? {
        var totalSec: Long = 0
        var matched = false

        // Hours
        val hPattern = Pattern.compile("(\\d+)\\s*(?:hours?|hrs?|ghanta|ghante)", Pattern.CASE_INSENSITIVE)
        val hMatcher = hPattern.matcher(text)
        if (hMatcher.find()) {
            val h = hMatcher.group(1).toLongOrNull() ?: 0
            totalSec += h * 3600
            matched = true
        }

        // Hindi aadha ghanta (half hour)
        if (text.contains("aadha ghanta") || text.contains("half an hour") || text.contains("half hour")) {
            totalSec += 1800
            matched = true
        }

        // Minutes
        val mPattern = Pattern.compile("(\\d+)\\s*(?:minutes?|mins?|minute)", Pattern.CASE_INSENSITIVE)
        val mMatcher = mPattern.matcher(text)
        if (mMatcher.find()) {
            val m = mMatcher.group(1).toLongOrNull() ?: 0
            totalSec += m * 60
            matched = true
        }

        // Seconds
        val sPattern = Pattern.compile("(\\d+)\\s*(?:seconds?|secs?|second)", Pattern.CASE_INSENSITIVE)
        val sMatcher = sPattern.matcher(text)
        if (sMatcher.find()) {
            val s = sMatcher.group(1).toLongOrNull() ?: 0
            totalSec += s
            matched = true
        }

        return if (matched && totalSec > 0) totalSec else null
    }

    fun extractDateCategory(text: String): String? {
        return when {
            text.contains("tomorrow") || text.contains("kal") -> "tomorrow"
            text.contains("today") || text.contains("aaj") || text.contains("tonight") || text.contains("this evening") -> "today"
            text.contains("next monday") || text.contains("monday") || text.contains("somvaar") -> "monday"
            text.contains("next friday") || text.contains("friday") || text.contains("shukravaar") -> "friday"
            text.contains("parso") || text.contains("day after tomorrow") -> "day_after_tomorrow"
            else -> null
        }
    }

    fun extractDateEpochMillis(text: String): Long {
        val cal = Calendar.getInstance()
        when {
            text.contains("tomorrow") || text.contains("kal") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            text.contains("parso") || text.contains("day after tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 2)
            }
            text.contains("next monday") || (text.contains("monday") && !text.contains("every monday")) -> {
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            text.contains("next friday") || (text.contains("friday") && !text.contains("every friday")) -> {
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return cal.timeInMillis
    }

    private fun extractReminderTask(original: String, lower: String): String? {
        // Patterns:
        // "remind me to study at 8 PM" -> "study"
        // "remind me tomorrow at 10 AM to submit the assignment" -> "submit the assignment"
        // "kal 8 baje mujhe assignment submit karne ka reminder dena" -> "assignment submit karne"
        // "remind me every Monday to call Dad" -> "call Dad"
        val p1 = Pattern.compile("remind\\s+me\\s+(?:tomorrow\\s+|today\\s+|at\\s+\\d+.*?\\s+)?to\\s+([a-zA-Z0-9\\s\\.]+?)(?:\\s+at\\s+\\d+|\\s+tomorrow|\\s+today|\\s+every|$)", Pattern.CASE_INSENSITIVE)
        val m1 = p1.matcher(original)
        if (m1.find()) {
            val task = m1.group(1).trim()
            if (task.isNotEmpty()) return task.replaceFirstChar { it.uppercase() }
        }

        val p2 = Pattern.compile("remind\\s+me\\s+about\\s+([a-zA-Z0-9\\s\\.]+?)(?:\\s+at|\\s+tomorrow|\\s+today|$)", Pattern.CASE_INSENSITIVE)
        val m2 = p2.matcher(original)
        if (m2.find()) {
            return m2.group(1).trim().replaceFirstChar { it.uppercase() }
        }

        // Hindi: "... assignment submit karne ka reminder"
        val p3 = Pattern.compile("(?:mujhe\\s+)?([a-zA-Z0-9\\s]+?)(?:\\s+karne\\s+ka\\s+reminder|\\s+yaad\\s+dilana)", Pattern.CASE_INSENSITIVE)
        val m3 = p3.matcher(original)
        if (m3.find()) {
            val cand = m3.group(1).replace("kal", "").replace("aaj", "").replace("shaam", "").trim()
            if (cand.isNotEmpty()) return cand.replaceFirstChar { it.uppercase() }
        }

        return null
    }

    private fun extractMeetingTitle(original: String, lower: String): String? {
        // E.g. "Create a meeting tomorrow at 3 PM" -> "Meeting"
        // "Add a dentist appointment next Monday at 11" -> "Dentist Appointment"
        // "Schedule a project meeting from 2 to 3 PM tomorrow" -> "Project Meeting"
        // "Add a class on Monday at 10 AM" -> "Class"
        val patterns = listOf(
            Pattern.compile("(?:create|schedule|add)\\s+(?:a|an)?\\s*([a-zA-Z0-9\\s]+?)(?:\\s+tomorrow|\\s+today|\\s+on\\s+|\\s+at\\s+|\\s+from\\s+|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([a-zA-Z0-9\\s]+?)\\s+(?:meeting|appointment|class)", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(original)
            if (m.find()) {
                val candidate = m.group(1).trim()
                if (candidate.isNotEmpty() && !candidate.equals("meeting", ignoreCase = true)) {
                    return candidate.replaceFirstChar { it.uppercase() }
                }
            }
        }
        if (lower.contains("dentist")) return "Dentist Appointment"
        if (lower.contains("class")) return "Class"
        if (lower.contains("project")) return "Project Meeting"
        return "Meeting"
    }

    fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val h = when (val display = hour % 12) {
            0 -> 12
            else -> display
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", h, minute, amPm)
    }
}
