package com.laterbox.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantActionHandlerTest {

    @Test
    fun parseModelResponse_stripsActionsBlockAndParsesAllowlistedActions() {
        val raw = """
            Here are your failed links. You can inspect them or open settings.
            [ACTIONS]
            [
              {"type":"SEARCH_LINKS","query":"kotlin"},
              {"type":"OPEN_LINK_DETAIL","linkId":42,"title":"Kotlin Video"},
              {"type":"OPEN_DOWNLOADS","initialTab":1},
              {"type":"OPEN_COLLECTIONS"}
            ]
            [/ACTIONS]
        """.trimIndent()

        val parsed = AssistantActionHandler.parseModelResponse(raw)
        assertEquals("Here are your failed links. You can inspect them or open settings.", parsed.displayText)
        assertEquals(4, parsed.actions.size)
        assertEquals(ChatAction.SearchLinks("kotlin"), parsed.actions[0])
        assertEquals(ChatAction.OpenLinkDetail(42L, "Kotlin Video"), parsed.actions[1])
        assertEquals(ChatAction.OpenDownloads(1), parsed.actions[2])
        assertEquals(ChatAction.OpenCollections, parsed.actions[3])
    }

    @Test
    fun parseModelResponse_rejectsUnknownActionsAndForcesConfirmationOnMutatingActions() {
        val raw = """
            Checking status.
            [ACTIONS]
            ```json
            [
              {"type":"DELETE_ALL_DATA"},
              {"type":"OPEN_SETTINGS","route":"settings/secret_admin"},
              {"type":"RETRY_INSPECTION","linkId":10,"title":"Broken Link","requiresConfirmation":false},
              {"type":"RETRY_DOWNLOAD","taskId":"task-9","title":"Broken Video","requiresConfirmation":false}
            ]
            ```
            [/ACTIONS]
        """.trimIndent()

        val parsed = AssistantActionHandler.parseModelResponse(raw)
        assertEquals("Checking status.", parsed.displayText)
        assertEquals(3, parsed.actions.size)
        assertEquals(ChatAction.OpenSettings("settings"), parsed.actions[0])

        val retryInsp = parsed.actions[1] as ChatAction.RetryInspection
        assertTrue(retryInsp.requiresConfirmation)
        assertEquals(10L, retryInsp.linkId)

        val retryDl = parsed.actions[2] as ChatAction.RetryDownload
        assertTrue(retryDl.requiresConfirmation)
        assertEquals("task-9", retryDl.taskId)
    }

    @Test
    fun roundTripSerialization_preservesAllowlistedActions() {
        val original = listOf(
            ChatAction.RunHealthCheck,
            ChatAction.OpenSettings("settings/reminders_ai"),
            ChatAction.RetryInspection(linkId = 7L, title = "Sample", requiresConfirmation = true)
        )
        val json = AssistantActionHandler.toJsonList(original)
        val restored = AssistantActionHandler.fromJsonList(json)
        assertEquals(original, restored)
    }

    @Test
    fun fromJsonList_returnsEmptyOnMalformedJson() {
        assertEquals(emptyList<ChatAction>(), AssistantActionHandler.fromJsonList("{not valid json"))
        assertEquals(emptyList<ChatAction>(), AssistantActionHandler.fromJsonList(null))
    }
}
