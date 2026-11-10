package com.laterbox.app.media.data.adapters.spotify

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.Proxy

@RunWith(RobolectricTestRunner::class)
class SpotifyMetadataProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var testClient: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        testClient = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchTrackMetadata_parsesEmbedTier1Successfully() = runTest {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Never Gonna Give You Up" />
                <meta property="og:description" content="Rick Astley · Whenever You Need Somebody · Song · 1987" />
            </head>
            <body>
                <script id="__NEXT_DATA__" type="application/json">
                {
                    "props": {
                        "pageProps": {
                            "state": {
                                "data": {
                                    "entity": {
                                        "title": "Never Gonna Give You Up",
                                        "artists": [
                                            { "name": "Rick Astley" }
                                        ],
                                        "duration": 213573,
                                        "isExplicit": false,
                                        "releaseDate": { "isoString": "1987-11-12T00:00:00Z" },
                                        "audioPreview": { "url": "https://p.scdn.co/mp3-preview/test.mp3" },
                                        "visualIdentity": {
                                            "image": [
                                                { "url": "https://image.small.jpg", "maxWidth": 300 },
                                                { "url": "https://image.large.jpg", "maxWidth": 640 }
                                            ]
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                </script>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(sampleHtml))

        val baseUrl = server.url("").toString().removeSuffix("/")
        val provider = SpotifyMetadataProviderImpl(
            client = testClient,
            baseUrl = baseUrl
        )
        val result = provider.fetchTrackMetadata("4cOdK2wGLETKBW3PvgPWqT")

        assertTrue(result.isSuccess)
        val meta = result.getOrThrow()
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", meta.trackId)
        assertEquals("Never Gonna Give You Up", meta.title)
        assertEquals("Rick Astley", meta.primaryArtist)
        assertTrue(meta.additionalArtists.isEmpty())
        assertEquals("Whenever You Need Somebody", meta.albumName)
        assertEquals("https://image.large.jpg", meta.artworkUrl)
        assertEquals(213573L, meta.durationMs)
        assertEquals("1987-11-12", meta.releaseDate)
        assertFalse(meta.isExplicit)
        assertEquals("https://p.scdn.co/mp3-preview/test.mp3", meta.audioPreviewUrl)
        assertEquals("3:33", meta.durationFormatted)
        assertEquals("Rick Astley Never Gonna Give You Up", meta.searchQuery)
    }

    @Test
    fun fetchTrackMetadata_multiArtistTrack_extractsPrimaryAndAdditionalArtists() = runTest {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:description" content="The Kid LAROI, Justin Bieber · STAY · Song · 2021" />
            </head>
            <body>
                <script id="__NEXT_DATA__" type="application/json">
                {
                    "props": {
                        "pageProps": {
                            "state": {
                                "data": {
                                    "entity": {
                                        "title": "STAY (with Justin Bieber)",
                                        "artists": [
                                            { "name": "The Kid LAROI" },
                                            { "name": "Justin Bieber" }
                                        ],
                                        "duration": 141805,
                                        "isExplicit": true,
                                        "releaseDate": { "isoString": "2021-07-09T00:00:00Z" }
                                    }
                                }
                            }
                        }
                    }
                }
                </script>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(sampleHtml))

        val baseUrl = server.url("").toString().removeSuffix("/")
        val provider = SpotifyMetadataProviderImpl(
            client = testClient,
            baseUrl = baseUrl
        )
        val result = provider.fetchTrackMetadata("5HCyWlXZPP0y6Gqq8TgA20")

        assertTrue(result.isSuccess)
        val meta = result.getOrThrow()
        assertEquals("STAY (with Justin Bieber)", meta.title)
        assertEquals("The Kid LAROI", meta.primaryArtist)
        assertEquals(listOf("Justin Bieber"), meta.additionalArtists)
        assertEquals("The Kid LAROI, Justin Bieber", meta.allArtistsFormatted)
        assertTrue(meta.isExplicit)
        assertEquals("2:21", meta.durationFormatted)
    }

    @Test
    fun fetchTrackMetadata_tier1Fails_fallsBackToTier2OEmbed() = runTest {
        val oembedJson = """
            {
                "title": "Blinding Lights",
                "thumbnail_url": "https://image.thumb.jpg"
            }
        """.trimIndent()

        // 1st request (embed): 500 Server Error
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        // 2nd request (oembed): 200 OK
        server.enqueue(MockResponse().setResponseCode(200).setBody(oembedJson))

        val baseUrl = server.url("").toString().removeSuffix("/")
        val provider = SpotifyMetadataProviderImpl(
            client = testClient,
            baseUrl = baseUrl
        )
        val result = provider.fetchTrackMetadata("0VjIjW4GlUZAMYd2vXMi3b")

        assertTrue(result.isSuccess)
        val meta = result.getOrThrow()
        assertEquals("Blinding Lights", meta.title)
        assertEquals("https://image.thumb.jpg", meta.artworkUrl)
        assertNull(meta.durationMs)
    }

    @Test
    fun fetchTrackMetadata_blankTrackId_failsFast() = runTest {
        val provider = SpotifyMetadataProviderImpl()
        val result = provider.fetchTrackMetadata("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
