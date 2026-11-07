package com.laterbox.app.ui.theme

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import com.laterbox.app.ui.components.BottomBarDestination
import com.laterbox.app.ui.components.EmptyStateView
import com.laterbox.app.ui.components.LaterboxBottomBar
import com.laterbox.app.ui.components.SavedLinkCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaterboxThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifiesAccessibleColorContrast() {
        // WCAG AAA requires >= 7.0:1 for normal text, AA requires >= 4.5:1
        fun contrastRatio(c1: Color, c2: Color): Double {
            val l1 = c1.luminance() + 0.05
            val l2 = c2.luminance() + 0.05
            return if (l1 > l2) l1 / l2 else l2 / l1
        }

        // Light Theme Contrasts
        val lightTextOnBg = contrastRatio(LightLaterboxColors.textPrimary, LightLaterboxColors.backgroundPrimary)
        assertTrue("Light primary text must exceed 7.0:1 (AAA), got $lightTextOnBg", lightTextOnBg >= 7.0)

        val lightSecOnBg = contrastRatio(LightLaterboxColors.textSecondary, LightLaterboxColors.backgroundPrimary)
        assertTrue("Light secondary text must exceed 4.5:1 (AA), got $lightSecOnBg", lightSecOnBg >= 4.5)

        // Dark Theme Contrasts
        val darkTextOnBg = contrastRatio(DarkLaterboxColors.textPrimary, DarkLaterboxColors.backgroundPrimary)
        assertTrue("Dark primary text must exceed 7.0:1 (AAA), got $darkTextOnBg", darkTextOnBg >= 7.0)

        val darkSecOnBg = contrastRatio(DarkLaterboxColors.textSecondary, DarkLaterboxColors.backgroundPrimary)
        assertTrue("Dark secondary text must exceed 4.5:1 (AA), got $darkSecOnBg", darkSecOnBg >= 4.5)
    }

    @Test
    fun rendersSavedLinkCardBothImageAndTextOnlyInCompose() {
        val imageLink = SavedLink(
            id = 1,
            originalUrl = "https://youtube.com/watch?v=123",
            normalizedUrl = "https://youtube.com/watch?v=123",
            title = "Kotlin Conf Talk",
            domain = "youtube.com",
            previewImageUrl = "https://example.com/thumbnail.jpg",
            contentType = ContentType.VIDEO
        )

        val textLink = SavedLink(
            id = 2,
            originalUrl = "https://news.ycombinator.com/item?id=1",
            normalizedUrl = "https://news.ycombinator.com/item?id=1",
            title = "Hacker News Discussion",
            domain = "news.ycombinator.com",
            description = "Discussion on software craft",
            contentType = ContentType.DISCUSSION
        )

        composeTestRule.setContent {
            LaterboxTheme(darkTheme = false) {
                SavedLinkCard(
                    link = imageLink,
                    onClick = {},
                    onQuickOpenClick = {}
                )
                SavedLinkCard(
                    link = textLink,
                    onClick = {},
                    onQuickOpenClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Kotlin Conf Talk").assertExists()
        composeTestRule.onNodeWithText("Hacker News Discussion").assertExists()
        composeTestRule.onNodeWithText("VIDEO").assertExists()
        composeTestRule.onNodeWithText("DISCUSSION").assertExists()
    }

    @Test
    fun rendersEmptyStateAndBottomBar() {
        var selectedDestination = BottomBarDestination.INBOX

        composeTestRule.setContent {
            LaterboxTheme(darkTheme = true) {
                EmptyStateView(
                    title = "Nothing saved. Yet.",
                    subtitle = "When something catches your attention, send it here.",
                    actionText = "+ Add link manually",
                    onActionClick = {}
                )

                LaterboxBottomBar(
                    currentDestination = selectedDestination,
                    onDestinationSelected = { selectedDestination = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Nothing saved. Yet.").assertExists()
        composeTestRule.onNodeWithText("+ Add link manually").assertExists()
        composeTestRule.onNodeWithText("Inbox").assertExists()
        composeTestRule.onNodeWithText("Collections").assertExists()
        composeTestRule.onNodeWithText("Archive").assertExists()
    }
}
