package com.laterbox.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.data.local.entity.SavedLinkWithCollection
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLinkDao {

    @Transaction
    @Query("SELECT * FROM saved_links WHERE is_archived = 0 ORDER BY created_at DESC")
    fun getInboxLinks(): Flow<List<SavedLinkWithCollection>>

    @Transaction
    @Query("SELECT * FROM saved_links WHERE is_archived = 1 ORDER BY updated_at DESC")
    fun getArchivedLinks(): Flow<List<SavedLinkWithCollection>>

    @Transaction
    @Query("SELECT * FROM saved_links WHERE collection_id = :collectionId AND is_archived = 0 ORDER BY created_at DESC")
    fun getLinksByCollection(collectionId: Long): Flow<List<SavedLinkWithCollection>>

    @Transaction
    @Query("""
        SELECT * FROM saved_links 
        WHERE is_archived = 0 AND (
            title LIKE '%' || :query || '%' OR 
            domain LIKE '%' || :query || '%' OR 
            original_url LIKE '%' || :query || '%' OR 
            personal_note LIKE '%' || :query || '%'
        ) 
        ORDER BY created_at DESC
    """)
    fun searchLinks(query: String): Flow<List<SavedLinkWithCollection>>

    @Transaction
    @Query("SELECT * FROM saved_links WHERE normalized_url = :normalizedUrl LIMIT 1")
    suspend fun findLinkByNormalizedUrl(normalizedUrl: String): SavedLinkWithCollection?

    @Transaction
    @Query("SELECT * FROM saved_links WHERE id = :id")
    fun getLinkById(id: Long): Flow<SavedLinkWithCollection?>

    @Query("SELECT * FROM saved_links WHERE metadata_status IN ('PENDING', 'FETCHING') ORDER BY created_at ASC")
    suspend fun getPendingLinks(): List<SavedLinkEntity>

    @Query("SELECT * FROM saved_links WHERE metadata_status IN ('FAILED', 'PENDING', 'FETCHING') OR (preview_image_url IS NULL AND metadata_status != 'SUCCESS') ORDER BY created_at DESC")
    suspend fun getLinksNeedingEnrichment(): List<SavedLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: SavedLinkEntity): Long

    @Update
    suspend fun updateLink(link: SavedLinkEntity)

    @Query("DELETE FROM saved_links WHERE id = :id")
    suspend fun deleteLink(id: Long)

    @Query("UPDATE saved_links SET is_archived = :isArchived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE saved_links SET is_read = :isRead, updated_at = :updatedAt WHERE id = :id")
    suspend fun setRead(id: Long, isRead: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE saved_links SET metadata_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateMetadataStatus(id: Long, status: MetadataStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE saved_links 
        SET title = :title,
            description = :description,
            site_name = :siteName,
            favicon_url = :faviconUrl,
            preview_image_url = :previewImageUrl,
            content_type = :contentType,
            metadata_status = :metadataStatus,
            last_fetched_at = :lastFetchedAt,
            updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateMetadata(
        id: Long,
        title: String,
        description: String?,
        siteName: String?,
        faviconUrl: String?,
        previewImageUrl: String?,
        contentType: ContentType,
        metadataStatus: MetadataStatus,
        lastFetchedAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE saved_links SET metadata_status = 'PENDING' WHERE metadata_status = 'FETCHING'")
    suspend fun resetStuckFetchingLinks()
}
