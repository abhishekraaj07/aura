package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CommandStatus {
    COMPLETED,
    FAILED,
    PENDING_CONFIRMATION,
    CANCELLED
}

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val intent: String,
    val result: String,
    val status: CommandStatus = CommandStatus.COMPLETED,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
