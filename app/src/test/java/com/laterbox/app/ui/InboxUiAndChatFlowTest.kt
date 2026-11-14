package com.laterbox.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.data.local.entity.ChatConversationEntity
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.chat.ChatScreen
import com.laterbox.app.ui.chat.ChatUiState
import com.laterbox.app.ai.speech.ComposerSpeechUiState
import com.laterbox.app.ai.speech.SpeechStatus
import com.laterbox.app.ui.components.ComposerInsetHost
import com.laterbox.app.ui.components.DrawerPrimarySection
import com.laterbox.app.ui.components.FloatingChatComposer
import com.laterbox.app.ui.components.LaterboxDrawerContent
import com.laterbox.app.ui.inbox.InboxScreen
import com.laterbox.app.ui.inbox.InboxUiState
import com.laterbox.app.ui.theme.LaterboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxUiAndChatFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun floatingChatComposer_hasPlusMicAndSend_expandsOnMultilineOnlyAndShowsFallbackBadge() {
        var textState by mutableStateOf("")
        var plusClicks = 0
        var micClicks = 0
        var speechState by mutableStateOf(ComposerSpeechUiState())

        composeTestRule.setContent {
            LaterboxTheme {
                FloatingChatComposer(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = {},
                    onAddLinkClick = { plusClicks++ },
                    onMicClick = { micClicks++ },
                    speechState = speechState
                )
            }
        }

        composeTestRule.onNodeWithText("Message...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("composer_add_link_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("composer_mic_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("composer_send_button").assertIsDisplayed()
        assertEquals(1, plusClicks)
        assertEquals(1, micClicks)

        // Tapping/focusing input without wrapping text must keep single-row height
        val initialBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        val initialHeight = initialBounds.bottom - initialBounds.top
        composeTestRule.onNodeWithTag("floating_chat_input").performClick()
        composeTestRule.waitForIdle()
        val focusedBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        assertEquals(initialHeight, focusedBounds.bottom - focusedBounds.top)

        // Multiline text grows composer smoothly to 2 rows (input above, controls below)
        composeTestRule.onNodeWithTag("floating_chat_input").performTextInput("Line 1\nLine 2\nLine 3")
        composeTestRule.waitForIdle()

        val multiBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        val multiHeight = multiBounds.bottom - multiBounds.top
        assertTrue(
            "Expected multiline composer ($multiHeight) to grow taller than single-line ($initialHeight)",
            multiHeight > initialHeight
        )
        val inputBounds = composeTestRule.onNodeWithTag("floating_chat_input").getUnclippedBoundsInRoot()
        val plusBounds = composeTestRule.onNodeWithTag("composer_add_link_button").getUnclippedBoundsInRoot()
        assertTrue("Expected controls below input in multiline mode", plusBounds.top >= inputBounds.bottom - 2.dp)

        // Collapses back cleanly when back to single line
        textState = "Single line"
        composeTestRule.waitForIdle()
        val collapsedBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        assertTrue((collapsedBounds.bottom - collapsedBounds.top) < multiHeight)

        // Active fallback listening shows cancelable status badge
        speechState = ComposerSpeechUiState(status = SpeechStatus.TRANSCRIBING_FALLBACK, fallbackBadgeVisible = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speech_fallback_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Listening with Android speech recognition").assertIsDisplayed()
        composeTestRule.onNodeWithTag("composer_cancel_active_transcription").assertIsDisplayed()

        // Fallback notice indicator renders clearly after completion
        speechState = ComposerSpeechUiState(status = SpeechStatus.IDLE, fallbackBadgeVisible = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speech_fallback_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Using Android speech recognition").assertIsDisplayed()
    }

    @Test
    fun chatScreen_showsAiDisclosureWhenUnaccepted_andTriggersAccept() {
        var accepted = false
        composeTestRule.setContent {
            LaterboxTheme {
                ChatScreen(
                    uiState = ChatUiState(
                        assistantEnabled = true,
                        aiDisclosureAccepted = false
                    ),
                    onMenuClick = {},
                    onNewChatClick = {},
                    onRunHealthCheck = {},
                    onAcceptAiDisclosure = { accepted = true },
                    onOpenAiSettings = {},
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRetryMessage = {},
                    onQuickPromptClick = {},
                    onActionClick = {},
                    onConfirmMutatingAction = {},
                    onDismissMutatingAction = {},
                    onDismissNotice = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("ai_disclosure_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accept_ai_disclosure_button").performClick()
        assertTrue(accepted)
    }

    @Test
    fun chatScreen_showsInlineActionApprovalCardWithThreeChoices() {
        var denied = false
        var allowedOnce = false
        var alwaysAllowed = false
        composeTestRule.setContent {
            LaterboxTheme {
                ChatScreen(
                    uiState = ChatUiState(
                        assistantEnabled = true,
                        aiDisclosureAccepted = true,
                        pendingMutatingAction = ChatAction.RetryDownload(
                            taskId = "t-1",
                            title = "Kotlin Talk",
                            requiresConfirmation = true
                        )
                    ),
                    onMenuClick = {},
                    onNewChatClick = {},
                    onRunHealthCheck = {},
                    onAcceptAiDisclosure = {},
                    onOpenAiSettings = {},
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onStopStreaming = {},
                    onRetryMessage = {},
                    onQuickPromptClick = {},
                    onActionClick = {},
                    onConfirmMutatingAction = { allowedOnce = true },
                    onDismissMutatingAction = { denied = true },
                    onAlwaysAllowMutatingAction = { alwaysAllowed = true },
                    onDismissNotice = {},
                    onAddLinkClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("action_approval_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item: Kotlin Talk").assertIsDisplayed()
        composeTestRule.onNodeWithText("Don't allow").performClick()
        composeTestRule.onNodeWithText("Allow once").performClick()
        composeTestRule.onNodeWithText("Always allow in this conversation").performClick()
        assertTrue(denied && allowedOnce && alwaysAllowed)
        composeTestRule.onNodeWithTag("composer_add_link_button").assertIsDisplayed()
    }

    @Test
    fun composerInsetHost_appliesTightOpenKeyboardGapWithoutDoublePadding() {
        composeTestRule.setContent {
            LaterboxTheme {
                ComposerInsetHost(openKeyboardGap = 5.dp, closedKeyboardGap = 12.dp) {
                    FloatingChatComposer(
                        text = "",
                        onTextChange = {},
                        onSend = {},
                        onAddLinkClick = {},
                        onMicClick = {}
                    )
                }
            }
        }
        val hostBounds = composeTestRule.onNodeWithTag("composer_inset_host").getUnclippedBoundsInRoot()
        val composerBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        val bottomPad = hostBounds.bottom - composerBounds.bottom
        assertEquals(12.dp.value, bottomPad.value, 1.5f)
    }

    @Test
    fun inboxScreen_hasNoFloatingAddLinkFab() {
        val sampleLinks = (1..4).map { idx ->
            SavedLink(
                id = idx.toLong(),
                originalUrl = "https://example.com/$idx",
                normalizedUrl = "https://example.com/$idx",
                title = "Link $idx",
                domain = "example.com",
                contentType = ContentType.ARTICLE
            )
        }
        composeTestRule.setContent {
            LaterboxTheme {
                InboxScreen(
                    uiState = InboxUiState(links = sampleLinks, isLoading = false, hasCompletedOnboarding = true),
                    onSearchQueryChange = {}, onClearSearch = {}, onFilterContentType = {},
                    onFilterDateRange = {}, onLinkClick = {}, onQuickOpenClick = {},
                    onAddLinkClick = {}, onSettingsClick = {}, onToggleViewMode = {},
                    onRefresh = {}, onCompleteOnboarding = {}, onToggleRead = {},
                    onArchive = {}, onDelete = {}, onMenuClick = {},
                    bottomComposerHeightPx = 180
                )
            }
        }
        assertEquals(0, composeTestRule.onAllNodesWithContentDescription("Add Link").fetchSemanticsNodes().size)
    }

    @Test
    fun drawerContent_displaysRecentConversations_andConfirmsDelete() {
        var deletedId: String? = null
        val conv = ChatConversationEntity(
            id = "c-123",
            title = "Find failed YouTube downloads",
            createdAt = 1000L,
            updatedAt = 2000L,
            lastMessagePreview = "3 failed tasks",
            isPinned = false
        )

        composeTestRule.setContent {
            LaterboxTheme {
                LaterboxDrawerContent(
                    currentSection = DrawerPrimarySection.INBOX,
                    activeConversationId = null,
                    recentConversations = listOf(conv),
                    unreadNotificationCount = 2,
                    userName = "Timi",
                    onNavigateInbox = {},
                    onNavigateCollections = {},
                    onNavigateDownloads = {},
                    onNavigateArchive = {},
                    onNavigateNotifications = {},
                    onNavigateSettings = {},
                    onNewChatClick = {},
                    onConversationClick = {},
                    onDeleteConversation = { deletedId = it },
                    modifier = Modifier.width(304.dp)
                )
            }
        }

        composeTestRule.onNodeWithTag("laterbox_drawer_list")
            .performScrollToNode(hasText("Find failed YouTube downloads"))
        composeTestRule.onNodeWithText("Find failed YouTube downloads")
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Delete conversation")
            .performClick()
        composeTestRule.onNodeWithText("Delete conversation?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()
        assertEquals("c-123", deletedId)
    }

    @Test
    fun inboxScreen_preservesHoistedListStateAndHeaderBoundsOnScroll() {
        val sampleLinks = (1..25).map { idx ->
            SavedLink(
                id = idx.toLong(),
                originalUrl = "https://example.com/post/$idx",
                normalizedUrl = "https://example.com/post/$idx",
                title = "Saved Article #$idx",
                domain = "example.com",
                contentType = ContentType.ARTICLE
            )
        }
        val listState = LazyListState()
        var showInbox by mutableStateOf(true)

        composeTestRule.setContent {
            LaterboxTheme {
                if (showInbox) {
                    InboxScreen(
                        uiState = InboxUiState(
                            links = sampleLinks,
                            isLoading = false,
                            hasCompletedOnboarding = true
                        ),
                        onSearchQueryChange = {},
                        onClearSearch = {},
                        onFilterContentType = {},
                        onFilterDateRange = {},
                        onLinkClick = {},
                        onQuickOpenClick = {},
                        onAddLinkClick = {},
                        onSettingsClick = {},
                        onToggleViewMode = {},
                        onRefresh = {},
                        onCompleteOnboarding = {},
                        onToggleRead = {},
                        onArchive = {},
                        onDelete = {},
                        onMenuClick = {},
                        listState = listState
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Laterbox").assertIsDisplayed()
        composeTestRule.runOnIdle {
            kotlinx.coroutines.runBlocking {
                listState.scrollToItem(10)
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(10, listState.firstVisibleItemIndex)

        // Simulate navigating away and back with hoisted listState
        showInbox = false
        composeTestRule.waitForIdle()
        showInbox = true
        composeTestRule.waitForIdle()

        assertEquals(10, listState.firstVisibleItemIndex)
    }
}
