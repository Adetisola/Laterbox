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

    @Query("SELECT * FROM media_inspection WHERE savedLinkId = :savedLinkId LIMIT 1")
    fun getInspectionForLink(savedLinkId: Long): Flow<MediaInspectionEntity?>

    @Query("SELECT * FROM media_inspection WHERE savedLinkId = :savedLinkId LIMIT 1")
    suspend fun getEntityForLink(savedLinkId: Long): MediaInspectionEntity?

    @Query("SELECT * FROM media_inspection WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: String): MediaInspectionEntity?

    @Query("DELETE FROM media_inspection WHERE savedLinkId = :savedLinkId")
    suspend fun deleteForLink(savedLinkId: Long)

    @Query("DELETE FROM media_inspection WHERE id = :id")
    suspend fun deleteById(id: String)
}
