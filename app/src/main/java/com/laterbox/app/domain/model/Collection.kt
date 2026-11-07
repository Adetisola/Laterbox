package com.laterbox.app.domain.model

data class Collection(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CollectionWithCount(
    val collection: Collection,
    val itemCount: Int
)
