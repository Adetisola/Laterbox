package com.laterbox.app.media.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.laterbox.app.data.local.entity.SavedLinkEntity

@Entity(
    tableName = "media_inspection",
    foreignKeys = [
        ForeignKey(
            entity = SavedLinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["savedLinkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("savedLinkId"),
        Index("status")
    ]
)
data class MediaInspectionEntity(
    @PrimaryKey val id: String,
    val savedLinkId: Long,
    val url: String,
    val status: String,
    val resultJson: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
