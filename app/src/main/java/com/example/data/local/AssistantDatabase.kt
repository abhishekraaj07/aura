package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.AlarmEntity
import com.example.data.model.CalendarEventEntity
import com.example.data.model.CommandHistoryEntity
import com.example.data.model.ContactEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.TimerEntity

@Database(
    entities = [
        AlarmEntity::class,
        TimerEntity::class,
        ReminderEntity::class,
        CalendarEventEntity::class,
        ContactEntity::class,
        CommandHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao
    abstract fun reminderDao(): ReminderDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun contactDao(): ContactDao
    abstract fun commandHistoryDao(): CommandHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AssistantDatabase? = null

        fun getDatabase(context: Context): AssistantDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AssistantDatabase::class.java,
                    "voice_assistant_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
