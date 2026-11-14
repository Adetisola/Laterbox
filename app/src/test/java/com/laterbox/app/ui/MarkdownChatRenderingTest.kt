package com.laterbox.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.laterbox.app.data.local.entity.ChatMessageEntity
import com.laterbox.app.data.repository.ChatMessageStatus
import com.laterbox.app.data.repository.ChatRole
import com.laterbox.app.ui.chat.ChatScreen
import com.laterbox.app.ui.chat.ChatUiState
import com.laterbox.app.ui.components.MarkdownText
import com.laterbox.app.ui.components.parseMarkdownDocumentSafely
import com.laterbox.app.ui.components.sanitizeMarkdownUrl
import com.laterbox.app.ui.theme.LaterboxTheme
import com.laterbox.app.ui.util.BrowserLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkdownChatRenderingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun SemanticsNodeInteraction.renderedAnnotatedText(): AnnotatedString {
        return fetchSemanticsNode().config[SemanticsProperties.Text].first()
    }

    private fun SemanticsNodeInteraction.clickTextSubstring(substring: String) {
        val node = fetchSemanticsNode()
        val fullText = node.config[SemanticsProperties.Text].first().text
        val startIdx = fullText.indexOf(substring)
        assertTrue("Substring '$substring' not found in '$fullText'", startIdx >= 0)
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.first()
        val midIdx = startIdx + (substring.length / 2)
        val box = layout.getBoundingBox(midIdx)
        val clickPoint = if (box.width > 0f) {
            box.center
        } else {
            layout.getBoundingBox(startIdx).center
        }
        performTouchInput { click(clickPoint) }
    }

    @Test
    fun urlSanitizer_allowsOnlyHttpAndHttpsAndBlocksUnsafeSchemes() {
        assertEquals("https://example.com/docs", sanitizeMarkdownUrl("https://example.com/docs"))
        assertEquals("http://localhost:8080/health", sanitizeMarkdownUrl("http://localhost:8080/health"))

        assertNull(sanitizeMarkdownUrl("javascript:alert(1)"))
        assertNull(sanitizeMarkdownUrl("JAVASCRIPT:void(0)"))
        assertNull(sanitizeMarkdownUrl("file:///etc/passwd"))
        assertNull(sanitizeMarkdownUrl("content://com.laterbox.app/db"))
        assertNull(sanitizeMarkdownUrl("intent://scan/#Intent;end"))
        assertNull(sanitizeMarkdownUrl("data:text/html,<script>alert(1)</script>"))
        assertNull(sanitizeMarkdownUrl(""))
        assertNull(sanitizeMarkdownUrl("relative/path"))

        assertTrue(BrowserLauncher.isSafeHttpUrl("https://laterbox.app"))
        assertFalse(BrowserLauncher.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(BrowserLauncher.isSafeHttpUrl("file:///sdcard/secret.txt"))
    }

    @Test
    fun markdownRenderer_supportsHeadingsListsFormattingCodeBlocksBlockquotesTablesAndRules() {
        val sampleMarkdown = """
            # Main Heading
            ## Subheading
            ### Section Three

            Paragraph with **bold**, *italic*, ***both***, ~~struck~~, and `inlineCode()`.

            - First bullet
              - Nested bullet
            - Second bullet

            3. Numbered item three
            4. Numbered item four

            > Quoted insight with **emphasis**

            ---

            | Feature | Status |
            | --- | --- |
            | Markdown | Ready |

            ```kotlin
            fun greet(name: String) {
                println("Hello " + name)
            }
            ```
        """.trimIndent()

        var copiedText: String? = null
        composeTestRule.setContent {
            LaterboxTheme(darkTheme = false) {
                val clipboard = LocalClipboardManager.current
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    MarkdownText(
                        markdown = sampleMarkdown,
                        isUserMessage = false
                    )
                    androidx.compose.material3.Button(onClick = {
                        copiedText = clipboard.getText()?.text
                    }) {
                        androidx.compose.material3.Text("ReadClipboard")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Main Heading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subheading").assertIsDisplayed()
        composeTestRule.onNodeWithText("Section Three").assertIsDisplayed()
        composeTestRule.onNodeWithText("inlineCode()", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("First bullet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nested bullet").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Numbered item three").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_blockquote").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Quoted insight with emphasis").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_thematic_break").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_table").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Feature").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_code_block").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("kotlin").assertIsDisplayed()
        composeTestRule.onNodeWithText("fun greet(name: String)", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("markdown_copy_code_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ReadClipboard").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(copiedText?.contains("fun greet(name: String)") == true)
    }

    @Test
    fun markdownRenderer_opensSafeLinksAndSuppressesUnsafeSchemesAndHtml() {
        val openedUrls = mutableListOf<String>()
        val content = """
            Check [Safe Docs](https://example.com/guide) and https://laterbox.example/help.

            Spacer line one

            Spacer line two

            Ignore [Bad JS](javascript:alert(1)) and [Bad File](file:///etc/hosts).

            Plain <script>alert(1)</script> text stays passive.
        """.trimIndent()

        composeTestRule.setContent {
            LaterboxTheme {
                MarkdownText(
                    markdown = content,
                    isUserMessage = false,
                    onLinkClick = { openedUrls += it }
                )
            }
        }

        val safeParagraphNode = composeTestRule.onNodeWithText("Safe Docs", substring = true)
        val safeAnnotated = safeParagraphNode.renderedAnnotatedText()
        val safeLinks = safeAnnotated.getLinkAnnotations(0, safeAnnotated.length)
            .mapNotNull { (it.item as? LinkAnnotation.Url)?.url }
        assertEquals(
            listOf("https://example.com/guide", "https://laterbox.example/help"),
            safeLinks
        )

        // Tap Safe Docs and autolinked URL; verify callback invocations
        safeParagraphNode.clickTextSubstring("Safe Docs")
        composeTestRule.waitForIdle()
        safeParagraphNode.clickTextSubstring("https://laterbox.example/help")
        composeTestRule.waitForIdle()
        assertEquals(
            listOf("https://example.com/guide", "https://laterbox.example/help"),
            openedUrls
        )

        // Unsafe links have zero LinkAnnotations attached and remain passive text even when tapped
        val unsafeParagraphNode = composeTestRule.onNodeWithText("Bad JS", substring = true)
        unsafeParagraphNode.assertIsDisplayed()
        val unsafeAnnotated = unsafeParagraphNode.renderedAnnotatedText()
        assertEquals(0, unsafeAnnotated.getLinkAnnotations(0, unsafeAnnotated.length).size)
        unsafeParagraphNode.clickTextSubstring("Bad JS")
        unsafeParagraphNode.clickTextSubstring("Bad File")
        composeTestRule.waitForIdle()
        assertEquals(
            listOf("https://example.com/guide", "https://laterbox.example/help"),
            openedUrls
        )

        // Raw HTML tags are suppressed cleanly
        assertEquals(0, composeTestRule.onAllNodesWithText("<script>", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun chatScreen_rendersBothUserAndAssistantMessagesWithMarkdown_inLightAndDarkThemes() {
        var darkTheme by mutableStateOf(false)
        val clickedUrls = mutableListOf<String>()
        val messages = listOf(
            ChatMessageEntity(
                id = "m-user",
                conversationId = "c-1",
                role = ChatRole.USER.name,
                content = "Please check **this repo** at [Laterbox](https://github.com/Adetisola/Laterbox) and `yt-dlp`",
                createdAt = 1000L,
                status = ChatMessageStatus.COMPLETE.name
            ),
            ChatMessageEntity(
                id = "m-assistant",
                conversationId = "c-1",
                role = ChatRole.ASSISTANT.name,
                content = "## Diagnostic Result\n- Status: **Healthy**\n- Command: `checkHealth()`\n\n> Everything looks good.",
                createdAt = 2000L,
                status = ChatMessageStatus.COMPLETE.name
            )
        )

        composeTestRule.setContent {
            LaterboxTheme(darkTheme = darkTheme) {
                ChatScreen(
                    uiState = ChatUiState(
                        assistantEnabled = true,
                        aiDisclosureAccepted = true,
                        messages = messages
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
                    onConfirmMutatingAction = {},
                    onDismissMutatingAction = {},
                    onDismissNotice = {},
                    onMarkdownLinkClick = { clickedUrls += it }
                )
            }
        }

        // User bubble Markdown
        val userNode = composeTestRule.onNodeWithText("Please check this repo at Laterbox and yt-dlp")
        userNode.assertIsDisplayed()
        userNode.clickTextSubstring("Laterbox")
        composeTestRule.waitForIdle()
        assertEquals(listOf("https://github.com/Adetisola/Laterbox"), clickedUrls)

        // Assistant bubble Markdown
        composeTestRule.onNodeWithText("Diagnostic Result").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status: Healthy").assertIsDisplayed()
        composeTestRule.onNodeWithTag("markdown_blockquote").assertIsDisplayed()

        // Switch to Dark theme cleanly without crashes or loss
        darkTheme = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Diagnostic Result").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please check this repo at Laterbox and yt-dlp").assertIsDisplayed()
    }

    @Test
    fun markdownRenderer_handlesStreamingPartialsAndDeeplyNestedInputGracefully() {
        var streamedText by mutableStateOf("### Streaming\n**Unclosed bold and `code")
        val streamingMsg = ChatMessageEntity(
            id = "m-stream",
            conversationId = "c-1",
            role = ChatRole.ASSISTANT.name,
            content = "",
            createdAt = 1000L,
            status = ChatMessageStatus.STREAMING.name
        )

        composeTestRule.setContent {
            LaterboxTheme {
                ChatScreen(
                    uiState = ChatUiState(
                        assistantEnabled = true,
                        aiDisclosureAccepted = true,
                        messages = listOf(streamingMsg),
                        isStreaming = true,
                        streamingAssistantMessageId = "m-stream",
                        streamingPartialText = streamedText
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
                    onConfirmMutatingAction = {},
                    onDismissMutatingAction = {},
                    onDismissNotice = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Streaming").assertIsDisplayed()

        // Progressively stream an unclosed fenced code block, then close it
        streamedText += "`\n\n```python\nprint('hello"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("markdown_code_block").assertIsDisplayed()

        streamedText += "')\n```"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("print('hello')", substring = true).assertIsDisplayed()

        // Malformed / deeply nested input parses safely without throwing
        val extremeNesting = "> ".repeat(150) + "[[[[deep"
        assertNotNull(parseMarkdownDocumentSafely(extremeNesting))
    }
}
