package com.laterbox.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.laterbox.app.data.local.dao.CollectionDao
import com.laterbox.app.data.local.dao.NotificationHistoryDao
import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.NotificationHistoryEntity
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.media.data.db.dao.DownloadedAssetDao
import com.laterbox.app.media.data.db.dao.DownloadTaskDao
import com.laterbox.app.media.data.db.dao.MediaInspectionDao
import com.laterbox.app.media.data.db.entity.DownloadedAssetEntity
import com.laterbox.app.media.data.db.entity.DownloadTaskEntity
import com.laterbox.app.media.data.db.entity.MediaInspectionEntity

@Database(
    entities = [
        SavedLinkEntity::class,
        CollectionEntity::class,
        com.laterbox.app.data.local.entity.LinkCollectionCrossRef::class,
        NotificationHistoryEntity::class,
        DownloadedAssetEntity::class,
        DownloadTaskEntity::class,
        MediaInspectionEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LaterboxDatabase : RoomDatabase() {

    abstract fun savedLinkDao(): SavedLinkDao
    abstract fun collectionDao(): CollectionDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
    abstract fun downloadedAssetDao(): DownloadedAssetDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun mediaInspectionDao(): MediaInspectionDao

    companion object {
        private const val DATABASE_NAME = "laterbox.db"

        /**
         * Non-destructive migration from version 1 to version 2:
         * 1. Safely merges any pre-existing duplicate rows sharing the same normalized_url
         *    (combining personal notes, preserving collection assignments, descriptions, and images).
         * 2. Replaces the non-unique index on normalized_url with a UNIQUE index.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val dupCursor = db.query(
                    "SELECT normalized_url FROM saved_links GROUP BY normalized_url HAVING COUNT(*) > 1"
                )
                val duplicateUrls = mutableListOf<String>()
                dupCursor.use { c ->
                    while (c.moveToNext()) {
                        duplicateUrls.add(c.getString(0))
                    }
                }

                for (normUrl in duplicateUrls) {
                    val rowCursor = db.query(
                        """
                        SELECT id, title, domain, description, site_name, favicon_url, 
                               preview_image_url, personal_note, collection_id, is_read, is_archived,
                               metadata_status, content_type
                        FROM saved_links
                        WHERE normalized_url = ?
                        ORDER BY id ASC
                        """.trimIndent(),
                        arrayOf(normUrl)
                    )

                    var keeperId: Long? = null
                    var bestTitle: String? = null
                    var domain: String = ""
                    var bestDescription: String? = null
                    var bestSiteName: String? = null
                    var bestFavicon: String? = null
                    var bestImage: String? = null
                    val notes = mutableListOf<String>()
                    var bestCollectionId: Long? = null
                    var anyRead = false
                    var anyUnarchived = false
                    var bestStatus = "FAILED"
                    var bestContentType = "WEBSITE"
                    val duplicateIdsToDelete = mutableListOf<Long>()

                    fun statusRank(status: String?): Int = when (status) {
                        "SUCCESS" -> 4
                        "PARTIAL" -> 3
                        "PENDING" -> 2
                        "FETCHING" -> 1
                        else -> 0
                    }

                    rowCursor.use { rc ->
                        while (rc.moveToNext()) {
                            val id = rc.getLong(0)
                            val title = rc.getString(1)
                            val rowDomain = rc.getString(2) ?: ""
                            val desc = rc.getString(3)
                            val siteName = rc.getString(4)
                            val favicon = rc.getString(5)
                            val image = rc.getString(6)
                            val note = rc.getString(7)
                            val colId = if (rc.isNull(8)) null else rc.getLong(8)
                            val isRead = rc.getInt(9) != 0
                            val isArchived = rc.getInt(10) != 0
                            val status = rc.getString(11) ?: "PENDING"
                            val contentType = rc.getString(12) ?: "WEBSITE"

                            if (keeperId == null) {
                                keeperId = id
                                domain = rowDomain
                                bestStatus = status
                                bestContentType = contentType
                            } else {
                                duplicateIdsToDelete.add(id)
                            }

                            if (!title.isNullOrBlank() && (bestTitle == null || bestTitle == domain)) {
                                bestTitle = title
                            }
                            if (bestDescription.isNullOrBlank() && !desc.isNullOrBlank()) {
                                bestDescription = desc
                            }
                            if (bestSiteName.isNullOrBlank() && !siteName.isNullOrBlank()) {
                                bestSiteName = siteName
                            }
                            if (bestFavicon.isNullOrBlank() && !favicon.isNullOrBlank()) {
                                bestFavicon = favicon
                            }
                            if (bestImage.isNullOrBlank() && !image.isNullOrBlank()) {
                                bestImage = image
                            }
                            if (!note.isNullOrBlank() && note.trim() !in notes) {
                                notes.add(note.trim())
                            }
                            if (bestCollectionId == null && colId != null) {
                                bestCollectionId = colId
                            }
                            if (isRead) anyRead = true
                            if (!isArchived) anyUnarchived = true
                            if (statusRank(status) > statusRank(bestStatus)) {
                                bestStatus = status
                            }
                            if (bestContentType == "WEBSITE" && contentType != "WEBSITE") {
                                bestContentType = contentType
                            }
                        }
                    }

                    val finalKeeperId = keeperId
                    if (finalKeeperId != null && duplicateIdsToDelete.isNotEmpty()) {
                        val mergedNote = if (notes.isEmpty()) null else notes.joinToString("\n\n")
                        db.execSQL(
                            """
                            UPDATE saved_links
                            SET title = ?,
                                description = ?,
                                site_name = ?,
                                favicon_url = ?,
                                preview_image_url = ?,
                                personal_note = ?,
                                collection_id = ?,
                                is_read = ?,
                                is_archived = ?,
                                metadata_status = ?,
                                content_type = ?
                            WHERE id = ?
                            """.trimIndent(),
                            arrayOf<Any?>(
                                bestTitle ?: domain,
                                bestDescription,
                                bestSiteName,
                                bestFavicon,
                                bestImage,
                                mergedNote,
                                bestCollectionId,
                                if (anyRead) 1 else 0,
                                if (anyUnarchived) 0 else 1,
                                bestStatus,
                                bestContentType,
                                finalKeeperId
                            )
                        )

                        for (delId in duplicateIdsToDelete) {
                            db.execSQL("DELETE FROM saved_links WHERE id = ?", arrayOf<Any>(delId))
                        }
                    }
                }

                db.execSQL("DROP INDEX IF EXISTS `index_saved_links_normalized_url`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_links_normalized_url` ON `saved_links` (`normalized_url`)")
            }
        }

        /**
         * Consolidated atomic migration from version 2 to version 3:
         * 1. Creates link_collection_cross_ref table with foreign keys & indices.
         * 2. Migrates existing saved_links.collection_id entries into link_collection_cross_ref.
         * 3. Adds reminder_at, last_nudged_at, nudge_count, and nudge_dismissed columns with index.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create link_collection_cross_ref
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `link_collection_cross_ref` (
                        `link_id` INTEGER NOT NULL,
                        `collection_id` INTEGER NOT NULL,
                        PRIMARY KEY(`link_id`, `collection_id`),
                        FOREIGN KEY(`link_id`) REFERENCES `saved_links`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`collection_id`) REFERENCES `collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_link_collection_cross_ref_collection_id` ON `link_collection_cross_ref` (`collection_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_link_collection_cross_ref_link_id` ON `link_collection_cross_ref` (`link_id`)")

                // 2. Migrate existing saved_links.collection_id
                db.execSQL("""
                    INSERT OR IGNORE INTO `link_collection_cross_ref` (`link_id`, `collection_id`)
                    SELECT `id`, `collection_id` FROM `saved_links` WHERE `collection_id` IS NOT NULL
                """.trimIndent())

                // 3. Add reminder & nudge columns
                db.execSQL("ALTER TABLE `saved_links` ADD COLUMN `reminder_at` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_links` ADD COLUMN `last_nudged_at` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_links` ADD COLUMN `nudge_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_links` ADD COLUMN `nudge_dismissed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_links_reminder_at` ON `saved_links` (`reminder_at`)")
            }
        }

        /**
         * Non-destructive migration from version 3 to version 4:
         * Creates persistent notification_history table and its indices.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notification_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `link_id` INTEGER,
                        `url` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `origin` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `site_name` TEXT,
                        `preview_image_url` TEXT,
                        `favicon_url` TEXT,
                        `is_read` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_created_at` ON `notification_history` (`created_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_is_read` ON `notification_history` (`is_read`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notification_history_link_id` ON `notification_history` (`link_id`)")
            }
        }

        /**
         * Migration from version 4 to version 5:
         * 1. Creates downloaded_asset table with foreign key to saved_links(id) (ON DELETE SET NULL),
         *    and indices on savedLinkId, checksum, and localPath.
         * 2. Creates download_task table with foreign key to saved_links(id) (ON DELETE CASCADE),
         *    and indices on savedLinkId and status.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `downloaded_asset` (
                        `id` TEXT NOT NULL,
                        `savedLinkId` INTEGER,
                        `sourcePlatform` TEXT NOT NULL,
                        `originalSourceUrl` TEXT NOT NULL,
                        `title` TEXT,
                        `mimeType` TEXT NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `exportedUri` TEXT,
                        `fileStatus` TEXT NOT NULL DEFAULT 'AVAILABLE',
                        `fileSize` INTEGER NOT NULL,
                        `durationMs` INTEGER,
                        `width` INTEGER,
                        `height` INTEGER,
                        `qualityLabel` TEXT,
                        `checksum` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`savedLinkId`) REFERENCES `saved_links`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_asset_savedLinkId` ON `downloaded_asset` (`savedLinkId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_asset_checksum` ON `downloaded_asset` (`checksum`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloaded_asset_localPath` ON `downloaded_asset` (`localPath`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `download_task` (
                        `id` TEXT NOT NULL,
                        `savedLinkId` INTEGER NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `sourcePlatform` TEXT NOT NULL,
                        `candidateId` TEXT,
                        `formatSelector` TEXT,
                        `mediaType` TEXT NOT NULL,
                        `qualityLabel` TEXT,
                        `title` TEXT,
                        `assetId` TEXT,
                        `status` TEXT NOT NULL,
                        `progress` INTEGER NOT NULL DEFAULT 0,
                        `downloadedBytes` INTEGER NOT NULL DEFAULT 0,
                        `totalBytes` INTEGER,
                        `allowCellularOverride` INTEGER NOT NULL DEFAULT 0,
                        `supportsRange` INTEGER NOT NULL DEFAULT 1,
                        `retryCount` INTEGER NOT NULL DEFAULT 0,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`savedLinkId`) REFERENCES `saved_links`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_task_savedLinkId` ON `download_task` (`savedLinkId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_download_task_status` ON `download_task` (`status`)")
            }
        }

        /**
         * Migration from version 5 to version 6:
         * Adds music metadata columns (artist, album, artworkUrl, spotifyTrackId, versionTag)
         * to downloaded_asset and download_task tables.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloaded_asset` ADD COLUMN `artist` TEXT")
                db.execSQL("ALTER TABLE `downloaded_asset` ADD COLUMN `album` TEXT")
                db.execSQL("ALTER TABLE `downloaded_asset` ADD COLUMN `artworkUrl` TEXT")
                db.execSQL("ALTER TABLE `downloaded_asset` ADD COLUMN `spotifyTrackId` TEXT")
                db.execSQL("ALTER TABLE `downloaded_asset` ADD COLUMN `versionTag` TEXT")

                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `artist` TEXT")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `album` TEXT")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `artworkUrl` TEXT")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `spotifyTrackId` TEXT")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `versionTag` TEXT")
            }
        }

        /**
         * Migration from version 6 to version 7:
         * 1. Adds stage and stageDetail columns to download_task table for transparent progress stages.
         * 2. Adds thumbnailPath column to downloaded_asset table for local thumbnail rendering.
         * 3. Creates media_inspection table with indexes and foreign keys for persistent inspection.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `stage` TEXT")
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `stageDetail` TEXT")
                db.execSQL("ALTER TABLE `downloaded_asset` ADD COLUMN `thumbnailPath` TEXT")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_inspection` (
                        `id` TEXT NOT NULL,
                        `savedLinkId` INTEGER NOT NULL,
                        `url` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `resultJson` TEXT,
                        `errorCode` TEXT,
                        `errorMessage` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`savedLinkId`) REFERENCES `saved_links`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_inspection_savedLinkId` ON `media_inspection` (`savedLinkId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_inspection_status` ON `media_inspection` (`status`)")
            }
        }

        /**
         * Migration from version 7 to version 8:
         * Adds headersJson column to download_task table to preserve request headers (e.g., Referer, User-Agent)
         * across worker restarts and app lifecycles.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `download_task` ADD COLUMN `headersJson` TEXT")
            }
        }

        @Volatile
        private var INSTANCE: LaterboxDatabase? = null

        fun getInstance(context: Context): LaterboxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LaterboxDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
