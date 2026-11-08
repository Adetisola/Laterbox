package com.laterbox.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.laterbox.app.data.local.dao.CollectionDao
import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.SavedLinkEntity

@Database(
    entities = [
        SavedLinkEntity::class,
        CollectionEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LaterboxDatabase : RoomDatabase() {

    abstract fun savedLinkDao(): SavedLinkDao
    abstract fun collectionDao(): CollectionDao

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

        @Volatile
        private var INSTANCE: LaterboxDatabase? = null

        fun getInstance(context: Context): LaterboxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LaterboxDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
