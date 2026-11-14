package com.laterbox.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.laterbox.app.ai.ChatAction
import com.laterbox.app.ui.chat.ChatScreen
import com.laterbox.app.ui.chat.ChatUiState
import com.laterbox.app.ui.components.FloatingChatComposer
import com.laterbox.app.ui.components.appendDictatedTranscript
import com.laterbox.app.ui.theme.LaterboxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingChatComposerInputTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun currentSelection(): TextRange {
        return composeTestRule.onNodeWithTag("floating_chat_input")
            .fetchSemanticsNode()
            .config[SemanticsProperties.TextSelectionRange]
    }

    private fun currentEditableText(): String {
        return composeTestRule.onNodeWithTag("floating_chat_input")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text
    }

    private fun runAllEightScenarios(
        getText: () -> String,
        setExternalText: (String) -> Unit,
        triggerSend: () -> Unit
    ) {
        val input = composeTestRule.onNodeWithTag("floating_chat_input")

        // 1 & 2. Type a word and delete it character by character, including the final remaining char
        input.performClick()
        input.performTextInput("word")
        composeTestRule.waitForIdle()
        assertEquals("word", getText())

        repeat(4) {
            input.performKeyInput { pressKey(Key.Backspace) }
            composeTestRule.waitForIdle()
        }
        assertEquals("", getText())
        assertEquals("", currentEditableText())
        composeTestRule.onNodeWithText("Message...").assertIsDisplayed()

        // Also test single character type -> backspace back to initially-captured ""
        input.performTextInput("x")
        composeTestRule.waitForIdle()
        assertEquals("x", getText())
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        assertEquals("", getText())
        assertEquals("", currentEditableText())

        // 3. Select all text and press delete
        input.performTextInput("Select and clear")
        composeTestRule.waitForIdle()
        assertEquals("Select and clear", getText())
        input.performTextInputSelection(TextRange(0, "Select and clear".length))
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        assertEquals("", getText())
        assertEquals("", currentEditableText())

        // 4. Select all text and replace it with another sentence
        input.performTextInput("Initial draft sentence")
        composeTestRule.waitForIdle()
        input.performTextInputSelection(TextRange(0, "Initial draft sentence".length))
        composeTestRule.waitForIdle()
        input.performTextReplacement("Replaced completely")
        composeTestRule.waitForIdle()
        assertEquals("Replaced completely", getText())
        assertEquals("Replaced completely", currentEditableText())

        // 5. Delete text in the middle while preserving the cursor position
        input.performTextClearance()
        composeTestRule.waitForIdle()
        input.performTextInput("hello world")
        composeTestRule.waitForIdle()
        // Place cursor right after "hello" (index 5) and backspace the 'o'
        input.performTextInputSelection(TextRange(5))
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        assertEquals("hell world", getText())
        assertEquals("Cursor must stay at index 4 after middle delete", TextRange(4), currentSelection())

        // Insert a character at preserved middle cursor to prove it didn't jump to end
        input.performTextInput("o")
        composeTestRule.waitForIdle()
        assertEquals("hello world", getText())
        assertEquals(TextRange(5), currentSelection())

        // 6. Clear composer after sending a message
        triggerSend()
        composeTestRule.waitForIdle()
        assertEquals("", getText())
        assertEquals("", currentEditableText())

        // 7. Append a dictated transcript to an existing draft
        input.performTextInput("Summarize")
        composeTestRule.waitForIdle()
        setExternalText(appendDictatedTranscript(getText(), "my unread links"))
        composeTestRule.waitForIdle()
        assertEquals("Summarize my unread links", getText())
        assertEquals("Summarize my unread links", currentEditableText())
        assertEquals(TextRange("Summarize my unread links".length), currentSelection())

        // Verify appended draft can still be fully cleared back to empty
        input.performTextInputSelection(TextRange(0, getText().length))
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        assertEquals("", getText())
    }

    @Test
    fun inboxComposerHost_passesAllInputIntegrityAndDeletionScenarios() {
        var inboxText by mutableStateOf("")
        val sentMessages = mutableListOf<String>()

        composeTestRule.setContent {
            LaterboxTheme {
                FloatingChatComposer(
                    text = inboxText,
                    onTextChange = { inboxText = it },
                    onSend = {
                        val trimmed = inboxText.trim()
                        if (trimmed.isNotEmpty()) {
                            sentMessages += trimmed
                            inboxText = ""
                        }
                    },
                    onAddLinkClick = {},
                    onMicClick = {}
                )
            }
        }

        runAllEightScenarios(
            getText = { inboxText },
            setExternalText = { inboxText = it },
            triggerSend = {
                composeTestRule.onNodeWithTag("composer_send_button").performClick()
            }
        )
        assertEquals(listOf("hello world"), sentMessages)
    }

    @Test
    fun chatScreenComposerHost_passesAllInputIntegrityAndDeletionScenarios() {
        val composerFlow = MutableStateFlow("")
        val sentMessages = mutableListOf<String>()

        composeTestRule.setContent {
            val composerText by composerFlow.collectAsState()
            LaterboxTheme {
                ChatScreen(
                    uiState = ChatUiState(
                        assistantEnabled = true,
                        aiDisclosureAccepted = true,
                        composerText = composerText
                    ),
                    onMenuClick = {},
                    onNewChatClick = {},
                    onRunHealthCheck = {},
                    onAcceptAiDisclosure = {},
                    onOpenAiSettings = {},
                    onComposerTextChange = { composerFlow.value = it },
                    onSendMessage = {
                        val trimmed = composerFlow.value.trim()
                        if (trimmed.isNotEmpty()) {
                            sentMessages += trimmed
                            composerFlow.value = ""
                        }
                    },
                    onStopStreaming = {},
                    onRetryMessage = {},
                    onQuickPromptClick = {},
                    onActionClick = {},
                    onConfirmMutatingAction = {},
                    onDismissMutatingAction = {},
                    onDismissNotice = {},
                    onAddLinkClick = {},
                    onMicClick = {}
                )
            }
        }

        val input = composeTestRule.onNodeWithTag("floating_chat_input")
        // Verify ChatScreen custom placeholder doesn't interfere either
        composeTestRule.onNodeWithText("Ask about your links, downloads, or app health...").assertIsDisplayed()

        // 1 & 2. Type and delete char by char including final char
        input.performClick()
        input.performTextInput("chat")
        composeTestRule.waitForIdle()
        assertEquals("chat", composerFlow.value)
        repeat(4) {
            input.performKeyInput { pressKey(Key.Backspace) }
            composeTestRule.waitForIdle()
        }
        assertEquals("", composerFlow.value)
        assertEquals("", currentEditableText())

        // 3. Select all & delete
        input.performTextInput("Delete all in chat")
        composeTestRule.waitForIdle()
        input.performTextInputSelection(TextRange(0, composerFlow.value.length))
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        assertEquals("", composerFlow.value)

        // 4. Select all & replace
        input.performTextInput("Old prompt")
        composeTestRule.waitForIdle()
        input.performTextInputSelection(TextRange(0, composerFlow.value.length))
        composeTestRule.waitForIdle()
        input.performTextReplacement("New prompt")
        composeTestRule.waitForIdle()
        assertEquals("New prompt", composerFlow.value)

        // 5. Middle delete keeping cursor
        input.performTextInputSelection(TextRange(3))
        composeTestRule.waitForIdle()
        input.performKeyInput { pressKey(Key.Backspace) }
        composeTestRule.waitForIdle()
        assertEquals("Ne prompt", composerFlow.value)
        assertEquals(TextRange(2), currentSelection())

        // 6. Send clears composer
        composeTestRule.onNodeWithTag("composer_send_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("", composerFlow.value)
        assertEquals("", currentEditableText())
        assertEquals(listOf("Ne prompt"), sentMessages)

        // 7. Dictation appends to draft
        input.performTextInput("Find")
        composeTestRule.waitForIdle()
        composerFlow.value = appendDictatedTranscript(composerFlow.value, "saved podcasts")
        composeTestRule.waitForIdle()
        assertEquals("Find saved podcasts", composerFlow.value)
        assertEquals("Find saved podcasts", currentEditableText())
        assertEquals(TextRange("Find saved podcasts".length), currentSelection())
    }

    @Test
    fun composer_preservesFocusAndSelectionAcrossWrapCollapseNewlineAndApprovalCard() {
        var text by mutableStateOf("")
        var pendingApproval by mutableStateOf<ChatAction?>(null)

        composeTestRule.setContent {
            LaterboxTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    FloatingChatComposer(
                        text = text,
                        onTextChange = { text = it },
                        onSend = {},
                        onAddLinkClick = {},
                        onMicClick = {},
                        pendingApproval = pendingApproval,
                        onDenyAction = { pendingApproval = null },
                        onAllowActionOnce = { pendingApproval = null },
                        onAlwaysAllowAction = { pendingApproval = null }
                    )
                }
            }
        }

        val input = composeTestRule.onNodeWithTag("floating_chat_input")
        input.performClick()
        composeTestRule.waitForIdle()
        input.assertIsFocused()

        val singleBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        val singleHeight = singleBounds.bottom - singleBounds.top

        // 1. Single-line -> multi-line wrap by typing a long sentence (sized to wrap even under compact Robolectric font metrics)
        val longLine =
            "This is a deliberately long sentence designed to wrap cleanly across multiple lines inside the composer without requiring any manual newline characters at all"
        input.performTextInput(longLine)
        composeTestRule.waitForIdle()

        input.assertIsFocused()
        assertEquals(TextRange(longLine.length), currentSelection())
        val wrappedBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected wrapped composer (${wrappedBounds.bottom - wrappedBounds.top}) to exceed single-line ($singleHeight)",
            (wrappedBounds.bottom - wrappedBounds.top) > singleHeight
        )
        val wrappedInputBounds = input.getUnclippedBoundsInRoot()
        val wrappedPlusBounds = composeTestRule.onNodeWithTag("composer_add_link_button").getUnclippedBoundsInRoot()
        assertTrue(wrappedPlusBounds.top >= wrappedInputBounds.bottom - 2.dp)

        // 2. Multi-line -> single-line collapse back via select-all + replacement with short text
        input.performTextInputSelection(TextRange(0, longLine.length))
        composeTestRule.waitForIdle()
        input.performTextReplacement("Short")
        composeTestRule.waitForIdle()

        input.assertIsFocused()
        assertEquals("Short", text)
        assertEquals(TextRange(5), currentSelection())
        val collapsedBounds = composeTestRule.onNodeWithTag("floating_chat_composer").getUnclippedBoundsInRoot()
        assertEquals(singleHeight, collapsedBounds.bottom - collapsedBounds.top)

        // 3. Explicit newline insertion keeps focus & places cursor at end
        input.performTextInput("\nSecond line")
        composeTestRule.waitForIdle()
        input.assertIsFocused()
        assertEquals("Short\nSecond line", text)
        assertEquals(TextRange("Short\nSecond line".length), currentSelection())

        // 4. Inline ActionApprovalCard appears and dismisses while user draft & focus remain intact
        pendingApproval = ChatAction.RetryInspection(linkId = 42L, title = "Kotlin Conf Talk")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("action_approval_card").assertIsDisplayed()
        input.assertIsFocused()
        assertEquals("Short\nSecond line", currentEditableText())
        assertEquals(TextRange("Short\nSecond line".length), currentSelection())

        // Place cursor in middle while approval card is visible, then dismiss card
        input.performTextInputSelection(TextRange(3))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("approval_deny").performClick()
        composeTestRule.waitForIdle()

        input.assertIsFocused()
        assertEquals("Short\nSecond line", text)
        assertEquals(TextRange(3), currentSelection())
    }
}
