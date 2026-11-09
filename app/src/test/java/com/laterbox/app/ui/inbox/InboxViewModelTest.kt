package com.laterbox.app.ui.inbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.local.entity.NotificationHistoryEntity
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.data.repository.NotificationHistoryRepository
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: LaterboxDatabase
    private lateinit var linkRepository: LinkRepository
    private lateinit var preferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        linkRepository = LinkRepository(database.savedLinkDao())
        preferencesRepository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inboxViewModel_observesLinksAndFiltersCorrectly() = runTest {
        val viewModel = InboxViewModel(linkRepository, preferencesRepository)

        // Save a test article and a test video
        linkRepository.saveLink(
            originalUrl = "https://example.com/article1",
            title = "First Article",
            contentType = ContentType.ARTICLE
        )
        linkRepository.saveLink(
            originalUrl = "https://youtube.com/watch?v=123",
            title = "Cooking Video",
            contentType = ContentType.VIDEO
        )

        viewModel.uiState.test {
            // First item might be initial loading or populated
            var state = awaitItem()
            while (state.isLoading || state.links.size < 2) {
                state = awaitItem()
            }

            assertEquals(2, state.links.size)
            assertFalse(state.isEmpty)

            // Test search filter
            viewModel.onSearchQueryChange("Cooking")
            state = awaitItem()
            while (state.links.size != 1 || state.links[0].title != "Cooking Video") {
                state = awaitItem()
            }
            assertEquals("Cooking Video", state.links[0].title)

            // Clear search
            viewModel.onClearSearch()
            state = awaitItem()
            while (state.links.size != 2) {
                state = awaitItem()
            }
            assertEquals(2, state.links.size)

            // Filter by ContentType
            viewModel.onFilterContentType(ContentType.ARTICLE)
            state = awaitItem()
            while (state.links.size != 1 || state.links[0].title != "First Article") {
                state = awaitItem()
            }
            assertEquals("First Article", state.links[0].title)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inboxViewModel_archiveRemovesLinkFromInbox() = runTest {
        val viewModel = InboxViewModel(linkRepository, preferencesRepository)

        val linkId = linkRepository.saveLink(
            originalUrl = "https://example.com/item",
            title = "Item to archive"
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.links.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(1, state.links.size)

            viewModel.onArchive(linkId)
            state = awaitItem()
            while (state.links.isNotEmpty()) {
                state = awaitItem()
            }
            assertEquals(0, state.links.size)
            assertTrue(state.isEmpty)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inboxViewModel_togglesViewMode() = runTest {
        val viewModel = InboxViewModel(linkRepository, preferencesRepository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) {
                state = awaitItem()
            }
            assertEquals(com.laterbox.app.data.preferences.InboxViewMode.HERO, state.viewMode)

            viewModel.onToggleViewMode()
            state = awaitItem()
            while (state.viewMode != com.laterbox.app.data.preferences.InboxViewMode.COMPACT) {
                state = awaitItem()
            }
            assertEquals(com.laterbox.app.data.preferences.InboxViewMode.COMPACT, state.viewMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkDeduplicatesNormalizedUrlAndMergesNotesNonDestructively() = runTest {
        val firstId = linkRepository.saveLink(
            originalUrl = "https://example.com/article?utm_source=newsletter",
            title = "Custom User Title",
            personalNote = "Initial note"
        )
        val secondId = linkRepository.saveLink(
            originalUrl = "https://example.com/article?utm_medium=social",
            title = "example.com",
            personalNote = "Follow-up thought"
        )

        assertEquals(firstId, secondId)
        val saved = database.savedLinkDao().getEntityById(firstId)
        assertEquals("Custom User Title", saved?.title)
        assertTrue(saved?.personalNote?.contains("Initial note") == true)
        assertTrue(saved?.personalNote?.contains("Follow-up thought") == true)
    }

    @Test
    fun refreshEnrichesPartialAndFailedLinksAndResetsRefreshingState() = runTest {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta property="og:title" content="Fetched OG Title" />
                    <meta property="og:description" content="Fetched OG Description" />
                    <meta property="og:image" content="/images/hero.png" />
                </head>
                <body></body>
                </html>
            """.trimIndent()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(html))

            val targetUrl = server.url("/post").toString()
            val id = database.savedLinkDao().insertLink(
                com.laterbox.app.data.local.entity.SavedLinkEntity(
                    originalUrl = targetUrl,
                    normalizedUrl = targetUrl,
                    title = "User Custom Title",
                    domain = "localhost",
                    previewImageUrl = null,
                    metadataStatus = com.laterbox.app.domain.model.MetadataStatus.PARTIAL
                )
            )

            val viewModel = InboxViewModel(linkRepository, preferencesRepository)
            viewModel.refresh()

            // Wait until repository finishes enriching the link
            var updated = database.savedLinkDao().getEntityById(id)
            var attempts = 0
            while (updated?.previewImageUrl == null && attempts < 50) {
                kotlinx.coroutines.delay(50)
                updated = database.savedLinkDao().getEntityById(id)
                attempts++
            }

            // Custom user title is preserved while missing description & OG image are populated
            assertEquals("User Custom Title", updated?.title)
            assertEquals("Fetched OG Description", updated?.description)
            assertEquals("${server.url("/")}images/hero.png", updated?.previewImageUrl)
            assertEquals(com.laterbox.app.domain.model.MetadataStatus.SUCCESS, updated?.metadataStatus)
            assertFalse(viewModel.uiState.value.isRefreshing)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun dateRangeFilter_filtersLinksCorrectly() = runTest {
        val viewModel = InboxViewModel(linkRepository, preferencesRepository)

        val now = System.currentTimeMillis()
        val oneDayAgo = now - 24 * 60 * 60 * 1000L
        val tenDaysAgo = now - 10 * 24 * 60 * 60 * 1000L

        database.savedLinkDao().insertLink(
            com.laterbox.app.data.local.entity.SavedLinkEntity(
                originalUrl = "https://example.com/today",
                normalizedUrl = "https://example.com/today",
                title = "Today Link",
                domain = "example.com",
                createdAt = now
            )
        )
        database.savedLinkDao().insertLink(
            com.laterbox.app.data.local.entity.SavedLinkEntity(
                originalUrl = "https://example.com/yesterday",
                normalizedUrl = "https://example.com/yesterday",
                title = "Yesterday Link",
                domain = "example.com",
                createdAt = oneDayAgo
            )
        )
        database.savedLinkDao().insertLink(
            com.laterbox.app.data.local.entity.SavedLinkEntity(
                originalUrl = "https://example.com/old",
                normalizedUrl = "https://example.com/old",
                title = "Old Link",
                domain = "example.com",
                createdAt = tenDaysAgo
            )
        )

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading || state.links.size < 3) {
                state = awaitItem()
            }
            assertEquals(3, state.links.size)

            // Filter Today
            viewModel.onFilterDateRange(DateRangeFilter.TODAY)
            state = awaitItem()
            while (state.links.size != 1 || state.links[0].title != "Today Link") {
                state = awaitItem()
            }
            assertEquals("Today Link", state.links[0].title)

            // Filter Last 7 Days (Today + Yesterday)
            viewModel.onFilterDateRange(DateRangeFilter.LAST_7_DAYS)
            state = awaitItem()
            while (state.links.size != 2) {
                state = awaitItem()
            }
            assertEquals(2, state.links.size)

            // Reset Filter
            viewModel.onFilterDateRange(DateRangeFilter.LAST_7_DAYS) // Toggle off -> ALL
            state = awaitItem()
            while (state.links.size != 3) {
                state = awaitItem()
            }
            assertEquals(3, state.links.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inboxViewModel_saveClipboardSuggestion_recordsHashAndSavesLink() = runTest {
        val viewModel = InboxViewModel(linkRepository, preferencesRepository)
        val suggestion = com.laterbox.app.capture.ClipboardSuggestion(
            url = "https://example.com/clipboard-save",
            domain = "example.com",
            hash = "testhash123"
        )

        var saved = false
        val job = viewModel.saveClipboardSuggestion(suggestion) {
            saved = true
        }
        job.join()
        testScheduler.advanceUntilIdle()

        assertTrue(saved)
        val savedLink = linkRepository.findDuplicate("https://example.com/clipboard-save")
        org.junit.Assert.assertNotNull(savedLink)

        val prefs = preferencesRepository.userPreferences.first()
        assertTrue(prefs.recentClipboardHistory.any { it.startsWith("testhash123:") })
    }

    @Test
    fun inboxViewModel_observesUnreadNotificationCount() = runTest {
        val notificationRepo = NotificationHistoryRepository(database.notificationHistoryDao())
        val viewModel = InboxViewModel(
            linkRepository = linkRepository,
            userPreferencesRepository = preferencesRepository,
            notificationHistoryRepository = notificationRepo
        )

        viewModel.uiState.test {
            var state = awaitItem()
            assertEquals(0, state.unreadNotificationCount)

            // Insert unread notification
            val notifId = notificationRepo.recordNotification(
                url = "https://example.com/notif",
                title = "Reminder",
                body = "Read now",
                origin = "SMART_DIGEST"
            )

            state = awaitItem()
            assertEquals(1, state.unreadNotificationCount)

            // Mark as read
            notificationRepo.markAsRead(notifId)
            state = awaitItem()
            assertEquals(0, state.unreadNotificationCount)
        }
    }
}


