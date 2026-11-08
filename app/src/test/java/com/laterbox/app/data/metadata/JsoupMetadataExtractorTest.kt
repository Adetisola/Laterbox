package com.laterbox.app.data.metadata

import com.laterbox.app.domain.model.ContentType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsoupMetadataExtractorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun extractsOpenGraphMetadataAccurately() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Fallback Title</title>
                <meta property="og:title" content="OpenGraph Article Title" />
                <meta property="og:description" content="This is an informative article about Android development." />
                <meta property="og:site_name" content="Laterbox Engineering" />
                <meta property="og:image" content="/images/og-preview.png" />
                <meta property="og:type" content="article" />
                <link rel="icon" href="/favicon.png" />
            </head>
            <body>
                <p>Hello world</p>
            </body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(html))
        val baseUrl = server.url("/articles/sample").toString()

        val result = JsoupMetadataExtractor.extract(baseUrl)
        assertTrue(result.isSuccess)

        val metadata = result.getOrThrow()
        assertEquals("OpenGraph Article Title", metadata.title)
        assertEquals("This is an informative article about Android development.", metadata.description)
        assertEquals("Laterbox Engineering", metadata.siteName)
        assertEquals("${server.url("/")}images/og-preview.png", metadata.previewImageUrl)
        assertEquals("${server.url("/")}favicon.png", metadata.faviconUrl)
        assertEquals(ContentType.ARTICLE, metadata.contentType)
    }

    @Test
    fun extractsTwitterCardWhenOpenGraphAbsent() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="twitter:title" content="Twitter Card Title" />
                <meta name="twitter:description" content="Twitter card description." />
                <meta name="twitter:image" content="https://images.example.com/card.jpg" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(html))
        val url = server.url("/post").toString()

        val result = JsoupMetadataExtractor.extract(url)
        assertTrue(result.isSuccess)

        val metadata = result.getOrThrow()
        assertEquals("Twitter Card Title", metadata.title)
        assertEquals("Twitter card description.", metadata.description)
        assertEquals("https://images.example.com/card.jpg", metadata.previewImageUrl)
    }

    @Test
    fun classifiesDomainsCorrectly() {
        val emptyDoc = Jsoup.parse("<html><head></head><body></body></html>")

        val ytMeta = JsoupMetadataExtractor.parseDocument(emptyDoc, "https://www.youtube.com/watch?v=123")
        assertEquals(ContentType.VIDEO, ytMeta.contentType)

        val redditMeta = JsoupMetadataExtractor.parseDocument(emptyDoc, "https://www.reddit.com/r/androiddev/comments/abc")
        assertEquals(ContentType.DISCUSSION, redditMeta.contentType)

        val hnMeta = JsoupMetadataExtractor.parseDocument(emptyDoc, "https://news.ycombinator.com/item?id=123")
        assertEquals(ContentType.DISCUSSION, hnMeta.contentType)

        val amazonMeta = JsoupMetadataExtractor.parseDocument(emptyDoc, "https://www.amazon.com/dp/B08N5WRWNW")
        assertEquals(ContentType.PRODUCT, amazonMeta.contentType)

        val siteMeta = JsoupMetadataExtractor.parseDocument(emptyDoc, "https://example.com/about")
        assertEquals(ContentType.WEBSITE, siteMeta.contentType)
    }

    @Test
    fun handlesHttpErrorGracefully() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val url = server.url("/not-found").toString()

        val result = JsoupMetadataExtractor.extract(url)
        assertTrue(result.isFailure)
    }

    @Test
    fun extractsJsonLdImageAndUnescapesEntities() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Timi &amp; Friends &#x2022; Profile</title>
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@type": "Article",
                    "image": "https://example.com/jsonld-hero.jpg"
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(html))
        val url = server.url("/profile").toString()

        val result = JsoupMetadataExtractor.extract(url)
        assertTrue(result.isSuccess)

        val metadata = result.getOrThrow()
        assertEquals("Timi & Friends • Profile", metadata.title)
        assertEquals("https://example.com/jsonld-hero.jpg", metadata.previewImageUrl)
    }
}
