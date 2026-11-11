package com.laterbox.app.media.data.adapters.x

import com.laterbox.app.media.domain.adapter.MediaPlatformAdapter
import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XMediaAdapterTest {

    @Test
    fun canHandle_recognizesTwitterAndXStatusUrls() {
        val adapter = XMediaAdapter()

        assertTrue(adapter.canHandle("https://x.com/jack/status/20"))
        assertTrue(adapter.canHandle("https://twitter.com/user/status/1234567890123456789"))
        assertTrue(adapter.canHandle("https://mobile.twitter.com/user/status/1234567890123456789?s=20"))
        assertTrue(adapter.canHandle("https://vxtwitter.com/user/status/1234567890123456789"))
        assertTrue(adapter.canHandle("https://fxtwitter.com/user/status/1234567890123456789"))

        // Non-status URLs
        assertFalse(adapter.canHandle("https://x.com/home"))
        assertFalse(adapter.canHandle("https://twitter.com/explore"))
        assertFalse(adapter.canHandle("https://youtube.com/watch?v=abc"))
        assertFalse(adapter.canHandle("https://instagram.com/p/abc/"))
    }

    @Test
    fun inspect_mapsPhotosAndPropagatesRefererHeader() = runTest {
        val fakeFxClient = object : FxEmbedClient(OkHttpClient()) {
            // Note: in Kotlin we can override or mock, but since getTweet is non-final we can subclass:
        }

        // Create adapter with custom client returning photo tweet
        val photoTweet = FxTweet(
            id = "123456",
            url = "https://x.com/photographer/status/123456",
            text = "Sunset at the beach",
            authorName = "Alice",
            authorScreenName = "alice",
            authorAvatarUrl = "https://pbs.twimg.com/avatar.jpg",
            photos = listOf(
                FxPhoto(url = "https://pbs.twimg.com/media/photo1.jpg", width = 1920, height = 1080),
                FxPhoto(url = "https://pbs.twimg.com/media/photo2.jpg", width = 1080, height = 1080)
            ),
            videos = emptyList()
        )

        val customClient = object : CustomFxClient(FxResult.Success(photoTweet)) {}
        val adapter = XMediaAdapter(fxClient = customClient)

        val result = adapter.inspect("https://x.com/alice/status/123456")
        assertTrue(result is InspectionResult.Available)

        val available = result as InspectionResult.Available
        assertEquals("X", available.platform)
        assertEquals("Sunset at the beach", available.title)
        assertEquals(2, available.candidates.size)

        val c1 = available.candidates[0]
        assertEquals(MediaType.IMAGE, c1.mediaType)
        assertFalse(c1.isThumbnail)
        assertEquals(1920, c1.width)
        assertEquals(1080, c1.height)

        val src1 = c1.source as AcquisitionSource.DirectHttp
        assertEquals("https://pbs.twimg.com/media/photo1.jpg", src1.url)
        assertEquals("https://x.com/", src1.headers["Referer"])
    }

    @Test
    fun inspect_mapsVideosWithMultipleResolutions() = runTest {
        val videoTweet = FxTweet(
            id = "654321",
            url = "https://x.com/filmmaker/status/654321",
            text = "Short film teaser",
            authorName = "Bob",
            authorScreenName = "bob",
            authorAvatarUrl = "https://pbs.twimg.com/avatar.jpg",
            photos = emptyList(),
            videos = listOf(
                FxVideo(
                    url = "https://video.twimg.com/vid_720.mp4",
                    thumbnailUrl = "https://pbs.twimg.com/vid_thumb.jpg",
                    width = 1280,
                    height = 720,
                    duration = 30.5,
                    format = "video/mp4",
                    formats = listOf(
                        FxVideoFormat("https://video.twimg.com/vid_1080.mp4", 1920, 1080, 4500000L),
                        FxVideoFormat("https://video.twimg.com/vid_720.mp4", 1280, 720, 2200000L),
                        FxVideoFormat("https://video.twimg.com/vid_480.mp4", 854, 480, 1000000L)
                    )
                )
            )
        )

        val customClient = object : CustomFxClient(FxResult.Success(videoTweet)) {}
        val adapter = XMediaAdapter(fxClient = customClient)

        val result = adapter.inspect("https://x.com/bob/status/654321")
        assertTrue(result is InspectionResult.Available)

        val available = result as InspectionResult.Available
        val videos = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertEquals(3, videos.size)

        // Best resolution (1080p)
        assertTrue(videos[0].isBest)
        assertEquals(1080, videos[0].height)
        assertTrue(videos[0].formatLabel.contains("1080p"))

        // Posterior video thumbnail candidate
        val thumb = available.candidates.first { it.isThumbnail }
        assertEquals(MediaType.IMAGE, thumb.mediaType)
        val thumbSrc = thumb.source as AcquisitionSource.DirectHttp
        assertEquals("https://pbs.twimg.com/vid_thumb.jpg", thumbSrc.url)
    }

    @Test
    fun inspect_handlesPrivateContentAsNeedsAuth() = runTest {
        val customClient = object : CustomFxClient(FxResult.PrivateContent("Post is protected.")) {}
        val adapter = XMediaAdapter(fxClient = customClient)

        val result = adapter.inspect("https://x.com/private_user/status/999")
        assertTrue(result is InspectionResult.NeedsAuth)
        assertEquals("Post is protected.", (result as InspectionResult.NeedsAuth).reason)
    }

    @Test
    fun inspect_fallsBackToYtDlpWhenFxEmbedFails() = runTest {
        val customClient = object : CustomFxClient(FxResult.Error("API timeout")) {}
        var fallbackCalled = false
        val fakeYtDlp = object : MediaPlatformAdapter {
            override val id = "ytdlp"
            override fun canHandle(url: String) = true
            override suspend fun inspect(url: String): InspectionResult {
                fallbackCalled = true
                return InspectionResult.Available(
                    platform = "X",
                    title = "Fallback Video",
                    thumbnailUrl = null,
                    candidates = emptyList()
                )
            }
        }

        val adapter = XMediaAdapter(fxClient = customClient, fallbackYtDlpAdapter = fakeYtDlp)
        val result = adapter.inspect("https://x.com/user/status/777")
        assertTrue(fallbackCalled)
        assertTrue(result is InspectionResult.Available)
    }

    private open class CustomFxClient(private val resultToReturn: FxResult) : FxEmbedClient(OkHttpClient()) {
        override suspend fun getTweet(statusId: String): FxResult = resultToReturn
    }
}
