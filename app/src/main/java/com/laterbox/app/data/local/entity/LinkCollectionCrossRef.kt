package com.laterbox.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "link_collection_cross_ref",
    primaryKeys = ["link_id", "collection_id"],
    foreignKeys = [
        ForeignKey(
            entity = SavedLinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["link_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["collection_id"]),
        Index(value = ["link_id"])
    ]
)
data class LinkCollectionCrossRef(
    @ColumnInfo(name = "link_id")
    val linkId: Long,
    @ColumnInfo(name = "collection_id")
    val collectionId: Long
)
