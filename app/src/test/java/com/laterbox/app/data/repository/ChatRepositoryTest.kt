package com.laterbox.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.local.LaterboxDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryTest {

    private lateinit var db: LaterboxDatabase
    private lateinit var repo: ChatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ChatRepository(db.chatDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addUserMessage_persistsConversationAndMessageImmediately() = runTest {
        val convId = repo.createConversation()
        val msg = repo.addUserMessage(convId, "Show me unread videos")

        val conv = repo.getConversation(convId)
        assertNotNull(conv)
        assertEquals("Show me unread videos", conv?.title)
        assertEquals("Show me unread videos", conv?.lastMessagePreview)

        val snapshot = repo.getMessagesSnapshot(convId)
        assertEquals(1, snapshot.size)
        assertEquals(msg.id, snapshot[0].id)
        assertEquals(ChatRole.USER.name, snapshot[0].role)
        assertEquals(ChatMessageStatus.COMPLETE.name, snapshot[0].status)
    }

    @Test
    fun updateAssistantMessage_transitionsStreamingToCompleteFailedOrCancelled() = runTest {
        val convId = repo.createConversation("Summarize unread links")
        repo.addUserMessage(convId, "Summarize unread links")
        val placeholder = repo.createAssistantPlaceholder(convId)

        var snapshot = repo.getMessagesSnapshot(convId)
        assertEquals(ChatMessageStatus.STREAMING.name, snapshot.last().status)

        repo.updateAssistantMessage(
            messageId = placeholder.id,
            conversationId = convId,
            content = "You have 3 unread articles.",
            status = ChatMessageStatus.COMPLETE,
            actionsJson = "[{\"type\":\"OPEN_COLLECTIONS\"}]",
            errorMessage = null
        )

        snapshot = repo.getMessagesSnapshot(convId)
        assertEquals(ChatMessageStatus.COMPLETE.name, snapshot.last().status)
        assertEquals("You have 3 unread articles.", snapshot.last().content)
        assertEquals("[{\"type\":\"OPEN_COLLECTIONS\"}]", snapshot.last().actionsJson)

        val failedPlaceholder = repo.createAssistantPlaceholder(convId)
        repo.updateAssistantMessage(
            messageId = failedPlaceholder.id,
            conversationId = convId,
            content = "",
            status = ChatMessageStatus.FAILED,
            errorMessage = "Request timed out."
        )
        snapshot = repo.getMessagesSnapshot(convId)
        assertEquals(ChatMessageStatus.FAILED.name, snapshot.last().status)
        assertEquals("Request timed out.", snapshot.last().errorMessage)

        repo.updateAssistantMessage(
            messageId = failedPlaceholder.id,
            conversationId = convId,
            content = "Partial text",
            status = ChatMessageStatus.CANCELLED,
            errorMessage = null
        )
        snapshot = repo.getMessagesSnapshot(convId)
        assertEquals(ChatMessageStatus.CANCELLED.name, snapshot.last().status)
        assertEquals("Partial text", snapshot.last().content)
    }

    @Test
    fun deleteConversation_cascadesAllMessages() = runTest {
        val convId = repo.createConversation("Delete me")
        repo.addUserMessage(convId, "Hello")
        repo.createAssistantPlaceholder(convId)

        assertEquals(2, repo.getMessagesSnapshot(convId).size)
        repo.deleteConversation(convId)

        assertNull(repo.getConversation(convId))
        assertTrue(repo.getMessagesSnapshot(convId).isEmpty())
    }
}
