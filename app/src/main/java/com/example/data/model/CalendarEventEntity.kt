package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val location: String = "",
    val attendees: String = "",
    val nativeEventId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(startEpochMillis))
    }

    fun getFormattedTimeRange(): String {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startStr = timeFormat.format(Date(startEpochMillis))
        val endStr = timeFormat.format(Date(endEpochMillis))
        return "$startStr - $endStr"
    }
}
