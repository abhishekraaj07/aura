package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReminderPriority {
    LOW, MEDIUM, HIGH
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val targetDateMillis: Long,
    val targetTimeFormatted: String = "",
    val repeatRule: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY
    val priority: ReminderPriority = ReminderPriority.MEDIUM,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val dateStr = sdf.format(Date(targetDateMillis))
        return if (targetTimeFormatted.isNotEmpty()) "$dateStr at $targetTimeFormatted" else dateStr
    }
}
