package com.laterbox.app.media.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.laterbox.app.media.data.db.entity.MediaInspectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaInspectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MediaInspectionEntity)

    @Update
    suspend fun update(entity: MediaInspectionEntity)

    @Query("SELECT * FROM media_inspection WHERE savedLinkId = :savedLinkId ORDER BY updatedAt DESC LIMIT 1")
    fun getInspectionForLink(savedLinkId: Long): Flow<MediaInspectionEntity?>

    @Query("SELECT * FROM media_inspection WHERE savedLinkId = :savedLinkId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getEntityForLink(savedLinkId: Long): MediaInspectionEntity?

    @Query("SELECT * FROM media_inspection WHERE id = :id ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getEntityById(id: String): MediaInspectionEntity?

    @Query("SELECT * FROM media_inspection WHERE status IN ('PENDING', 'PREPARING', 'INSPECTING', 'PROCESSING') AND updatedAt < :staleThreshold")
    suspend fun getStaleInspections(staleThreshold: Long): List<MediaInspectionEntity>

    @Query("SELECT DISTINCT savedLinkId FROM media_inspection WHERE status = 'COMPLETED'")
    fun getCompletedInspectionLinkIds(): Flow<List<Long>>

    @Query("SELECT * FROM media_inspection WHERE status = 'COMPLETED' GROUP BY savedLinkId ORDER BY updatedAt DESC")
    fun getCompletedInspections(): Flow<List<MediaInspectionEntity>>

    @Query("DELETE FROM media_inspection WHERE savedLinkId = :savedLinkId")
    suspend fun deleteForLink(savedLinkId: Long)

    @Query("DELETE FROM media_inspection WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM media_inspection WHERE status IN ('FAILED', 'NEEDS_AUTH')")
    suspend fun countFailedOrNeedsAuth(): Int

    @Query("SELECT * FROM media_inspection ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentInspectionsSnapshot(limit: Int = 20): List<MediaInspectionEntity>
}
