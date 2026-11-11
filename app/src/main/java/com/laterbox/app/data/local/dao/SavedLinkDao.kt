package com.laterbox.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.laterbox.app.data.local.entity.LinkCollectionCrossRef
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
    @Query("""
        SELECT * FROM saved_links 
        WHERE (id IN (SELECT link_id FROM link_collection_cross_ref WHERE collection_id = :collectionId) OR collection_id = :collectionId)
          AND is_archived = 0 
        ORDER BY created_at DESC
    """)
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

    @Query("SELECT * FROM saved_links WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: Long): SavedLinkEntity?

    @Query("SELECT * FROM saved_links WHERE metadata_status = 'PENDING' OR (metadata_status = 'FETCHING' AND updated_at < :staleThreshold) ORDER BY created_at ASC")
    suspend fun getPendingLinks(staleThreshold: Long = System.currentTimeMillis() - 30_000L): List<SavedLinkEntity>

    @Query("""
        SELECT * FROM saved_links 
        WHERE metadata_status IN ('FAILED', 'PENDING', 'PARTIAL') 
           OR (metadata_status = 'FETCHING' AND updated_at < :staleThreshold)
        ORDER BY created_at DESC
    """)
    suspend fun getLinksNeedingEnrichment(staleThreshold: Long = System.currentTimeMillis() - 30_000L): List<SavedLinkEntity>

    @Query("""
        SELECT * FROM saved_links 
        WHERE is_archived = 0 
          AND id NOT IN (SELECT savedLinkId FROM media_inspection WHERE status = 'COMPLETED')
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    suspend fun getLinksNeedingInspection(limit: Int = 20): List<SavedLinkEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinkCollectionCrossRef(crossRef: LinkCollectionCrossRef)

    @Query("DELETE FROM link_collection_cross_ref WHERE link_id = :linkId AND collection_id = :collectionId")
    suspend fun deleteLinkCollectionCrossRef(linkId: Long, collectionId: Long)

    @Query("DELETE FROM link_collection_cross_ref WHERE link_id = :linkId")
    suspend fun deleteLinkCollectionCrossRefsForLink(linkId: Long)

    @Query("SELECT collection_id FROM link_collection_cross_ref WHERE link_id = :linkId")
    suspend fun getCollectionIdsForLink(linkId: Long): List<Long>

    @Query("UPDATE saved_links SET reminder_at = :reminderAt, nudge_dismissed = 0, updated_at = :updatedAt WHERE id = :linkId")
    suspend fun updateReminder(linkId: Long, reminderAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE saved_links SET nudge_dismissed = 1, updated_at = :updatedAt WHERE id = :linkId")
    suspend fun dismissReminder(linkId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE saved_links SET nudge_count = nudge_count + 1, last_nudged_at = :now, updated_at = :now WHERE id = :linkId")
    suspend fun recordNudge(linkId: Long, now: Long = System.currentTimeMillis())

    @Transaction
    @Query("SELECT * FROM saved_links WHERE reminder_at IS NOT NULL AND reminder_at <= :now AND nudge_dismissed = 0 AND is_archived = 0")
    suspend fun getDueReminders(now: Long = System.currentTimeMillis()): List<SavedLinkWithCollection>

    @Transaction
    @Query("SELECT * FROM saved_links WHERE reminder_at IS NOT NULL AND reminder_at > :now AND nudge_dismissed = 0 AND is_archived = 0")
    suspend fun getScheduledReminders(now: Long = System.currentTimeMillis()): List<SavedLinkWithCollection>

    @Transaction
    @Query("""
        SELECT * FROM saved_links 
        WHERE is_archived = 0 
          AND is_read = 0 
          AND nudge_dismissed = 0 
          AND nudge_count < :maxNudgeCount 
          AND (last_nudged_at IS NULL OR last_nudged_at < :recentNudgeThreshold)
          AND created_at < :minAgeThreshold
        ORDER BY 
          CASE WHEN personal_note IS NOT NULL AND LENGTH(TRIM(personal_note)) > 0 THEN 0 ELSE 1 END,
          created_at ASC
        LIMIT :limit
    """)
    suspend fun getSmartDigestCandidates(
        minAgeThreshold: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000L,
        recentNudgeThreshold: Long = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L,
        maxNudgeCount: Int = 2,
        limit: Int = 10
    ): List<SavedLinkWithCollection>

    @Transaction
    @Query("""
        SELECT * FROM saved_links 
        WHERE is_archived = 0 
          AND is_read = 0 
          AND nudge_dismissed = 0 
          AND nudge_count < :maxNudgeCount 
          AND (last_nudged_at IS NULL OR last_nudged_at < :recentNudgeThreshold)
        ORDER BY 
          CASE WHEN personal_note IS NOT NULL AND LENGTH(TRIM(personal_note)) > 0 THEN 0 ELSE 1 END,
          created_at DESC
        LIMIT :limit
    """)
    suspend fun getRelaxedDigestCandidates(
        recentNudgeThreshold: Long = System.currentTimeMillis() - 6 * 60 * 60 * 1000L,
        maxNudgeCount: Int = 2,
        limit: Int = 5
    ): List<SavedLinkWithCollection>

    @Transaction
    @Query("""
        SELECT * FROM saved_links 
        WHERE is_archived = 0 
          AND is_read = 0
        ORDER BY created_at DESC
        LIMIT 1
    """)
    suspend fun getLatestUnreadLink(): SavedLinkWithCollection?
}
