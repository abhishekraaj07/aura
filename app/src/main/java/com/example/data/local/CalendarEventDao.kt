package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startEpochMillis ASC")
    fun getAllEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE startEpochMillis >= :startOfDay AND startEpochMillis < :endOfDay ORDER BY startEpochMillis ASC")
    fun getEventsForRange(startOfDay: Long, endOfDay: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE startEpochMillis >= :now ORDER BY startEpochMillis ASC")
    fun getUpcomingEvents(now: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE LOWER(title) LIKE '%' || LOWER(:titleKeyword) || '%' LIMIT 1")
    suspend fun findEventByTitle(titleKeyword: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity): Long

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM calendar_events WHERE LOWER(title) LIKE '%' || LOWER(:titleKeyword) || '%'")
    suspend fun deleteByTitle(titleKeyword: String): Int
}
