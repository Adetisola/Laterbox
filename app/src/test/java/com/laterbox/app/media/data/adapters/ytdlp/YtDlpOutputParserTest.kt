package com.laterbox.app.media.data.adapters.ytdlp

import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // Best Video (1080p)
        val bestVideo = candidates.first { it.height == 1080 }
        assertEquals(MediaType.VIDEO, bestVideo.mediaType)
        assertEquals(1080, bestVideo.height)
        assertEquals(1920, bestVideo.width)
        assertEquals(125500L, bestVideo.durationMs)
        assertEquals(55000000L, bestVideo.estimatedBytes) // 50MB video + 5MB audio
        assertTrue(bestVideo.formatLabel.contains("1080p"))
        assertTrue(bestVideo.isBest)
        assertTrue(bestVideo.requiresMerge)

        // Lower Video (480p)
        val saverVideo = candidates.first { it.height == 480 }
        assertEquals(MediaType.VIDEO, saverVideo.mediaType)
        assertEquals(480, saverVideo.height)
        assertEquals(20000000L, saverVideo.estimatedBytes) // 15MB video + 5MB audio
        assertTrue(saverVideo.formatLabel.contains("480p"))
        assertFalse(saverVideo.isBest)

        // Audio
        val audio = candidates.first { it.mediaType == MediaType.AUDIO }
        assertEquals(5000000L, audio.estimatedBytes)
        assertTrue(audio.formatLabel.contains("Audio"))

        // Thumbnail candidate independently extracted
        val thumb = candidates.first { it.mediaType == MediaType.IMAGE }
        assertTrue(thumb.isThumbnail)
        assertTrue(thumb.formatLabel.contains("Thumbnail"))
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
        val imageCandidates = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertTrue(imageCandidates.isNotEmpty())
        val best = imageCandidates.first { it.isBest }
        assertEquals(1200, best.width)
        assertEquals(1200, best.height)
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
        assertTrue("Should extract images from each slide", imageCandidates.size >= 3)
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
        assertTrue("Should extract video candidates", videos.isNotEmpty())
        assertTrue("Should extract image candidates", images.isNotEmpty())
        assertEquals(1920, videos[0].height)
    }

    @Test
    fun parse_extractsVideoThumbnailsAsIndependentImageCandidates_whenVideoFormatsExist() {
        val json = """
            {
              "id": "single_video",
              "title": "Just a Video",
              "extractor_key": "Youtube",
              "thumbnail": "https://i.ytimg.com/vi/single_video/hqdefault.jpg",
              "thumbnails": [
                { "url": "https://i.ytimg.com/vi/single_video/maxresdefault.jpg", "width": 1280, "height": 720 },
                { "url": "https://i.ytimg.com/vi/single_video/hqdefault.jpg", "width": 480, "height": 360 }
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

        // Video thumbnail MUST be included as an independent downloadable image candidate
        val imageCandidates = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertFalse("Thumbnails must be extracted even when video formats exist", imageCandidates.isEmpty())

        val bestThumb = imageCandidates.first { it.isBest }
        assertEquals(1280, bestThumb.width)
        assertEquals(720, bestThumb.height)
        assertTrue(bestThumb.isThumbnail)
        assertTrue(bestThumb.formatLabel.contains("Thumbnail — 1280 × 720 (Best)"))

        val videoCandidates = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertTrue("Should also extract video candidate", videoCandidates.isNotEmpty())
    }

    @Test
    fun parse_filtersOutStoryboardSpriteSheets() {
        val json = """
            {
              "id": "video_with_sb",
              "title": "Video with storyboard",
              "extractor_key": "Youtube",
              "thumbnails": [
                { "id": "storyboard_spec_0", "url": "https://i.ytimg.com/sb/tiles_0.jpg", "preference": -25, "width": 48, "height": 27 },
                { "id": "sb_preview", "url": "https://i.ytimg.com/sb/tiles_1.jpg", "preference": -20, "width": 160, "height": 90 },
                { "id": "maxres", "url": "https://i.ytimg.com/vi/video_with_sb/maxresdefault.jpg", "preference": 0, "width": 1920, "height": 1080 }
              ],
              "formats": [
                { "format_id": "18", "ext": "mp4", "vcodec": "avc1", "acodec": "aac", "width": 640, "height": 360 }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://youtube.com/watch?v=video_with_sb", json)
        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available

        val imageCandidates = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertEquals(1, imageCandidates.size)
        assertEquals("https://i.ytimg.com/vi/video_with_sb/maxresdefault.jpg", (imageCandidates[0].source as com.laterbox.app.media.domain.model.AcquisitionSource.DirectHttp).url)
        assertEquals(1920, imageCandidates[0].width)
    }

    @Test
    fun parse_discoversAllRealResolutionsDynamically() {
        val json = """
            {
              "id": "multi_res_vid",
              "title": "4K Video with Many Formats",
              "extractor_key": "Youtube",
              "formats": [
                { "format_id": "f_2160", "ext": "mp4", "vcodec": "vp9", "acodec": "none", "width": 3840, "height": 2160, "filesize": 200000000 },
                { "format_id": "f_1440", "ext": "mp4", "vcodec": "vp9", "acodec": "none", "width": 2560, "height": 1440, "filesize": 100000000 },
                { "format_id": "f_1080", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "width": 1920, "height": 1080, "filesize": 50000000 },
                { "format_id": "f_720", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "width": 1280, "height": 720, "filesize": 25000000 },
                { "format_id": "f_480", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "width": 854, "height": 480, "filesize": 12000000 },
                { "format_id": "f_360", "ext": "mp4", "vcodec": "avc1", "acodec": "none", "width": 640, "height": 360, "filesize": 6000000 },
                { "format_id": "audio_1", "ext": "m4a", "vcodec": "none", "acodec": "aac", "filesize": 5000000 }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://youtube.com/watch?v=multi_res_vid", json)
        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available

        val videoCandidates = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertEquals(6, videoCandidates.size)

        // Descending order of height
        assertEquals(2160, videoCandidates[0].height)
        assertTrue(videoCandidates[0].isBest)
        assertTrue(videoCandidates[0].formatLabel.contains("2160p"))

        assertEquals(1440, videoCandidates[1].height)
        assertFalse(videoCandidates[1].isBest)

        assertEquals(1080, videoCandidates[2].height)
        assertEquals(720, videoCandidates[3].height)
        assertEquals(480, videoCandidates[4].height)
        assertEquals(360, videoCandidates[5].height)
    }

    @Test
    fun parse_multiItemPost_preservesValidEntriesWhenOneEntryFails() {
        val json = """
            {
              "id": "playlist_with_error",
              "title": "Three items with one malformed",
              "extractor_key": "Instagram",
              "entries": [
                {
                  "id": "item_1",
                  "title": "Valid Photo",
                  "formats": [],
                  "thumbnails": [{ "url": "https://instagram.com/valid1.jpg", "width": 1080, "height": 1080 }]
                },
                null,
                {
                  "id": "item_3",
                  "title": "Valid Photo 2",
                  "formats": [],
                  "thumbnails": [{ "url": "https://instagram.com/valid3.jpg", "width": 1080, "height": 1080 }]
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://instagram.com/p/playlist_with_error", json)
        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available

        val images = available.candidates.filter { it.mediaType == MediaType.IMAGE }
        assertEquals(2, images.size)
        assertEquals(1, available.partialFailures.size)
        assertTrue(available.partialFailures[0].contains("Entry 2"))
    }

    @Test
    fun parse_flagsMissingAudioTrack_whenVideoHasNoSound() {
        val json = """
            {
              "id": "silent_gif_mp4",
              "title": "Silent Animation",
              "extractor_key": "Twitter",
              "formats": [
                {
                  "format_id": "vid_silent",
                  "ext": "mp4",
                  "vcodec": "avc1",
                  "acodec": "none",
                  "width": 640,
                  "height": 640,
                  "filesize": 1500000
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://x.com/user/status/silent_gif_mp4", json)
        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available

        val videos = available.candidates.filter { it.mediaType == MediaType.VIDEO }
        assertEquals(1, videos.size)
        val video = videos[0]
        assertTrue(video.audioTrackMissing)
        assertFalse(video.requiresMerge)
        assertTrue(video.formatLabel.contains("(No Audio)"))
        assertEquals("vid_silent", (video.source as com.laterbox.app.media.domain.model.AcquisitionSource.YtDlp).formatSelector)
    }

    @Test
    fun attachPreviewImageFallback_preservesFailedResult_andDoesNotConvertToAvailable() {
        val failed = InspectionResult.Failed(
            code = FailureCode.EXTRACTOR_BROKEN,
            message = "Unable to extract post",
            retryable = false
        )
        val ogUrl = "https://preview.linkedin.com/feed/og_image.jpg"
        val attached = YtDlpOutputParser.attachPreviewImageFallback(failed, ogUrl)

        assertTrue(attached is InspectionResult.Failed)
        val resultFailed = attached as InspectionResult.Failed
        assertEquals(FailureCode.EXTRACTOR_BROKEN, resultFailed.code)
        assertEquals("Unable to extract post", resultFailed.message)
    }

    @Test
    fun parse_safeVideoFormatSelector_neverFallsBackToAudioOnly() {
        val json = """
            {
              "id": "safe_sel_vid",
              "title": "Safe Selector Test",
              "extractor_key": "Youtube",
              "formats": [
                {
                  "format_id": "137",
                  "ext": "mp4",
                  "vcodec": "avc1.640028",
                  "acodec": "none",
                  "width": 1920,
                  "height": 1080
                },
                {
                  "format_id": "140",
                  "ext": "m4a",
                  "vcodec": "none",
                  "acodec": "mp4a.40.2"
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://youtube.com/watch?v=safe_sel_vid", json)
        assertTrue(result is InspectionResult.Available)
        val video = (result as InspectionResult.Available).candidates.first { it.mediaType == MediaType.VIDEO }
        val source = video.source as com.laterbox.app.media.domain.model.AcquisitionSource.YtDlp

        // Format selector must never contain audio-only fallback "bestaudio/best"
        assertFalse(source.formatSelector.contains("/bestaudio/best"))
        assertEquals("137+bestaudio[ext=m4a]/137+bestaudio/137", source.formatSelector)
    }

    @Test
    fun parse_instagramPhotoPost_createsPhotoCandidateInsteadOfThumbnail() {
        val json = """
            {
              "id": "insta_photo_1",
              "title": "Instagram Photo Post",
              "extractor_key": "Instagram",
              "thumbnails": [
                {
                  "id": "0",
                  "url": "https://instagram.com/p/pic_1080.jpg",
                  "width": 1080,
                  "height": 1350
                }
              ],
              "formats": []
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://instagram.com/p/insta_photo_1", json)
        assertTrue(result is InspectionResult.Available)
        val candidates = (result as InspectionResult.Available).candidates
        val photo = candidates.first { it.mediaType == MediaType.IMAGE }
        assertFalse("Photo post candidate must not be marked as a video thumbnail", photo.isThumbnail)
        assertTrue(photo.formatLabel.startsWith("Photo"))
        assertEquals(1080, photo.width)
        assertEquals(1350, photo.height)
    }

    @Test
    fun parseError_mapsLinkedInExtractionError_toExtractorBroken() {
        val err = "ERROR: [LinkedIn] Unable to extract video: No valid video manifest found"
        val result = YtDlpOutputParser.parseError(err)

        assertTrue(result is InspectionResult.Failed)
        assertEquals(FailureCode.EXTRACTOR_BROKEN, (result as InspectionResult.Failed).code)
    }

    @Test
    fun parseError_errno7AndDnsFailures_mappedToNetworkError() {
        val err1 = "ERROR: [youtube] 12345: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>"
        val res1 = YtDlpOutputParser.parseError(err1)
        assertTrue(res1 is InspectionResult.Failed)
        assertEquals(FailureCode.NETWORK_ERROR, (res1 as InspectionResult.Failed).code)
        assertTrue((res1 as InspectionResult.Failed).retryable)

        val err2 = "ERROR: Unable to download API page: <urlopen error [Errno -3] Temporary failure in name resolution>"
        val res2 = YtDlpOutputParser.parseError(err2)
        assertTrue(res2 is InspectionResult.Failed)
        assertEquals(FailureCode.NETWORK_ERROR, (res2 as InspectionResult.Failed).code)
        assertTrue((res2 as InspectionResult.Failed).retryable)

        val err3 = "socket.gaierror: [Errno 7] No address associated with hostname"
        val res3 = YtDlpOutputParser.parseError(err3)
        assertTrue(res3 is InspectionResult.Failed)
        assertEquals(FailureCode.NETWORK_ERROR, (res3 as InspectionResult.Failed).code)
        assertTrue((res3 as InspectionResult.Failed).retryable)
    }

    @Test
    fun attachPreviewImageFallback_doesNotConvertFailureToAvailable() {
        val failed = InspectionResult.Failed(FailureCode.NETWORK_ERROR, "Network down", retryable = true)
        val previewUrl = "https://example.com/og_image.jpg"
        val res = YtDlpOutputParser.attachPreviewImageFallback(failed, previewUrl)

        assertTrue("Failure must remain Failed and not converted to Available", res is InspectionResult.Failed)
        val failedRes = res as InspectionResult.Failed
        assertEquals(FailureCode.NETWORK_ERROR, failedRes.code)
        assertNotNull(failedRes.previewCandidate)
        assertEquals("Preview image only", failedRes.previewCandidate?.formatLabel)
        assertEquals(previewUrl, (failedRes.previewCandidate?.source as com.laterbox.app.media.domain.model.AcquisitionSource.DirectHttp).url)

        val unsupported = InspectionResult.Unsupported("No formats")
        val resUnsupported = YtDlpOutputParser.attachPreviewImageFallback(unsupported, previewUrl)
        assertTrue("Unsupported must remain Unsupported", resUnsupported is InspectionResult.Unsupported)
        assertNotNull((resUnsupported as InspectionResult.Unsupported).previewCandidate)
    }

    @Test
    fun mergeResults_combinesCandidates_deduplicatesByFormatId_andSetsIsComplete() {
        val candidate1 = com.laterbox.app.media.domain.model.MediaCandidate(
            candidateId = "c1",
            mediaType = MediaType.VIDEO,
            formatLabel = "720p",
            estimatedBytes = 1000000L,
            durationMs = 60000L,
            width = 1280,
            height = 720,
            source = com.laterbox.app.media.domain.model.AcquisitionSource.DirectHttp("https://vid1.mp4"),
            formatId = "format_720"
        )
        val candidate2 = com.laterbox.app.media.domain.model.MediaCandidate(
            candidateId = "c2",
            mediaType = MediaType.VIDEO,
            formatLabel = "1080p",
            estimatedBytes = 2000000L,
            durationMs = 60000L,
            width = 1920,
            height = 1080,
            source = com.laterbox.app.media.domain.model.AcquisitionSource.DirectHttp("https://vid2.mp4"),
            formatId = "format_1080"
        )
        val duplicateCandidate1 = candidate1.copy(candidateId = "c1_dup", formatLabel = "720p Duplicate")

        val stage1 = InspectionResult.Available(
            platform = "YouTube",
            title = "Fast Stage Title",
            candidates = listOf(candidate1),
            isComplete = false
        )
        val stage2 = InspectionResult.Available(
            platform = "YouTube",
            title = "Complete Stage Title",
            candidates = listOf(duplicateCandidate1, candidate2),
            isComplete = false
        )

        val merged = YtDlpOutputParser.mergeResults(stage1, stage2)
        assertTrue(merged.isComplete)
        assertEquals(2, merged.candidates.size)
        assertEquals("Fast Stage Title", merged.title)
        assertTrue(merged.candidates.any { it.formatId == "format_720" })
        assertTrue(merged.candidates.any { it.formatId == "format_1080" })
    }

    @Test
    fun parse_literalJsonNull_returnsRetryableNetworkError() {
        val result = YtDlpOutputParser.parse("https://www.tiktok.com/@user/video/123456", "null")
        assertTrue(result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.NETWORK_ERROR, failed.code)
        assertTrue("Null extraction result should be retryable", failed.retryable)
    }

    @Test
    fun parse_emptyOrBlankOutput_returnsRetryableNetworkError() {
        val resultEmpty = YtDlpOutputParser.parse("https://www.tiktok.com/@user/video/123456", "")
        assertTrue(resultEmpty is InspectionResult.Failed)
        assertEquals(FailureCode.NETWORK_ERROR, (resultEmpty as InspectionResult.Failed).code)
        assertTrue(resultEmpty.retryable)

        val resultBlank = YtDlpOutputParser.parse("https://www.tiktok.com/@user/video/123456", "   \n\t  ")
        assertTrue(resultBlank is InspectionResult.Failed)
        assertEquals(FailureCode.NETWORK_ERROR, (resultBlank as InspectionResult.Failed).code)
        assertTrue(resultBlank.retryable)
    }

    @Test
    fun parse_jsonArrayInsteadOfObject_returnsExtractorBroken() {
        val jsonArray = """[{"id": "item1"}, {"id": "item2"}]"""
        val result = YtDlpOutputParser.parse("https://www.tiktok.com/@user/video/123456", jsonArray)
        assertTrue(result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.EXTRACTOR_BROKEN, failed.code)
        assertFalse("Array output is a structural extractor mismatch, not transient network error", failed.retryable)
    }

    @Test
    fun parse_truncatedJson_returnsRetryableNetworkError() {
        val truncatedJson = """{"id": "tiktok_123", "title": "Sample TikTok", "duration": 15.0, "formats": [{"format_id": "1080""""
        val result = YtDlpOutputParser.parse("https://www.tiktok.com/@user/video/123456", truncatedJson)
        assertTrue(result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.NETWORK_ERROR, failed.code)
        assertTrue("Truncated JSON output from interrupted stream must be retryable", failed.retryable)
    }

    @Test
    fun parse_validTikTokResponse_extractsCandidatesSuccessfully() {
        val validTikTokJson = """
            {
              "id": "7123456789012345678",
              "title": "Inspiring architecture and interior design",
              "extractor_key": "TikTok",
              "thumbnail": "https://p16.tiktokcdn.com/thumb.jpg",
              "duration": 45.0,
              "formats": [
                {
                  "format_id": "download_addr",
                  "ext": "mp4",
                  "vcodec": "h264",
                  "acodec": "aac",
                  "width": 1080,
                  "height": 1920,
                  "filesize": 15000000,
                  "tbr": 2600.0,
                  "url": "https://v16.tiktokcdn.com/video.mp4?signature=secret_token"
                }
              ]
            }
        """.trimIndent()

        val result = YtDlpOutputParser.parse("https://www.tiktok.com/@creator/video/7123456789012345678", validTikTokJson)
        assertTrue(result is InspectionResult.Available)
        val available = result as InspectionResult.Available
        assertEquals("TikTok", available.platform)
        assertEquals("Inspiring architecture and interior design", available.title)
        assertEquals("https://p16.tiktokcdn.com/thumb.jpg", available.thumbnailUrl)
        assertTrue(available.candidates.isNotEmpty())

        val videoCandidate = available.candidates.first { it.mediaType == MediaType.VIDEO }
        assertEquals(1920, videoCandidate.height)
        assertEquals(1080, videoCandidate.width)
    }

    @Test
    fun parse_tiktokRetryableExtractionFailure() {
        val errorText = "ERROR: [TikTok] 7123456789: Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>"
        val result = YtDlpOutputParser.parseError(errorText)
        assertTrue(result is InspectionResult.Failed)
        val failed = result as InspectionResult.Failed
        assertEquals(FailureCode.NETWORK_ERROR, failed.code)
        assertTrue("Transient network error should be retryable", failed.retryable)
    }
}
