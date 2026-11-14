package com.laterbox.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** "Always allow in this conversation" grant for one action type. Removed with its conversation. */
@Entity(
    tableName = "chat_action_permissions",
    primaryKeys = ["conversation_id", "action_type"],
    foreignKeys = [
        ForeignKey(
            entity = ChatConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversation_id"])]
)
data class ChatActionPermissionEntity(
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "action_type")
    val actionType: String,
    @ColumnInfo(name = "granted_at")
    val grantedAt: Long
)
