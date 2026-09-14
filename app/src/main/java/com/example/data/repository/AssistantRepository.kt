package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.data.local.AssistantDatabase
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarEventEntity
import com.example.data.model.CommandHistoryEntity
import com.example.data.model.CommandStatus
import com.example.data.model.ContactEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.ReminderPriority
import com.example.data.model.TimerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

class AssistantRepository(private val database: AssistantDatabase) {

    // Alarms
    val allAlarms: Flow<List<AlarmEntity>> = database.alarmDao().getAllAlarms()
    val enabledAlarms: Flow<List<AlarmEntity>> = database.alarmDao().getEnabledAlarms()

    suspend fun insertAlarm(alarm: AlarmEntity): Long = database.alarmDao().insertAlarm(alarm)
    suspend fun updateAlarm(alarm: AlarmEntity) = database.alarmDao().updateAlarm(alarm)
    suspend fun deleteAlarm(alarm: AlarmEntity) = database.alarmDao().deleteAlarm(alarm)
    suspend fun deleteAlarmById(id: Long) = database.alarmDao().deleteById(id)
    suspend fun deleteAlarmByTime(hour: Int, minute: Int): Int = database.alarmDao().deleteByTime(hour, minute)
    suspend fun findAlarmByTime(hour: Int, minute: Int): AlarmEntity? = database.alarmDao().findAlarmByTime(hour, minute)

    // Timers
    val allTimers: Flow<List<TimerEntity>> = database.timerDao().getAllTimers()
    val activeTimer: Flow<TimerEntity?> = database.timerDao().getActiveTimer()

    suspend fun insertTimer(timer: TimerEntity): Long = database.timerDao().insertTimer(timer)
    suspend fun updateTimer(timer: TimerEntity) = database.timerDao().updateTimer(timer)
    suspend fun deleteTimer(timer: TimerEntity) = database.timerDao().deleteTimer(timer)
    suspend fun deleteTimerById(id: Long) = database.timerDao().deleteById(id)
    suspend fun deleteAllTimers() = database.timerDao().deleteAllTimers()

    // Reminders
    val allReminders: Flow<List<ReminderEntity>> = database.reminderDao().getAllReminders()
    val pendingReminders: Flow<List<ReminderEntity>> = database.reminderDao().getPendingReminders()

    suspend fun insertReminder(reminder: ReminderEntity): Long = database.reminderDao().insertReminder(reminder)
    suspend fun updateReminder(reminder: ReminderEntity) = database.reminderDao().updateReminder(reminder)
    suspend fun deleteReminder(reminder: ReminderEntity) = database.reminderDao().deleteReminder(reminder)
    suspend fun deleteReminderById(id: Long) = database.reminderDao().deleteById(id)
    suspend fun findRemindersByTitle(title: String): List<ReminderEntity> = database.reminderDao().findRemindersByTitle(title)
    suspend fun deleteReminderByTitle(title: String): Int = database.reminderDao().deleteByTitle(title)

    // Calendar
    val allEvents: Flow<List<CalendarEventEntity>> = database.calendarEventDao().getAllEvents()

    fun getEventsForRange(startOfDay: Long, endOfDay: Long): Flow<List<CalendarEventEntity>> =
        database.calendarEventDao().getEventsForRange(startOfDay, endOfDay)

    fun getUpcomingEvents(now: Long): Flow<List<CalendarEventEntity>> =
        database.calendarEventDao().getUpcomingEvents(now)

    suspend fun insertEvent(event: CalendarEventEntity): Long = database.calendarEventDao().insertEvent(event)
    suspend fun updateEvent(event: CalendarEventEntity) = database.calendarEventDao().updateEvent(event)
    suspend fun deleteEvent(event: CalendarEventEntity) = database.calendarEventDao().deleteEvent(event)
    suspend fun deleteEventById(id: Long) = database.calendarEventDao().deleteById(id)
    suspend fun findEventByTitle(title: String): CalendarEventEntity? = database.calendarEventDao().findEventByTitle(title)
    suspend fun deleteEventByTitle(title: String): Int = database.calendarEventDao().deleteByTitle(title)

    // Contacts
    val allContacts: Flow<List<ContactEntity>> = database.contactDao().getAllContacts()
    val favoriteContacts: Flow<List<ContactEntity>> = database.contactDao().getFavoriteContacts()

    suspend fun searchContacts(name: String): List<ContactEntity> = database.contactDao().searchContacts(name)
    suspend fun insertContact(contact: ContactEntity): Long = database.contactDao().insertContact(contact)
    suspend fun updateContact(contact: ContactEntity) = database.contactDao().updateContact(contact)
    suspend fun deleteContact(contact: ContactEntity) = database.contactDao().deleteContact(contact)

    suspend fun importDeviceContacts(context: Context): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return 0
        }
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val cursor = resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE
                    ),
                    null,
                    null,
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                ) ?: return@withContext 0

                val imported = mutableListOf<ContactEntity>()
                cursor.use { c ->
                    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    while (c.moveToNext()) {
                        val name = if (nameIdx != -1) c.getString(nameIdx) else null
                        val num = if (numIdx != -1) c.getString(numIdx) else null
                        val type = if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        val rel = when (type) {
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                            else -> "Device"
                        }
                        if (!name.isNullOrBlank() && !num.isNullOrBlank()) {
                            imported.add(
                                ContactEntity(
                                    name = name.trim(),
                                    phoneNumber = num.trim(),
                                    relationship = rel,
                                    isDeviceContact = true
                                )
                            )
                        }
                    }
                }
                if (imported.isNotEmpty()) {
                    database.contactDao().insertContacts(imported)
                }
                imported.size
            } catch (e: Exception) {
                0
            }
        }
    }

    // Command History
    val allHistory: Flow<List<CommandHistoryEntity>> = database.commandHistoryDao().getAllHistory()
    fun getRecentHistory(limit: Int): Flow<List<CommandHistoryEntity>> = database.commandHistoryDao().getRecentHistory(limit)

    suspend fun recordHistory(
        command: String,
        intent: String,
        result: String,
        status: CommandStatus = CommandStatus.COMPLETED
    ): Long {
        return database.commandHistoryDao().insertHistory(
            CommandHistoryEntity(
                command = command,
                intent = intent,
                result = result,
                status = status
            )
        )
    }

    suspend fun clearHistory() = database.commandHistoryDao().clearAllHistory()

    // Prepopulate initial seed data if empty
    suspend fun seedInitialDataIfEmpty() {
        if (database.contactDao().getCount() == 0) {
            val defaultContacts = listOf(
                ContactEntity(name = "Rahul Sharma", phoneNumber = "+1-555-0192", relationship = "Colleague", isFavorite = true),
                ContactEntity(name = "Mom", phoneNumber = "+1-555-0144", relationship = "Family", isFavorite = true),
                ContactEntity(name = "Dad", phoneNumber = "+1-555-0187", relationship = "Family", isFavorite = true),
                ContactEntity(name = "Alex Johnson", phoneNumber = "+1-555-0112", relationship = "Friend", isFavorite = false),
                ContactEntity(name = "Alex Rivera", phoneNumber = "+1-555-0199", relationship = "Designer", isFavorite = false),
                ContactEntity(name = "Doctor Smith", phoneNumber = "+1-555-0177", relationship = "Healthcare", isFavorite = false)
            )
            database.contactDao().insertContacts(defaultContacts)

            // Seed a starter alarm
            database.alarmDao().insertAlarm(
                AlarmEntity(hour = 7, minute = 0, label = "Morning Workout", isDaily = false, isWeekdayOnly = true)
            )

            // Seed a starter reminder
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
            }
            database.reminderDao().insertReminder(
                ReminderEntity(
                    title = "Submit project assignment",
                    description = "Upload final report to student portal",
                    targetDateMillis = cal.timeInMillis,
                    targetTimeFormatted = "8:00 PM",
                    priority = ReminderPriority.HIGH
                )
            )

            // Seed a starter calendar event
            val eventCalStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 0)
            }
            val eventCalEnd = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 16)
                set(Calendar.MINUTE, 0)
            }
            database.calendarEventDao().insertEvent(
                CalendarEventEntity(
                    title = "Project Sync Meeting",
                    description = "Discuss sprint deliverables and system integration",
                    startEpochMillis = eventCalStart.timeInMillis,
                    endEpochMillis = eventCalEnd.timeInMillis,
                    location = "Conference Room B / Video Call"
                )
            )

            // Seed starter history
            database.commandHistoryDao().insertHistory(
                CommandHistoryEntity(
                    command = "Set alarm for 7 AM",
                    intent = "ALARM_CREATE",
                    result = "Alarm set for 7:00 AM on weekdays.",
                    status = CommandStatus.COMPLETED
                )
            )
        }
    }
}
