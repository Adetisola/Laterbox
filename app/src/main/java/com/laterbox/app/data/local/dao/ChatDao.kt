package com.laterbox.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.laterbox.app.data.local.entity.ChatActionPermissionEntity
import com.laterbox.app.data.local.entity.ChatConversationEntity
import com.laterbox.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_conversations ORDER BY is_pinned DESC, updated_at DESC LIMIT :limit")
    fun getRecentConversations(limit: Int = 30): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ChatConversationEntity?

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    fun observeConversationById(id: String): Flow<ChatConversationEntity?>

    @Upsert
    suspend fun upsertConversation(conversation: ChatConversationEntity)

    @Query("DELETE FROM chat_conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getMessagesSnapshot(conversationId: String): List<ChatMessageEntity>

    @Upsert
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("SELECT COUNT(*) FROM chat_action_permissions WHERE conversation_id = :conversationId AND action_type = :actionType")
    suspend fun countActionPermission(conversationId: String, actionType: String): Int

    @Query("SELECT action_type FROM chat_action_permissions WHERE conversation_id = :conversationId")
    suspend fun getGrantedActionTypes(conversationId: String): List<String>

    @Upsert
    suspend fun grantActionPermission(permission: ChatActionPermissionEntity)

    @Query("DELETE FROM chat_action_permissions WHERE conversation_id = :conversationId")
    suspend fun revokeActionPermissions(conversationId: String)
}
