package com.laterbox.app.data.backup

import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class BackupManager(private val database: LaterboxDatabase) {

    suspend fun exportToJson(outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val collectionDao = database.collectionDao()
            val savedLinkDao = database.savedLinkDao()

            val collections = collectionDao.getCollections().first().map {
                BackupCollection(name = it.name, createdAt = it.createdAt)
            }

            val inboxLinks = savedLinkDao.getInboxLinks().first()
            val archivedLinks = savedLinkDao.getArchivedLinks().first()
            val allLinks = (inboxLinks + archivedLinks).map { item ->
                BackupLink(
                    originalUrl = item.link.originalUrl,
                    normalizedUrl = item.link.normalizedUrl,
                    title = item.link.title,
                    description = item.link.description,
                    siteName = item.link.siteName,
                    domain = item.link.domain,
                    faviconUrl = item.link.faviconUrl,
                    previewImageUrl = item.link.previewImageUrl,
                    contentType = item.link.contentType,
                    personalNote = item.link.personalNote,
                    collectionName = item.collection?.name,
                    isRead = item.link.isRead,
                    isArchived = item.link.isArchived,
                    createdAt = item.link.createdAt
                )
            }

            val backup = LaterboxBackup(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                collections = collections,
                links = allLinks
            )

            val jsonString = serializeBackup(backup)
            outputStream.bufferedWriter().use { it.write(jsonString) }

            Result.success(allLinks.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromJson(inputStream: InputStream): Result<BackupRestoreResult> = withContext(Dispatchers.IO) {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val backup = deserializeBackup(jsonString)

            val collectionDao = database.collectionDao()
            val savedLinkDao = database.savedLinkDao()

            var restoredCollectionsCount = 0
            var restoredLinksCount = 0
            var skippedDuplicatesCount = 0

            // 1. Resolve / Create Collections
            val existingCollections = collectionDao.getCollections().first()
            val collectionMap = existingCollections.associate { it.name.lowercase() to it.id }.toMutableMap()

            for (bCol in backup.collections) {
                val key = bCol.name.trim().lowercase()
                if (!collectionMap.containsKey(key)) {
                    val id = collectionDao.insertCollection(CollectionEntity(name = bCol.name.trim()))
                    collectionMap[key] = id
                    restoredCollectionsCount++
                }
            }

            // 2. Restore Links with duplicate detection
            for (bLink in backup.links) {
                val existing = savedLinkDao.findLinkByNormalizedUrl(bLink.normalizedUrl)
                if (existing != null) {
                    // Duplicate found: update notes if incoming backup has personal note and current does not
                    if (existing.link.personalNote.isNullOrBlank() && !bLink.personalNote.isNullOrBlank()) {
                        savedLinkDao.updateLink(existing.link.copy(personalNote = bLink.personalNote))
                    }
                    skippedDuplicatesCount++
                } else {
                    val targetColId = bLink.collectionName?.trim()?.lowercase()?.let { collectionMap[it] }
                    val entity = SavedLinkEntity(
                        originalUrl = bLink.originalUrl,
                        normalizedUrl = bLink.normalizedUrl,
                        title = bLink.title,
                        description = bLink.description,
                        siteName = bLink.siteName,
                        domain = bLink.domain,
                        faviconUrl = bLink.faviconUrl,
                        previewImageUrl = bLink.previewImageUrl,
                        contentType = bLink.contentType,
                        personalNote = bLink.personalNote,
                        collectionId = targetColId,
                        isRead = bLink.isRead,
                        isArchived = bLink.isArchived,
                        metadataStatus = if (bLink.previewImageUrl != null) MetadataStatus.SUCCESS else MetadataStatus.PARTIAL,
                        createdAt = bLink.createdAt,
                        updatedAt = System.currentTimeMillis()
                    )
                    savedLinkDao.insertLink(entity)
                    restoredLinksCount++
                }
            }

            Result.success(
                BackupRestoreResult(
                    restoredLinksCount = restoredLinksCount,
                    skippedDuplicatesCount = skippedDuplicatesCount,
                    restoredCollectionsCount = restoredCollectionsCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun serializeBackup(backup: LaterboxBackup): String {
        val root = JSONObject()
        root.put("version", backup.version)
        root.put("appName", backup.appName)
        root.put("exportedAt", backup.exportedAt)

        val colArray = JSONArray()
        for (col in backup.collections) {
            val cObj = JSONObject()
            cObj.put("name", col.name)
            cObj.put("createdAt", col.createdAt)
            colArray.put(cObj)
        }
        root.put("collections", colArray)

        val linkArray = JSONArray()
        for (link in backup.links) {
            val lObj = JSONObject()
            lObj.put("originalUrl", link.originalUrl)
            lObj.put("normalizedUrl", link.normalizedUrl)
            lObj.put("title", link.title)
            lObj.put("description", link.description ?: JSONObject.NULL)
            lObj.put("siteName", link.siteName ?: JSONObject.NULL)
            lObj.put("domain", link.domain)
            lObj.put("faviconUrl", link.faviconUrl ?: JSONObject.NULL)
            lObj.put("previewImageUrl", link.previewImageUrl ?: JSONObject.NULL)
            lObj.put("contentType", link.contentType.name)
            lObj.put("personalNote", link.personalNote ?: JSONObject.NULL)
            lObj.put("collectionName", link.collectionName ?: JSONObject.NULL)
            lObj.put("isRead", link.isRead)
            lObj.put("isArchived", link.isArchived)
            lObj.put("createdAt", link.createdAt)
            linkArray.put(lObj)
        }
        root.put("links", linkArray)

        return root.toString(2)
    }

    fun deserializeBackup(jsonString: String): LaterboxBackup {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", 1)
        val appName = root.optString("appName", "Laterbox")
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        val collections = mutableListOf<BackupCollection>()
        val colArray = root.optJSONArray("collections")
        if (colArray != null) {
            for (i in 0 until colArray.length()) {
                val cObj = colArray.getJSONObject(i)
                collections.add(
                    BackupCollection(
                        name = cObj.getString("name"),
                        createdAt = cObj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val links = mutableListOf<BackupLink>()
        val linkArray = root.optJSONArray("links")
        if (linkArray != null) {
            for (i in 0 until linkArray.length()) {
                val lObj = linkArray.getJSONObject(i)
                val contentType = try {
                    ContentType.valueOf(lObj.optString("contentType", "WEBSITE"))
                } catch (e: Exception) {
                    ContentType.WEBSITE
                }

                links.add(
                    BackupLink(
                        originalUrl = lObj.getString("originalUrl"),
                        normalizedUrl = lObj.optString("normalizedUrl", lObj.getString("originalUrl")),
                        title = lObj.getString("title"),
                        description = lObj.optNullableString("description"),
                        siteName = lObj.optNullableString("siteName"),
                        domain = lObj.getString("domain"),
                        faviconUrl = lObj.optNullableString("faviconUrl"),
                        previewImageUrl = lObj.optNullableString("previewImageUrl"),
                        contentType = contentType,
                        personalNote = lObj.optNullableString("personalNote"),
                        collectionName = lObj.optNullableString("collectionName"),
                        isRead = lObj.optBoolean("isRead", false),
                        isArchived = lObj.optBoolean("isArchived", false),
                        createdAt = lObj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return LaterboxBackup(
            version = version,
            exportedAt = exportedAt,
            appName = appName,
            collections = collections,
            links = links
        )
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        val str = optString(key)
        return str.ifEmpty { null }
    }
}
