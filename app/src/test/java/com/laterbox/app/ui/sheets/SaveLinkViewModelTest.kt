package com.laterbox.app.ui.sheets

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.laterbox.app.data.local.LaterboxDatabase
import com.laterbox.app.data.metadata.ExtractedMetadata
import com.laterbox.app.data.repository.CollectionRepository
import com.laterbox.app.data.repository.LinkRepository
import com.laterbox.app.domain.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveLinkViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: LaterboxDatabase
    private lateinit var linkRepository: LinkRepository
    private lateinit var collectionRepository: CollectionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LaterboxDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        linkRepository = LinkRepository(database.savedLinkDao())
        collectionRepository = CollectionRepository(database.collectionDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun createViewModel(
        extractor: (suspend (String) -> Result<ExtractedMetadata>)? = null
    ): SaveLinkViewModel = SaveLinkViewModel(
        linkRepository = linkRepository,
        collectionRepository = collectionRepository,
        metadataExtractor = extractor
    )

    @Test
    fun saveLinkViewModel_detectsDuplicateUrlDebounced() = runTest(testDispatcher) {
        // Pre-save a link
        linkRepository.saveLink(
            originalUrl = "https://example.com/unique-article",
            title = "Existing Unique Article"
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            runCurrent()
            val initial = awaitItem()
            assertNull(initial.duplicateLink)

            // Input matching URL
            viewModel.onUrlChange("example.com/unique-article")
            runCurrent()
            val typing = awaitItem()
            assertEquals("example.com/unique-article", typing.url)

            // Advance debounce timer (250ms) and complete work
            advanceTimeBy(300)
            advanceUntilIdle()

            val detected = awaitItem()
            assertNotNull(detected.duplicateLink)
            assertEquals("Existing Unique Article", detected.duplicateLink?.title)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_savesNewLinkSuccessfully() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        val reminderTime = 1_800_000_000_000L
        viewModel.onReminderAtChange(reminderTime)
        viewModel.onUrlChange("https://kotlinlang.org")
        viewModel.onTitleChange("Kotlin Programming Language")
        viewModel.onPersonalNoteChange("Official documentation")

        var successCalled = false
        viewModel.onSave {
            successCalled = true
        }

        runCurrent()

        // Verify saved in repository
        val duplicate = linkRepository.findDuplicate("https://kotlinlang.org")
        assertNotNull(duplicate)
        assertEquals("Kotlin Programming Language", duplicate?.title)
        assertEquals("Official documentation", duplicate?.personalNote)
        assertEquals(reminderTime, duplicate?.reminderAt)
        assertTrue(successCalled)
    }

    @Test
    fun saveLinkViewModel_emptyUrlDisplaysInlineError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            runCurrent()
            awaitItem() // initial

            var successCalled = false
            viewModel.onUrlChange("")
            viewModel.onSave { successCalled = true }
            runCurrent()

            val state = awaitItem()
            assertEquals("Please enter a website address", state.urlError)
            assertFalse(successCalled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_invalidUrlDisplaysInlineError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            runCurrent()
            awaitItem() // initial

            var successCalled = false
            viewModel.onUrlChange("invalid url without dot")
            runCurrent()
            awaitItem() // url change

            viewModel.onSave { successCalled = true }
            runCurrent()

            val state = awaitItem()
            assertEquals("Please enter a valid website address", state.urlError)
            assertFalse(successCalled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_typingClearsUrlError() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            runCurrent()
            awaitItem() // initial

            viewModel.onUrlChange("")
            viewModel.onSave { }
            runCurrent()
            val errorState = awaitItem()
            assertEquals("Please enter a website address", errorState.urlError)

            viewModel.onUrlChange("https://valid.com")
            runCurrent()
            val typingState = awaitItem()
            assertNull(typingState.urlError)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_characterLimitEnforced() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.uiState.test {
            runCurrent()
            awaitItem() // initial

            val longNote = "a".repeat(600)
            viewModel.onPersonalNoteChange(longNote)
            runCurrent()
            val noteState = awaitItem()
            assertEquals(500, noteState.personalNote.length)

            val longTitle = "b".repeat(300)
            viewModel.onTitleChange(longTitle)
            runCurrent()
            val titleState = awaitItem()
            assertEquals(200, titleState.title.length)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_togglesCollectionsAndCreatesNew() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            runCurrent()
            val initial = awaitItem()
            assertTrue(initial.selectedCollectionIds.isEmpty())

            // Toggle collection on
            viewModel.onToggleCollection(42L)
            runCurrent()
            val stateOn = awaitItem()
            assertTrue(stateOn.selectedCollectionIds.contains(42L))

            // Toggle collection off
            viewModel.onToggleCollection(42L)
            runCurrent()
            val stateOff = awaitItem()
            assertFalse(stateOff.selectedCollectionIds.contains(42L))

            // Create new collection
            viewModel.onCreateCollection("Design")
            runCurrent()
            var stateCreated = awaitItem()
            while (stateCreated.selectedCollectionIds.isEmpty()) {
                stateCreated = awaitItem()
            }
            assertEquals(1, stateCreated.selectedCollectionIds.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_prefillPopulatesFields() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            runCurrent()
            awaitItem() // Consume initial state from stateIn

            viewModel.prefill("https://example.com/prefill-test", "Custom Title", "Custom Note")
            runCurrent()
            var state = awaitItem()
            while (state.url.isEmpty()) {
                state = awaitItem()
            }
            assertEquals("https://example.com/prefill-test", state.url)
            assertEquals("Custom Title", state.title)
            assertEquals("Custom Note", state.personalNote)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun saveLinkViewModel_metadataExtractorPopulatesTitleAndImage() = runTest(testDispatcher) {
        val mockExtractor: suspend (String) -> Result<ExtractedMetadata> = { _ ->
            Result.success(
                ExtractedMetadata(
                    title = "Fetched Title",
                    description = "Description",
                    siteName = "Site",
                    faviconUrl = null,
                    previewImageUrl = "https://example.com/og.jpg",
                    contentType = ContentType.ARTICLE
                )
            )
        }
        val viewModel = createViewModel(extractor = mockExtractor)

        viewModel.uiState.test {
            runCurrent()
            awaitItem() // Consume initial state

            viewModel.onUrlChange("https://example.com/article")
            runCurrent()
            advanceUntilIdle()

            var state = awaitItem()
            while (state.metadataStatus != MetadataPreviewStatus.READY) {
                state = awaitItem()
            }
            assertEquals("Fetched Title", state.title)
            assertEquals("https://example.com/og.jpg", state.previewImageUrl)
            assertEquals(MetadataPreviewStatus.READY, state.metadataStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

