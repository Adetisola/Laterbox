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
import androidx.compose.ui.unit.dp
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
        // Status colors contrast
        val lightSuccessOnBg = contrastRatio(LightLaterboxColors.statusSuccess, LightLaterboxColors.backgroundPrimary)
        assertTrue("Light success must exceed 4.5:1 (AA), got $lightSuccessOnBg", lightSuccessOnBg >= 4.5)

        val lightDangerOnBg = contrastRatio(LightLaterboxColors.statusDanger, LightLaterboxColors.backgroundPrimary)
        assertTrue("Light danger must exceed 4.5:1 (AA), got $lightDangerOnBg", lightDangerOnBg >= 4.5)

        val lightWarningOnBg = contrastRatio(LightLaterboxColors.statusWarning, LightLaterboxColors.backgroundPrimary)
        assertTrue("Light warning must exceed 4.5:1 (AA), got $lightWarningOnBg", lightWarningOnBg >= 4.5)

        val darkSuccessOnBg = contrastRatio(DarkLaterboxColors.statusSuccess, DarkLaterboxColors.backgroundPrimary)
        assertTrue("Dark success must exceed 4.5:1 (AA), got $darkSuccessOnBg", darkSuccessOnBg >= 4.5)

        val darkDangerOnBg = contrastRatio(DarkLaterboxColors.statusDanger, DarkLaterboxColors.backgroundPrimary)
        assertTrue("Dark danger must exceed 4.5:1 (AA), got $darkDangerOnBg", darkDangerOnBg >= 4.5)

        val darkWarningOnBg = contrastRatio(DarkLaterboxColors.statusWarning, DarkLaterboxColors.backgroundPrimary)
        assertTrue("Dark warning must exceed 4.5:1 (AA), got $darkWarningOnBg", darkWarningOnBg >= 4.5)
    }

    @Test
    fun verifiesShapeAndElevationTokens() {
        assertEquals(16.dp, LaterboxSpacing.cardRadius)
        assertEquals(0.dp, LaterboxElevationTokens.flat)
        assertEquals(1.dp, LaterboxElevationTokens.card)
        assertEquals(6.dp, LaterboxElevationTokens.sheet)
        assertEquals(6.dp, LaterboxElevationTokens.floating)
        assertEquals(8.dp, LaterboxElevationTokens.dialog)
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

    @Test
    fun verifiesFontScalingAppliesCorrectly() {
        val original = DefaultLaterboxTypography
        val scaled = original.scaled(1.15f)
        assertTrue(scaled.cardTitle.fontSize.value > original.cardTitle.fontSize.value)
        assertTrue(scaled.badgeText.fontSize.value > original.badgeText.fontSize.value)
        assertTrue(scaled.bodySmall.fontSize.value > original.bodySmall.fontSize.value)
        assertTrue(scaled.labelMedium.fontSize.value > original.labelMedium.fontSize.value)
        assertTrue(scaled.titleLarge.fontSize.value > original.titleLarge.fontSize.value)
        assertTrue(scaled.badgeLabel.fontSize.value > original.badgeLabel.fontSize.value)

        val small = original.scaled(0.85f)
        assertTrue(small.cardTitle.fontSize.value < original.cardTitle.fontSize.value)
        assertTrue(small.badgeText.fontSize.value < original.badgeText.fontSize.value)
        assertTrue(small.bodySmall.fontSize.value < original.bodySmall.fontSize.value)
    }

    @Test
    fun rendersLaterboxComponentsCorrectly() {
        var clicked = false
        var confirmed = false

        composeTestRule.setContent {
            LaterboxTheme {
                com.laterbox.app.ui.components.LaterboxCard(
                    onClick = { clicked = true }
                ) {
                    Text("Card Content Inside")
                }

                com.laterbox.app.ui.components.LaterboxBadge(
                    text = "High Quality 1080p",
                    variant = com.laterbox.app.ui.components.LaterboxBadgeVariant.SUCCESS
                )

                com.laterbox.app.ui.components.LaterboxSearchField(
                    query = "android",
                    onQueryChange = {},
                    placeholder = "Search saved links..."
                )

                com.laterbox.app.ui.components.LaterboxConfirmDialog(
                    title = "Confirm Action",
                    message = "Are you sure you want to proceed?",
                    confirmText = "Proceed",
                    onConfirm = { confirmed = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Card Content Inside").assertExists()
        composeTestRule.onNodeWithText("High Quality 1080p").assertExists()
        composeTestRule.onNodeWithText("Confirm Action").assertExists()
        composeTestRule.onNodeWithText("Are you sure you want to proceed?").assertExists()
        composeTestRule.onNodeWithText("Proceed").assertExists()
    }
}
