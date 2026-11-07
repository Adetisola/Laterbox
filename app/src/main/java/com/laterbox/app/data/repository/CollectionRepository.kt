package com.laterbox.app.data.repository

import com.laterbox.app.data.local.dao.CollectionDao
import com.laterbox.app.data.local.entity.CollectionEntity
import com.laterbox.app.data.local.entity.CollectionWithCountEntity
import com.laterbox.app.domain.model.Collection
import com.laterbox.app.domain.model.CollectionWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CollectionRepository(private val collectionDao: CollectionDao) {

    val collections: Flow<List<Collection>> = collectionDao.getCollections().map { list ->
        list.map { it.toDomain() }
    }

    val collectionsWithCounts: Flow<List<CollectionWithCount>> = collectionDao.getCollectionsWithCounts().map { list ->
        list.map { it.toDomain() }
    }

    fun getCollectionById(id: Long): Flow<Collection?> {
        return collectionDao.getCollectionById(id).map { it?.toDomain() }
    }

    suspend fun createCollection(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be empty" }
        val entity = CollectionEntity(name = trimmed)
        return collectionDao.insertCollection(entity)
    }

    suspend fun updateCollection(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be empty" }
        val entity = CollectionEntity(id = id, name = trimmed, updatedAt = System.currentTimeMillis())
        collectionDao.updateCollection(entity)
    }

    suspend fun deleteCollection(id: Long) {
        collectionDao.deleteCollection(id)
    }

    private fun CollectionEntity.toDomain() = Collection(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun CollectionWithCountEntity.toDomain() = CollectionWithCount(
        collection = Collection(
            id = id,
            name = name,
            createdAt = createdAt,
            updatedAt = updatedAt
        ),
        itemCount = itemCount
    )
}
