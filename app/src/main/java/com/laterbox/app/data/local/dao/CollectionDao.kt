package com.laterbox.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.CollectionWithCountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getCollections(): Flow<List<CollectionEntity>>

    @Query("""
        SELECT c.id, c.name, c.created_at, c.updated_at,
               COUNT(l.id) AS item_count
        FROM collections c
        LEFT JOIN saved_links l ON c.id = l.collection_id AND l.is_archived = 0
        GROUP BY c.id
        ORDER BY c.name ASC
    """)
    fun getCollectionsWithCounts(): Flow<List<CollectionWithCountEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun getCollectionById(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: Long)
}
