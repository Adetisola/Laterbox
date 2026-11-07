package com.laterbox.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SavedLinkWithCollection(
    @Embedded
    val link: SavedLinkEntity,

    @Relation(
        parentColumn = "collection_id",
        entityColumn = "id"
    )
    val collection: CollectionEntity?
)
