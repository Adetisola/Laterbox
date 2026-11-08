package com.laterbox.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class SavedLinkWithCollection(
    @Embedded
    val link: SavedLinkEntity,

    @Relation(
        parentColumn = "collection_id",
        entityColumn = "id"
    )
    val legacyCollection: CollectionEntity? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = LinkCollectionCrossRef::class,
            parentColumn = "link_id",
            entityColumn = "collection_id"
        )
    )
    val crossRefCollections: List<CollectionEntity> = emptyList()
) {
    val collections: List<CollectionEntity>
        get() = (crossRefCollections + listOfNotNull(legacyCollection)).distinctBy { it.id }

    val collection: CollectionEntity?
        get() = collections.firstOrNull()
}
