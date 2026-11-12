package com.laterbox.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laterbox.app.data.local.entity.ActivityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ActivityEventEntity): Long

    @Query("SELECT * FROM activity_events ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 30): List<ActivityEventEntity>

    @Query("SELECT * FROM activity_events ORDER BY created_at DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 30): Flow<List<ActivityEventEntity>>

    @Query(
        """
        DELETE FROM activity_events 
        WHERE id NOT IN (SELECT id FROM activity_events ORDER BY created_at DESC LIMIT :keepCount)
        """
    )
    suspend fun pruneToLatest(keepCount: Int = 200)

    @Query("DELETE FROM activity_events")
    suspend fun clearAll()
}
