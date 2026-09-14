package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarm",
    val isEnabled: Boolean = true,
    val repeatDays: String = "", // Comma-separated: "MON,TUE,WED,THU,FRI"
    val isDaily: Boolean = false,
    val isWeekdayOnly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        val displayMinute = String.format("%02d", minute)
        return "$displayHour:$displayMinute $amPm"
    }

    fun getRepeatSummary(): String {
        return when {
            isDaily -> "Every day"
            isWeekdayOnly -> "Weekdays (Mon-Fri)"
            repeatDays.isNotEmpty() -> repeatDays.replace(",", ", ")
            else -> "Once"
        }
    }
}
