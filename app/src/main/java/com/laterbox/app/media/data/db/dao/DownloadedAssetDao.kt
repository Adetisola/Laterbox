package com.laterbox.app.media.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.laterbox.app.media.data.db.entity.DownloadedAssetEntity
import kotlinx.coroutines.flow.Flow

data class MediaStorageTuple(
    val mediaType: String,
    val totalBytes: Long
)

@Dao
interface DownloadedAssetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: DownloadedAssetEntity)

    @Update
    suspend fun updateAsset(asset: DownloadedAssetEntity)

    @Query("SELECT * FROM downloaded_asset WHERE savedLinkId = :savedLinkId ORDER BY createdAt DESC")
    fun getAssetsForLink(savedLinkId: Long): Flow<List<DownloadedAssetEntity>>

    @Query("SELECT * FROM downloaded_asset ORDER BY createdAt DESC")
    fun getAllAssets(): Flow<List<DownloadedAssetEntity>>

    @Query("SELECT * FROM downloaded_asset WHERE id = :id")
    fun getAssetById(id: String): Flow<DownloadedAssetEntity?>

    @Query("SELECT * FROM downloaded_asset WHERE id = :id LIMIT 1")
    suspend fun getEntityById(id: String): DownloadedAssetEntity?

    @Query("SELECT * FROM downloaded_asset WHERE checksum = :checksum AND fileStatus = 'AVAILABLE' LIMIT 1")
    suspend fun findByChecksum(checksum: String): DownloadedAssetEntity?

    @Query("SELECT COUNT(*) FROM downloaded_asset WHERE localPath = :localPath")
    suspend fun countByLocalPath(localPath: String): Int

    @Query("SELECT * FROM downloaded_asset WHERE localPath = :localPath")
    suspend fun getAssetsByLocalPath(localPath: String): List<DownloadedAssetEntity>

    @Query("UPDATE downloaded_asset SET fileStatus = :status WHERE id = :id")
    suspend fun updateFileStatus(id: String, status: String)

    @Query("UPDATE downloaded_asset SET exportedUri = :exportedUri WHERE id = :id")
    suspend fun updateExportedUri(id: String, exportedUri: String?)

    @Query("DELETE FROM downloaded_asset WHERE id = :id")
    suspend fun deleteAssetById(id: String)

    @Query("DELETE FROM downloaded_asset WHERE savedLinkId = :savedLinkId")
    suspend fun deleteAssetsForLink(savedLinkId: Long)

    @Query("SELECT * FROM downloaded_asset WHERE savedLinkId = :savedLinkId")
    suspend fun getAssetsListForLink(savedLinkId: Long): List<DownloadedAssetEntity>

    @Query("SELECT mediaType, SUM(fileSize) as totalBytes FROM downloaded_asset GROUP BY mediaType")
    fun getStorageBreakdown(): Flow<List<MediaStorageTuple>>

    @Query("SELECT COUNT(*) FROM downloaded_asset")
    fun getAssetCount(): Flow<Int>

    @Query("""
        SELECT * FROM downloaded_asset 
        WHERE (:mediaType IS NULL OR mediaType = :mediaType)
        AND (:query = '' OR (
            title LIKE '%' || :query || '%' 
            OR artist LIKE '%' || :query || '%' 
            OR album LIKE '%' || :query || '%' 
            OR originalSourceUrl LIKE '%' || :query || '%'
        ))
        ORDER BY createdAt DESC
    """)
    fun filterAssets(mediaType: String?, query: String): Flow<List<DownloadedAssetEntity>>
}
