package com.laterbox.app.ui.inbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
}
