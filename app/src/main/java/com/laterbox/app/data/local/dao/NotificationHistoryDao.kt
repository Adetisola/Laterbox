package com.laterbox.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laterbox.app.data.local.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationHistoryEntity): Long

    @Query("SELECT * FROM notification_history ORDER BY created_at DESC")
    fun getAllHistory(): Flow<List<NotificationHistoryEntity>>

    @Query("SELECT COUNT(*) FROM notification_history WHERE is_read = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT * FROM notification_history WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NotificationHistoryEntity?

    @Query("UPDATE notification_history SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notification_history SET is_read = 0 WHERE id = :id")
    suspend fun markAsUnread(id: Long)

    @Query("UPDATE notification_history SET is_read = 1 WHERE is_read = 0")
    suspend fun markAllAsRead()

    @Query("UPDATE notification_history SET is_read = 1 WHERE link_id = :linkId")
    suspend fun markReadByLinkId(linkId: Long)

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notification_history")
    suspend fun clearAll()
}
