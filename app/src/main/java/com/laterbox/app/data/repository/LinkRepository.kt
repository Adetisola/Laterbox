package com.laterbox.app.data.repository

import android.content.Context
import com.laterbox.app.data.local.dao.SavedLinkDao
import com.laterbox.app.data.local.entity.SavedLinkEntity
import com.laterbox.app.data.local.entity.SavedLinkWithCollection
import com.laterbox.app.data.metadata.JsoupMetadataExtractor
import com.laterbox.app.data.metadata.MetadataFetchException
import com.laterbox.app.data.metadata.MetadataSyncWorker
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.MetadataStatus
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.domain.util.UrlNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jsoup.HttpStatusException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

class LinkRepository(
    private val savedLinkDao: SavedLinkDao,
    private val appContext: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val saveMutex = Mutex()
    private val extractionSemaphore = Semaphore(permits = 4)
    private val inFlightIds = ConcurrentHashMap.newKeySet<Long>()

    val inboxLinks: Flow<List<SavedLink>> = savedLinkDao.getInboxLinks().map { list ->
        list.map { it.toDomain() }
    }

    val archivedLinks: Flow<List<SavedLink>> = savedLinkDao.getArchivedLinks().map { list ->
        list.map { it.toDomain() }
    }

    fun getLinksByCollection(collectionId: Long): Flow<List<SavedLink>> {
        return savedLinkDao.getLinksByCollection(collectionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun searchLinks(query: String): Flow<List<SavedLink>> {
        return savedLinkDao.searchLinks(query.trim()).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun findDuplicate(rawUrl: String): SavedLink? {
        val normalized = UrlNormalizer.normalize(rawUrl)
        if (normalized.isEmpty()) return null
        return savedLinkDao.findLinkByNormalizedUrl(normalized)?.toDomain()
    }

    fun getLinkById(id: Long): Flow<SavedLink?> {
        return savedLinkDao.getLinkById(id).map { it?.toDomain() }
    }

    suspend fun saveLink(
        originalUrl: String,
        title: String? = null,
        personalNote: String? = null,
        collectionId: Long? = null,
        contentType: ContentType = ContentType.WEBSITE,
        autoFetchMetadata: Boolean = true
    ): Long {
        val trimmedUrl = originalUrl.trim()
        val normalized = UrlNormalizer.normalize(trimmedUrl)
        val domain = UrlNormalizer.extractDomain(trimmedUrl)
        val resolvedTitle = if (!title.isNullOrBlank()) title.trim() else domain
        val cleanedNote = personalNote?.trim()?.ifEmpty { null }

        val linkId: Long = saveMutex.withLock {
            val entity = SavedLinkEntity(
                originalUrl = trimmedUrl,
                normalizedUrl = normalized,
                title = resolvedTitle,
                domain = domain,
                contentType = contentType,
                personalNote = cleanedNote,
                collectionId = collectionId,
                isRead = false,
                isArchived = false,
                metadataStatus = MetadataStatus.PENDING,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            try {
                savedLinkDao.insertLink(entity)
            } catch (_: Exception) {
                val existing = if (normalized.isNotEmpty()) {
                    savedLinkDao.findLinkByNormalizedUrl(normalized)?.link
                } else {
                    null
                }
                if (existing != null) {
                    val mergedTitle = if (!title.isNullOrBlank() && title.trim() != domain) {
                        title.trim()
                    } else {
                        existing.title
                    }
                    val mergedNote = when {
                        cleanedNote == null -> existing.personalNote
                        existing.personalNote.isNullOrBlank() -> cleanedNote
                        existing.personalNote.contains(cleanedNote) -> existing.personalNote
                        else -> "${existing.personalNote}\n\n$cleanedNote"
                    }
                    val mergedCollectionId = collectionId ?: existing.collectionId
                    val updated = existing.copy(
                        title = mergedTitle,
                        personalNote = mergedNote,
                        collectionId = mergedCollectionId,
                        isArchived = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    savedLinkDao.updateLink(updated)
                    existing.id
                } else {
                    -1L
                }
            }
        }

        if (linkId > 0 && autoFetchMetadata) {
            appContext?.let { MetadataSyncWorker.enqueue(it) }
            scope.launch {
                fetchMetadataNow(linkId, isManualRetry = false)
            }
        }

        return linkId
    }

    /**
     * Unified metadata extraction and persistence pipeline for a single link.
     * Deduplicates in-flight requests, bounds concurrency with a Semaphore, and merges fields
     * non-destructively so user titles, notes, collections, and previously extracted fields are preserved.
     *
     * @return true if extraction completed or was skipped safely; false if a transient/retriable error occurred.
     */
    suspend fun fetchMetadataNow(linkId: Long, isManualRetry: Boolean = false): Boolean {
        if (!inFlightIds.add(linkId)) {
            // Already being fetched by another coroutine/worker
            return true
        }

        try {
            return extractionSemaphore.withPermit {
                val initialLink = savedLinkDao.getEntityById(linkId) ?: return@withPermit true

                // Avoid re-fetching already complete records unless explicitly retried
                if (!isManualRetry && initialLink.metadataStatus == MetadataStatus.SUCCESS &&
                    !initialLink.previewImageUrl.isNullOrBlank() &&
                    JsoupMetadataExtractor.isMeaningfulTitle(initialLink.title, initialLink.domain)
                ) {
                    return@withPermit true
                }

                savedLinkDao.updateMetadataStatus(linkId, MetadataStatus.FETCHING)

                val result = JsoupMetadataExtractor.extract(initialLink.originalUrl)

                // Re-read latest entity from DB so concurrent user edits (notes, title, collection, read/archive) are never lost
                val currentLink = savedLinkDao.getEntityById(linkId) ?: return@withPermit true

                if (result.isSuccess) {
                    val meta = result.getOrThrow()

                    val userHasCustomTitle = currentLink.title.isNotBlank() &&
                        currentLink.title != currentLink.domain &&
                        !currentLink.title.equals("www.${currentLink.domain}", ignoreCase = true) &&
                        !currentLink.title.startsWith("http://", ignoreCase = true) &&
                        !currentLink.title.startsWith("https://", ignoreCase = true)

                    val resolvedTitle: String = when {
                        userHasCustomTitle -> currentLink.title
                        JsoupMetadataExtractor.isMeaningfulTitle(meta.title, currentLink.domain) -> meta.title ?: currentLink.domain
                        currentLink.title.isNotBlank() -> currentLink.title
                        else -> currentLink.domain
                    }

                    val resolvedDescription = if (!meta.description.isNullOrBlank()) {
                        meta.description
                    } else {
                        currentLink.description
                    }

                    val resolvedSiteName = if (!meta.siteName.isNullOrBlank() && meta.siteName != currentLink.domain) {
                        meta.siteName
                    } else {
                        currentLink.siteName ?: meta.siteName
                    }

                    val defaultFavicon = "https://${currentLink.domain}/favicon.ico"
                    val resolvedFavicon = when {
                        !meta.faviconUrl.isNullOrBlank() && meta.faviconUrl != defaultFavicon -> meta.faviconUrl
                        !currentLink.faviconUrl.isNullOrBlank() -> currentLink.faviconUrl
                        else -> meta.faviconUrl
                    }

                    val resolvedPreviewImage = if (!meta.previewImageUrl.isNullOrBlank()) {
                        meta.previewImageUrl
                    } else {
                        currentLink.previewImageUrl
                    }

                    val resolvedContentType = if (meta.contentType != ContentType.WEBSITE) {
                        meta.contentType
                    } else {
                        currentLink.contentType
                    }

                    val hasUsefulTitle = JsoupMetadataExtractor.isMeaningfulTitle(resolvedTitle, currentLink.domain)
                    val hasRichContent = !resolvedPreviewImage.isNullOrBlank() || !resolvedDescription.isNullOrBlank()

                    val status = when {
                        hasUsefulTitle && hasRichContent -> MetadataStatus.SUCCESS
                        hasUsefulTitle || hasRichContent -> MetadataStatus.PARTIAL
                        else -> MetadataStatus.FAILED
                    }

                    savedLinkDao.updateLink(
                        currentLink.copy(
                            title = resolvedTitle,
                            description = resolvedDescription,
                            siteName = resolvedSiteName,
                            faviconUrl = resolvedFavicon,
                            previewImageUrl = resolvedPreviewImage,
                            contentType = resolvedContentType,
                            metadataStatus = status,
                            lastFetchedAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    true
                } else {
                    val error = result.exceptionOrNull()
                    val statusCode = (error as? MetadataFetchException)?.httpStatusCode
                        ?: (error?.cause as? HttpStatusException)?.statusCode
                        ?: (error as? HttpStatusException)?.statusCode

                    val isTransientNetworkError = isTransientNetworkError(error)
                    val isPermanentHttpRestriction = statusCode in listOf(401, 403, 404, 410, 451)

                    // Preserve any existing partial metadata if present
                    val hasExistingUsefulMetadata = JsoupMetadataExtractor.isMeaningfulTitle(currentLink.title, currentLink.domain) ||
                        !currentLink.previewImageUrl.isNullOrBlank() ||
                        !currentLink.description.isNullOrBlank()

                    when {
                        isTransientNetworkError -> {
                            // Keep PENDING on offline/DNS/timeout without burning permanent failure retries
                            savedLinkDao.updateLink(
                                currentLink.copy(
                                    metadataStatus = if (hasExistingUsefulMetadata) MetadataStatus.PARTIAL else MetadataStatus.PENDING,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            false
                        }
                        isPermanentHttpRestriction -> {
                            // Avoid endless retries on 404 or 403 authentication walls
                            savedLinkDao.updateLink(
                                currentLink.copy(
                                    retryCount = maxOf(currentLink.retryCount + 1, 3),
                                    metadataStatus = if (hasExistingUsefulMetadata) MetadataStatus.PARTIAL else MetadataStatus.FAILED,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            true
                        }
                        else -> {
                            // HTTP 429, 5xx, or parse error: bounded retry
                            val baseRetry = if (isManualRetry && currentLink.retryCount >= 3) 2 else currentLink.retryCount
                            val newRetry = baseRetry + 1
                            val nextStatus = when {
                                newRetry >= 3 && hasExistingUsefulMetadata -> MetadataStatus.PARTIAL
                                newRetry >= 3 -> MetadataStatus.FAILED
                                else -> MetadataStatus.PENDING
                            }
                            savedLinkDao.updateLink(
                                currentLink.copy(
                                    retryCount = newRetry,
                                    metadataStatus = nextStatus,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            newRetry >= 3
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            inFlightIds.remove(linkId)
        }
    }

    /**
     * Drains all pending links in bounded-concurrency batches, looping if new links were saved
     * while the current batch was executing.
     *
     * @return true if all pending links resolved cleanly; false if any link encountered a transient failure needing WorkManager backoff.
     */
    suspend fun syncPendingLinks(): Boolean {
        var allClean = true
        val processedInRun = mutableSetOf<Long>()

        while (true) {
            val pending = savedLinkDao.getPendingLinks()
                .filter { it.id !in processedInRun }

            if (pending.isEmpty()) break

            processedInRun.addAll(pending.map { it.id })

            val results = coroutineScope {
                pending.map { link ->
                    async {
                        fetchMetadataNow(link.id, isManualRetry = false)
                    }
                }.awaitAll()
            }

            if (results.any { !it }) {
                allClean = false
            }
        }

        return allClean
    }

    /**
     * Triggered by manual Pull-to-Refresh: retries eligible PENDING, PARTIAL, and FAILED links concurrently.
     */
    suspend fun retryPendingAndFailedFetches() {
        val linksToRetry = savedLinkDao.getLinksNeedingEnrichment()
        if (linksToRetry.isEmpty()) return

        appContext?.let { MetadataSyncWorker.enqueue(it) }

        coroutineScope {
            linksToRetry.map { link ->
                async {
                    fetchMetadataNow(link.id, isManualRetry = true)
                }
            }.awaitAll()
        }
    }

    suspend fun retrySingleLink(linkId: Long) {
        val current = savedLinkDao.getEntityById(linkId) ?: return
        savedLinkDao.updateLink(
            current.copy(
                metadataStatus = MetadataStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
        )
        appContext?.let { MetadataSyncWorker.enqueue(it) }
        fetchMetadataNow(linkId, isManualRetry = true)
    }

    private fun isTransientNetworkError(error: Throwable?): Boolean {
        var curr: Throwable? = error
        while (curr != null) {
            if (curr is UnknownHostException ||
                curr is SocketTimeoutException ||
                curr is ConnectException
            ) {
                return true
            }
            if (curr is IOException && curr !is HttpStatusException) {
                return true
            }
            curr = curr.cause
        }
        return false
    }

    suspend fun updateLink(link: SavedLink) {
        val existing = savedLinkDao.getEntityById(link.id)
        val entity = SavedLinkEntity(
            id = link.id,
            originalUrl = link.originalUrl,
            normalizedUrl = link.normalizedUrl,
            title = link.title,
            description = link.description ?: existing?.description,
            siteName = link.siteName ?: existing?.siteName,
            domain = link.domain,
            faviconUrl = link.faviconUrl ?: existing?.faviconUrl,
            previewImageUrl = link.previewImageUrl ?: existing?.previewImageUrl,
            contentType = link.contentType,
            personalNote = link.personalNote,
            collectionId = link.collectionId,
            isRead = link.isRead,
            isArchived = link.isArchived,
            metadataStatus = link.metadataStatus,
            retryCount = link.retryCount,
            lastFetchedAt = link.lastFetchedAt ?: existing?.lastFetchedAt,
            createdAt = link.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        savedLinkDao.updateLink(entity)
    }

    suspend fun deleteLink(id: Long) {
        savedLinkDao.deleteLink(id)
    }

    suspend fun setArchived(id: Long, isArchived: Boolean) {
        savedLinkDao.setArchived(id, isArchived)
    }

    suspend fun setRead(id: Long, isRead: Boolean) {
        savedLinkDao.setRead(id, isRead)
    }

    private fun SavedLinkWithCollection.toDomain(): SavedLink {
        return SavedLink(
            id = link.id,
            originalUrl = link.originalUrl,
            normalizedUrl = link.normalizedUrl,
            title = link.title,
            description = link.description,
            siteName = link.siteName,
            domain = link.domain,
            faviconUrl = link.faviconUrl,
            previewImageUrl = link.previewImageUrl,
            contentType = link.contentType,
            personalNote = link.personalNote,
            collectionId = link.collectionId,
            collectionName = collection?.name,
            isRead = link.isRead,
            isArchived = link.isArchived,
            metadataStatus = link.metadataStatus,
            retryCount = link.retryCount,
            lastFetchedAt = link.lastFetchedAt,
            createdAt = link.createdAt,
            updatedAt = link.updatedAt
        )
    }
}
