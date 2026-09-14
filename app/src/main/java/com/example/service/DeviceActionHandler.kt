package com.example.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarEventEntity
import com.example.data.model.CommandStatus
import com.example.data.model.ContactEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.ReminderPriority
import com.example.data.model.TimerEntity
import com.example.data.repository.AssistantRepository
import com.example.engine.IntentType
import com.example.engine.NlpEngine
import com.example.engine.ParsedIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class ActionResult {
    data class Success(val responseMessage: String, val extraData: Any? = null) : ActionResult()
    data class RequiresConfirmation(val prompt: String, val pendingIntent: ParsedIntent) : ActionResult()
    data class AmbiguousContacts(val prompt: String, val matchedContacts: List<ContactEntity>) : ActionResult()
    data class MissingInformation(val prompt: String, val missingSlot: String) : ActionResult()
    data class Error(val errorMessage: String) : ActionResult()
}

class DeviceActionHandler(
    private val context: Context,
    private val repository: AssistantRepository,
    private val alarmScheduler: SystemAlarmScheduler
) {

    suspend fun execute(parsed: ParsedIntent): ActionResult {
        // 1. Check for missing required parameters
        if (parsed.missingSlots.isNotEmpty()) {
            val slot = parsed.missingSlots.first()
            val prompt = when (slot) {
                "time" -> "What time should I set?"
                "task" -> "What should I remind you about?"
                "duration" -> "For how long should I set the timer?"
                "contactName" -> if (parsed.language == "hi" || parsed.language == "hinglish") "Aap kise call karna chahte hain?" else "Who would you like to call?"
                else -> "Could you please provide the missing details?"
            }
            return ActionResult.MissingInformation(prompt, slot)
        }

        // 2. Check for confirmation (CALL_CONTACT resolves matches and checks existence before confirming)
        if (parsed.requiresConfirmation && parsed.confirmationPrompt != null && parsed.intent != IntentType.CALL_CONTACT) {
            return ActionResult.RequiresConfirmation(parsed.confirmationPrompt, parsed)
        }

        // 3. Route to controlled action registry
        return when (parsed.intent) {
            IntentType.ALARM_CREATE -> handleAlarmCreate(parsed)
            IntentType.ALARM_DELETE -> handleAlarmDelete(parsed)
            IntentType.ALARM_LIST -> handleAlarmList()
            IntentType.ALARM_TOGGLE -> handleAlarmList()

            IntentType.TIMER_CREATE -> handleTimerCreate(parsed)
            IntentType.TIMER_CANCEL -> handleTimerCancel()
            IntentType.TIMER_PAUSE -> handleTimerPause()
            IntentType.TIMER_RESUME -> handleTimerResume()
            IntentType.TIMER_LIST, IntentType.TIMER_QUERY -> handleTimerQuery()

            IntentType.REMINDER_CREATE -> handleReminderCreate(parsed)
            IntentType.REMINDER_DELETE -> handleReminderDelete(parsed)
            IntentType.REMINDER_LIST -> handleReminderList()

            IntentType.CALENDAR_CREATE -> handleCalendarCreate(parsed)
            IntentType.CALENDAR_DELETE -> handleCalendarDelete(parsed)
            IntentType.CALENDAR_UPDATE -> handleCalendarList()
            IntentType.CALENDAR_LIST -> handleCalendarList()
            IntentType.CALENDAR_TODAY -> handleCalendarToday()
            IntentType.CALENDAR_TOMORROW -> handleCalendarTomorrow()

            IntentType.CALL_CONTACT -> handleCallContact(parsed)
            IntentType.CALL_NUMBER -> handleCallNumber(parsed)
            IntentType.CONTACTS_LIST -> handleContactsList()

            IntentType.TIME_QUERY -> handleTimeQuery()
            IntentType.DATE_QUERY -> handleDateQuery()
            IntentType.HELP -> handleHelp()
            IntentType.UNKNOWN -> ActionResult.Error(
                "I'm not sure how to help with that yet. Try saying 'Set an alarm for 7 AM', 'Set a timer for 15 minutes', or 'Remind me to study tomorrow'."
            )
        }
    }

    // ==========================================
    // ALARMS
    // ==========================================
    private suspend fun handleAlarmCreate(parsed: ParsedIntent): ActionResult {
        val h = parsed.hour ?: 7
        val m = parsed.minute ?: 0
        val isDaily = parsed.repeatPattern == "DAILY"
        val isWeekday = parsed.repeatPattern == "WEEKDAY"
        val label = parsed.label ?: "Alarm"

        val alarm = AlarmEntity(
            hour = h,
            minute = m,
            label = label,
            isDaily = isDaily,
            isWeekdayOnly = isWeekday,
            isEnabled = true
        )
        val id = repository.insertAlarm(alarm)
        alarmScheduler.scheduleAlarm(alarm.copy(id = id))

        val formattedTime = NlpEngine.formatTime(h, m)
        val repeatText = when {
            isDaily -> " every day"
            isWeekday -> " every weekday"
            parsed.dateCategory == "tomorrow" -> " for tomorrow"
            else -> ""
        }
        val response = "Done. Alarm set for $formattedTime$repeatText."
        repository.recordHistory(parsed.rawQuery, "ALARM_CREATE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response, alarm.copy(id = id))
    }

    private suspend fun handleAlarmDelete(parsed: ParsedIntent): ActionResult {
        val count = if (parsed.hour != null) {
            repository.deleteAlarmByTime(parsed.hour, parsed.minute ?: 0)
        } else {
            0
        }
        val response = if (count > 0) {
            "Cancelled your ${NlpEngine.formatTime(parsed.hour ?: 0, parsed.minute ?: 0)} alarm."
        } else {
            "I couldn't find an alarm matching that time."
        }
        repository.recordHistory(parsed.rawQuery, "ALARM_DELETE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response)
    }

    private suspend fun handleAlarmList(): ActionResult {
        val response = "Here are your scheduled alarms."
        return ActionResult.Success(response)
    }

    // ==========================================
    // TIMERS
    // ==========================================
    private suspend fun handleTimerCreate(parsed: ParsedIntent): ActionResult {
        val duration = parsed.durationSeconds ?: 300L
        val label = parsed.label ?: "Timer"

        val timer = TimerEntity(
            label = label,
            totalSeconds = duration,
            remainingSeconds = duration,
            isRunning = true,
            isPaused = false
        )
        val id = repository.insertTimer(timer)

        val mins = duration / 60
        val secs = duration % 60
        val timeDesc = when {
            mins > 0 && secs > 0 -> "$mins minutes and $secs seconds"
            mins > 0 -> "$mins minute${if (mins > 1) "s" else ""}"
            else -> "$secs seconds"
        }
        val response = "Timer started for $timeDesc."
        repository.recordHistory(parsed.rawQuery, "TIMER_CREATE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response, timer.copy(id = id))
    }

    private suspend fun handleTimerCancel(): ActionResult {
        repository.deleteAllTimers()
        val response = "Timer cancelled."
        repository.recordHistory("Cancel timer", "TIMER_CANCEL", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response)
    }

    private suspend fun handleTimerPause(): ActionResult {
        val response = "Timer paused."
        return ActionResult.Success(response)
    }

    private suspend fun handleTimerResume(): ActionResult {
        val response = "Timer resumed."
        return ActionResult.Success(response)
    }

    private suspend fun handleTimerQuery(): ActionResult {
        val response = "Showing your active timers."
        return ActionResult.Success(response)
    }

    // ==========================================
    // REMINDERS
    // ==========================================
    private suspend fun handleReminderCreate(parsed: ParsedIntent): ActionResult {
        val title = parsed.title ?: "Reminder"
        val cal = Calendar.getInstance()
        if (parsed.targetDateMillis != null) {
            cal.timeInMillis = parsed.targetDateMillis
        }
        if (parsed.hour != null) {
            cal.set(Calendar.HOUR_OF_DAY, parsed.hour)
            cal.set(Calendar.MINUTE, parsed.minute ?: 0)
        }

        val timeFormatted = if (parsed.hour != null) NlpEngine.formatTime(parsed.hour, parsed.minute ?: 0) else "8:00 PM"
        val reminder = ReminderEntity(
            title = title,
            targetDateMillis = cal.timeInMillis,
            targetTimeFormatted = timeFormatted,
            priority = ReminderPriority.MEDIUM,
            isCompleted = false
        )
        val id = repository.insertReminder(reminder)

        val response = "Done. I'll remind you to $title at $timeFormatted."
        repository.recordHistory(parsed.rawQuery, "REMINDER_CREATE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response, reminder.copy(id = id))
    }

    private suspend fun handleReminderDelete(parsed: ParsedIntent): ActionResult {
        val count = if (!parsed.title.isNullOrBlank()) {
            repository.deleteReminderByTitle(parsed.title)
        } else {
            0
        }
        val response = if (count > 0) {
            "Deleted reminder for \"${parsed.title}\"."
        } else {
            "No matching reminder found to delete."
        }
        repository.recordHistory(parsed.rawQuery, "REMINDER_DELETE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response)
    }

    private suspend fun handleReminderList(): ActionResult {
        return ActionResult.Success("Here are your current reminders.")
    }

    // ==========================================
    // CALENDAR
    // ==========================================
    private suspend fun handleCalendarCreate(parsed: ParsedIntent): ActionResult {
        val title = parsed.title ?: "Meeting"
        val calStart = Calendar.getInstance()
        if (parsed.targetDateMillis != null) {
            calStart.timeInMillis = parsed.targetDateMillis
        }
        val h = parsed.hour ?: 10
        val m = parsed.minute ?: 0
        calStart.set(Calendar.HOUR_OF_DAY, h)
        calStart.set(Calendar.MINUTE, m)

        val calEnd = (calStart.clone() as Calendar).apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }

        val event = CalendarEventEntity(
            title = title,
            startEpochMillis = calStart.timeInMillis,
            endEpochMillis = calEnd.timeInMillis,
            location = parsed.location ?: "Office / Remote",
            description = "Created via Voice Assistant"
        )
        val id = repository.insertEvent(event)

        val formattedTime = NlpEngine.formatTime(h, m)
        val response = "Created event \"$title\" for $formattedTime."
        repository.recordHistory(parsed.rawQuery, "CALENDAR_CREATE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response, event.copy(id = id))
    }

    private suspend fun handleCalendarToday(): ActionResult {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis

        val response = "Checking your agenda for today."
        return ActionResult.Success(response)
    }

    private suspend fun handleCalendarTomorrow(): ActionResult {
        val response = "Here is what you have scheduled for tomorrow."
        return ActionResult.Success(response)
    }

    private suspend fun handleCalendarList(): ActionResult {
        return ActionResult.Success("Here is your calendar schedule.")
    }

    private suspend fun handleCalendarDelete(parsed: ParsedIntent): ActionResult {
        val count = if (!parsed.title.isNullOrBlank()) {
            repository.deleteEventByTitle(parsed.title)
        } else {
            0
        }
        val response = if (count > 0) {
            "Removed event \"${parsed.title}\" from your calendar."
        } else {
            "No matching event found to cancel."
        }
        repository.recordHistory(parsed.rawQuery, "CALENDAR_DELETE", response, CommandStatus.COMPLETED)
        return ActionResult.Success(response)
    }

    // ==========================================
    // CALLING & CONTACTS
    // ==========================================
    private fun normalizeContactQuery(name: String): List<String> {
        val lower = name.lowercase(Locale.ROOT).trim()
        val queries = mutableListOf(name.trim())
        when (lower) {
            "mummy", "maa", "mataji", "mother" -> queries.add("Mom")
            "papa", "pitaji", "father", "bapuji" -> queries.add("Dad")
            "mom" -> queries.add("Mummy")
            "dad" -> queries.add("Papa")
        }
        return queries
    }

    private suspend fun handleCallContact(parsed: ParsedIntent): ActionResult {
        val name = parsed.contactName ?: return ActionResult.Error("Please specify who you want to call.")

        val queries = normalizeContactQuery(name)
        var matches = emptyList<ContactEntity>()
        for (q in queries) {
            matches = repository.searchContacts(q)
            if (matches.isNotEmpty()) break
        }

        if (matches.isEmpty()) {
            val response = if (parsed.language == "hi" || parsed.language == "hinglish") {
                "$name naam ka koi contact nahi mila."
            } else {
                "I couldn't find a contact named $name in your contacts."
            }
            repository.recordHistory(parsed.rawQuery, "CALL_CONTACT", response, CommandStatus.FAILED)
            return ActionResult.Error(response)
        }

        if (matches.size > 1) {
            val prompt = if (parsed.language == "hi" || parsed.language == "hinglish") {
                "Mujhe $name naam ke ${matches.size} contacts mile. Aap kise call karna chahte hain?"
            } else {
                "I found ${matches.size} contacts matching \"$name\". Which one would you like to call?"
            }
            return ActionResult.AmbiguousContacts(prompt, matches)
        }

        val contact = matches.first()
        if (parsed.requiresConfirmation) {
            return ActionResult.RequiresConfirmation(
                "Call ${contact.name} (${contact.phoneNumber})?",
                parsed.copy(phoneNumber = contact.phoneNumber, contactName = contact.name, requiresConfirmation = false)
            )
        }
        return initiateRealCall(contact.name, contact.phoneNumber, parsed.rawQuery)
    }

    private suspend fun handleCallNumber(parsed: ParsedIntent): ActionResult {
        val number = parsed.phoneNumber ?: return ActionResult.Error("Please specify a phone number to call.")
        if (parsed.requiresConfirmation) {
            return ActionResult.RequiresConfirmation(
                "Call $number?",
                parsed.copy(requiresConfirmation = false)
            )
        }
        return initiateRealCall(number, number, parsed.rawQuery)
    }

    private suspend fun handleContactsList(): ActionResult {
        val contacts = repository.allContacts.first()
        val count = contacts.size
        val prompt = if (count > 0) {
            "You have $count contacts saved. Showing your contacts."
        } else {
            "You don't have any contacts saved yet. You can add one or sync from your device."
        }
        return ActionResult.Success(prompt, contacts)
    }

    fun initiateRealCall(displayName: String, phoneNumber: String, rawQuery: String): ActionResult {
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanNumber.isBlank()) {
            return ActionResult.Error("Invalid phone number format: $phoneNumber")
        }

        val hasCallPhonePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val telUri = Uri.fromParts("tel", cleanNumber, null)

        val intent = if (hasCallPhonePermission) {
            Intent(Intent.ACTION_CALL, telUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            // Safe fallback to dialer (never crashes, works across all devices & emulators)
            Intent(Intent.ACTION_DIAL, telUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        return try {
            context.startActivity(intent)
            val actionType = if (hasCallPhonePermission) "Calling" else "Opening dialer for"
            val response = "$actionType $displayName ($phoneNumber)..."
            CoroutineScope(Dispatchers.IO).launch {
                repository.recordHistory(rawQuery, "CALL", response, CommandStatus.COMPLETED)
            }
            ActionResult.Success(response, phoneNumber)
        } catch (e: SecurityException) {
            // Fallback to ACTION_DIAL if ACTION_CALL failed security check
            try {
                val fallbackIntent = Intent(Intent.ACTION_DIAL, telUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                val response = "Opening dialer for $displayName ($phoneNumber)..."
                CoroutineScope(Dispatchers.IO).launch {
                    repository.recordHistory(rawQuery, "CALL", response, CommandStatus.COMPLETED)
                }
                ActionResult.Success(response, phoneNumber)
            } catch (fallbackEx: Exception) {
                ActionResult.Error("Unable to open dialer: ${fallbackEx.localizedMessage}")
            }
        } catch (e: ActivityNotFoundException) {
            ActionResult.Error("No phone or dialer application is available on this device to place the call.")
        } catch (e: Exception) {
            ActionResult.Error("Unable to initiate call: ${e.localizedMessage}")
        }
    }

    // ==========================================
    // UTILITY QUERIES
    // ==========================================
    private fun handleTimeQuery(): ActionResult {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val currentTime = timeFormat.format(Date())
        val response = "It is currently $currentTime."
        return ActionResult.Success(response)
    }

    private fun handleDateQuery(): ActionResult {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        val response = "Today is $currentDate."
        return ActionResult.Success(response)
    }

    private fun handleHelp(): ActionResult {
        val response = "You can ask me to set alarms, start timers, create reminders, manage calendar meetings, or call contacts. Try saying 'Set an alarm for 7 AM' or 'Kal 20 minute ka timer laga do'."
        return ActionResult.Success(response)
    }
}
