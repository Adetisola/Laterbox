package com.laterbox.app.media.data.adapters.ytdlp

import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YtDlpOutputParserTest {

    @Test
    fun parse_extractsBestVideo_saverVideo_andAudioCandidates_whenFormatsAvailable() {
        val json = """
            {
              "id": "sample_video_1",
              "title": "A Great Architecture Video",
              "extractor_key": "Youtube",
              "thumbnail": "https://example.com/thumb.jpg",
              "duration": 125.5,
              "formats": [
                {
                  "format_id": "137",
                  "ext": "mp4",
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "width": 1920,
                  "height": 1080,
                  "filesize": 50000000,
                  "tbr": 4500.0
                },
                {
                  "format_id": "135",
                  "ext": "mp4",
                  "vcodec": "avc1.4d401f",
                  "acodec": "none",
                  "width": 854,
                  "height": 480,
                  "filesize": 15000000,
                  "tbr": 1200.0
                },
                {
                  "format_id": "140",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2",
                  "filesize": 5000000,
                  "tbr": 128.0
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://youtube.com/watch?v=sample_video_1", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals("Youtube", available.platform)
        assertEquals("A Great Architecture Video", available.title)
        assertEquals("https://example.com/thumb.jpg", available.thumbnailUrl)

        val candidates = available.candidates
        assertEquals(3, candidates.size)

        // Best Video
        val bestVideo = candidates.first { it.candidateId.contains("best_video") }
        assertEquals(MediaType.VIDEO, bestVideo.mediaType)
        assertEquals(1080, bestVideo.height)
        assertEquals(1920, bestVideo.width)
        assertEquals(125500L, bestVideo.durationMs)
        assertEquals(55000000L, bestVideo.estimatedBytes) // 50MB video + 5MB audio
        assertTrue(bestVideo.formatLabel.contains("1080p"))

        // Data Saver Video
        val saverVideo = candidates.first { it.candidateId.contains("saver_video") }
        assertEquals(MediaType.VIDEO, saverVideo.mediaType)
        assertEquals(480, saverVideo.height)
        assertEquals(20000000L, saverVideo.estimatedBytes) // 15MB video + 5MB audio
        assertTrue(saverVideo.formatLabel.contains("480p"))

        // Audio
        val audio = candidates.first { it.candidateId.contains("audio") }
        assertEquals(MediaType.AUDIO, audio.mediaType)
        assertEquals(5000000L, audio.estimatedBytes)
        assertTrue(audio.formatLabel.contains("Audio"))
    }

    @Test
    fun parse_doesNotProduceSaverVideo_whenHighestResIs480pOrLower() {
        val json = """
            {
              "id": "low_res_1",
              "title": "Old Archive Video",
              "extractor_key": "ArchiveOrg",
              "duration": 60.0,
              "formats": [
                {
                  "format_id": "sd",
                  "ext": "mp4",
                  "vcodec": "avc1",
                  "acodec": "none",
                  "width": 640,
                  "height": 360,
                  "filesize": 10000000
                },
                {
                  "format_id": "audio",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "aac",
                  "filesize": 2000000
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://archive.org/details/low_res_1", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        val videoCandidates = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertEquals(1, videoCandidates.size)
        assertEquals(360, videoCandidates[0].height)
    }

    @Test
    fun parse_extractsAudioOnly_whenNoVideoFormats() {
        val json = """
            {
              "id": "track_123",
              "title": "SoundCloud Beat",
              "extractor_key": "Soundcloud",
              "formats": [
                {
                  "format_id": "hls_mp3",
                  "ext": "mp3",
                  "vcodec": "none",
                  "acodec": "mp3",
                  "filesize": 8000000
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://soundcloud.com/user/track_123", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals(1, available.candidates.size)
        assertEquals(MediaType.AUDIO, available.candidates[0].mediaType)
    }

    @Test
    fun parse_bestEffortImageExtraction_whenNoVideoOrAudioFormats() {
        val json = """
            {
              "id": "photo_post_1",
              "title": "Photo post on X",
              "extractor_key": "Twitter",
              "formats": [],
              "thumbnails": [
                { "url": "https://pbs.twimg.com/media/small.jpg", "width": 300, "height": 300 },
                { "url": "https://pbs.twimg.com/media/orig.jpg", "width": 1200, "height": 1200 }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://x.com/user/status/photo_post_1", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals(1, available.candidates.size)
        val imageCandidate = available.candidates[0]
        assertEquals(MediaType.IMAGE, imageCandidate.mediaType)
        assertEquals(1200, imageCandidate.width)
        assertEquals(1200, imageCandidate.height)
    }

    @Test
    fun parse_handlesEmptyFormats_gracefully_returnsUnsupported() {
        val json = """
            {
              "id": "empty_post",
              "title": "Text only status",
              "extractor_key": "Generic",
              "formats": [],
              "thumbnails": []
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://example.com/post", json)
        assertTrue(result is InspectionResult.Unsupported)
    }

    @Test
    fun parseError_mapsPrivateVideo_toPrivateContent() {
        val err = "ERROR: [youtube] 12345: This video is private. Sign in if you've been granted access to this video"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.PRIVATE_CONTENT, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_mapsLoginRequired_toNeedsAuth() {
        val err = "ERROR: [twitter] Authentication required. Please sign in to view this post"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.NeedsAuth)
    }

    @Test
    fun parseError_mapsGeoBlocked_toGeoBlocked() {
        val err = "ERROR: The uploader has not made this video available in your country (georestricted)"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.GEO_BLOCKED, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_mapsRateLimit_toRateLimited() {
        val err = "HTTP Error 429: Too Many Requests. You are being rate limited"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.RATE_LIMITED, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_mapsExtractorBroken_toExtractorBroken() {
        val err = "ERROR: [instagram] Unable to extract post: ExtractorError occurred"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.EXTRACTOR_BROKEN, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_mapsYouTubeChallenge_toYouTubeChallengeError() {
        val err = "ERROR: [youtube] Sign in to confirm you're not a bot. Use --cookies-from-browser or pass credentials."
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.YOUTUBE_CHALLENGE_ERROR, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_mapsSocketTimeout_toConnectionTimeout() {
        val err = "ERROR: [youtube] unable to download video data: The read operation timed out (socket timeout)"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.CONNECTION_TIMEOUT, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_mapsPythonTraceback_toPythonRuntimeError() {
        val err = "Traceback (most recent call last):\n  File \"yt_dlp/__main__.py\", line 19\nModuleNotFoundError: No module named 'certifi'"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.PYTHON_RUNTIME_ERROR, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parse_handlesMultiImageCarousel_extractsAllImages() {
        val json = """
            {
              "id": "carousel_post_1",
              "title": "A 3-photo carousel",
              "extractor_key": "Instagram",
              "_type": "playlist",
              "entries": [
                {
                  "id": "carousel_post_1_1",
                  "title": "Photo 1",
                  "formats": [],
                  "thumbnails": [
                    { "url": "https://instagram.com/p1_small.jpg", "width": 320, "height": 320 },
                    { "url": "https://instagram.com/p1_large.jpg", "width": 1080, "height": 1080 }
                  ]
                },
                {
                  "id": "carousel_post_1_2",
                  "title": "Photo 2",
                  "formats": [],
                  "thumbnails": [
                    { "url": "https://instagram.com/p2_large.jpg", "width": 1080, "height": 1080 }
                  ]
                },
                {
                  "id": "carousel_post_1_3",
                  "title": "Photo 3",
                  "url": "https://instagram.com/p3_direct.jpg",
                  "formats": []
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://instagram.com/p/carousel_post_1", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        val imageCandidates = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertEquals(3, imageCandidates.size)
        assertEquals(1080, imageCandidates[0].width)
        assertEquals(1080, imageCandidates[1].width)
        assertTrue(imageCandidates[0].formatLabel.contains("Image 1"))
        assertTrue(imageCandidates[1].formatLabel.contains("Image 2"))
        assertTrue(imageCandidates[2].formatLabel.contains("Image 3"))
    }

    @Test
    fun parse_handlesMixedCarousel_extractsBothVideoAndImages() {
        val json = """
            {
              "id": "mixed_post",
              "title": "Post with Video and Photo",
              "extractor_key": "Instagram",
              "_type": "playlist",
              "entries": [
                {
                  "id": "entry_vid",
                  "title": "Video clip",
                  "duration": 15.0,
                  "formats": [
                    {
                      "format_id": "v1",
                      "ext": "mp4",
                      "vcodec": "avc1",
                      "acodec": "aac",
                      "width": 1080,
                      "height": 1920,
                      "filesize": 12000000
                    }
                  ]
                },
                {
                  "id": "entry_pic",
                  "title": "Photo slide",
                  "formats": [],
                  "thumbnails": [
                    { "url": "https://instagram.com/slide2.jpg", "width": 1080, "height": 1080 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://instagram.com/p/mixed_post", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        val videos = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        val images = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertTrue("Should extract video candidates (best + saver)", videos.isNotEmpty())
        assertEquals(1, images.size)
        assertEquals(1920, videos[0].height)
        assertEquals(1080, images[0].width)
    }

    @Test
    fun parse_doesNotMistakeVideoThumbnailForOriginalImage() {
        val json = """
            {
              "id": "single_video",
              "title": "Just a Video",
              "extractor_key": "Youtube",
              "thumbnail": "https://i.ytimg.com/vi/single_video/hqdefault.jpg",
              "thumbnails": [
                { "url": "https://i.ytimg.com/vi/single_video/maxresdefault.jpg", "width": 1280, "height": 720 }
              ],
              "formats": [
                {
                  "format_id": "22",
                  "ext": "mp4",
                  "vcodec": "avc1",
                  "acodec": "aac",
                  "width": 1280,
                  "height": 720,
                  "filesize": 25000000
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://youtube.com/watch?v=single_video", json)

        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        // Video thumbnail must NOT be included as an image candidate
        val imageCandidates = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertTrue(imageCandidates.isEmpty())
        val videoCandidates = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertTrue("Should extract video candidates", videoCandidates.isNotEmpty())
    }

    @Test
    fun attachPreviewImageFallback_addsOgFallbackCandidate_whenInspectionFailed() {
        val failed = InspectionResult.Failed(
            code = FailureCode.EXTRACTOR_BROKEN,
            message = "Unable to extract post",
            retryable = false
        )
        val ogUrl = "https://preview.linkedin.com/feed/og_image.jpg"
        val attached = YtDlpOutputParser.attachPreviewImageFallback(failed, ogUrl)

        assertTrue(attached is InspectionResult.Available)
        val available = attached as InspectionResult.Available
        assertEquals(1, available.candidates.size)
        val candidate = available.candidates[0]
        assertEquals(MediaType.IMAGE, candidate.mediaType)
        assertTrue(candidate.isOgFallback)
        assertEquals("Preview image (Open Graph fallback)", candidate.formatLabel)
    }

    @Test
    fun parseError_mapsLinkedInExtractionError_toExtractorBroken() {
        val err = "ERROR: [LinkedIn] Unable to extract video: No valid video manifest found"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.EXTRACTOR_BROKEN, (result as InspectionResult.Failed).code)
    }
}
