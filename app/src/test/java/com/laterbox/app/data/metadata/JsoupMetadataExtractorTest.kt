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

    @Test
    fun mergeMetadataPreservesPrimaryTitleAndAdoptsFallbackImage() {
        val primary = ExtractedMetadata(
            title = "Real Article Title",
            description = "Detailed article summary from primary request.",
            siteName = "Ritter",
            previewImageUrl = null,
            faviconUrl = "https://ritterapp.site/favicon.ico",
            contentType = ContentType.ARTICLE
        )
        val fallback = ExtractedMetadata(
            title = "Just a moment...", // Generic bot-challenge title that returns null from cleanTitle in real parsing, or generic fallback
            description = "Short desc",
            siteName = null,
            previewImageUrl = "https://www.ritterapp.site/og-cover.png",
            faviconUrl = "https://www.ritterapp.site/apple-touch-icon.png",
            contentType = ContentType.WEBSITE
        )

        val merged = JsoupMetadataExtractor.mergeMetadata(primary, fallback)
        assertEquals("Real Article Title", merged.title)
        assertEquals("Detailed article summary from primary request.", merged.description)
        assertEquals("Ritter", merged.siteName)
        assertEquals("https://www.ritterapp.site/og-cover.png", merged.previewImageUrl)
        assertEquals("https://ritterapp.site/favicon.ico", merged.faviconUrl)
        assertEquals(ContentType.ARTICLE, merged.contentType)
    }

    @Test
    fun fallbackFetchEnrichesMissingImageWithoutOverwritingPrimaryTitle() = runTest {
        val htmlWithoutImage = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta property="og:title" content="Primary Valid Title" />
                <meta property="og:description" content="Primary valid description." />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val htmlWithImageAndGenericTitle = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Access Denied</title>
                <meta property="og:image" content="/assets/hero-banner.jpg" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(htmlWithoutImage))
        server.enqueue(MockResponse().setResponseCode(200).setBody(htmlWithImageAndGenericTitle))

        val url = server.url("/story").toString()
        val result = JsoupMetadataExtractor.extract(url)
        assertTrue(result.isSuccess)

        val metadata = result.getOrThrow()
        assertEquals("Primary Valid Title", metadata.title)
        assertEquals("Primary valid description.", metadata.description)
        assertEquals("${server.url("/")}assets/hero-banner.jpg", metadata.previewImageUrl)
    }

    @Test
    fun parsesComplexJsonLdGraphAndImageObjectsAndSurvivesMalformedBlock() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <script type="application/ld+json">
                { invalid json here !!!
                </script>
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@graph": [
                        {
                            "@type": "Organization",
                            "name": "DeepTech Journal",
                            "logo": {
                                "@type": "ImageObject",
                                "url": "https://example.com/logo-small.png",
                                "width": 32,
                                "height": 32
                            }
                        },
                        {
                            "@type": "NewsArticle",
                            "headline": "Structured JSON-LD Headline &amp; Insights",
                            "description": "Extracted from @graph NewsArticle node.",
                            "publisher": {
                                "@type": "Organization",
                                "name": "DeepTech Journal"
                            },
                            "image": [
                                {
                                    "@type": "ImageObject",
                                    "url": "/images/article-hero-1200.jpg",
                                    "width": 1200,
                                    "height": 630
                                }
                            ]
                        }
                    ]
                }
                </script>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com/news/1")
        val metadata = JsoupMetadataExtractor.parseDocument(doc, "https://example.com/news/1")

        assertEquals("Structured JSON-LD Headline & Insights", metadata.title)
        assertEquals("Extracted from @graph NewsArticle node.", metadata.description)
        assertEquals("DeepTech Journal", metadata.siteName)
        assertEquals("https://example.com/images/article-hero-1200.jpg", metadata.previewImageUrl)
    }

    @Test
    fun filtersOutTrackingPixelsAndTinyImagesInFavorOfValidHeroImage() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Article With Tracking Pixels</title>
                <meta property="og:image" content="https://example.com/1x1-pixel.gif" />
                <meta property="og:image:width" content="1" />
                <meta property="og:image:height" content="1" />
                <meta name="twitter:image" content="//cdn.example.com/media/valid-hero.webp" />
                <link rel="apple-touch-icon" href="//cdn.example.com/icons/apple-touch-icon.png" />
            </head>
            <body>
                <article>
                    <img src="/analytics/spacer.gif" width="1" height="1" />
                    <img src="/content/inline-photo.jpg" width="800" height="500" />
                </article>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com/post")
        val metadata = JsoupMetadataExtractor.parseDocument(doc, "https://example.com/post")

        assertEquals("https://cdn.example.com/media/valid-hero.webp", metadata.previewImageUrl)
        assertEquals("https://cdn.example.com/icons/apple-touch-icon.png", metadata.faviconUrl)
    }

    @Test
    fun selectsArticleHeroImageWhenMetaTagsHaveOnlyTinyIcons() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Longform Essay</title>
            </head>
            <body>
                <article>
                    <img src="/icons/avatar-32x32.png" width="32" height="32" />
                    <img data-src="/photos/featured-essay-cover.jpg" width="1024" height="576" />
                </article>
            </body>
            </html>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://essays.example.org/read")
        val metadata = JsoupMetadataExtractor.parseDocument(doc, "https://essays.example.org/read")

        assertEquals("https://essays.example.org/photos/featured-essay-cover.jpg", metadata.previewImageUrl)
    }

    @Test
    fun parsesOEmbedResponsesForYouTubeRedditAndX() {
        val youtubeOEmbed = """
            {
                "title": "Kotlin Coroutines Under the Hood",
                "author_name": "Android Developers",
                "provider_name": "YouTube",
                "thumbnail_url": "https://i.ytimg.com/vi/abc123xyz/hqdefault.jpg",
                "thumbnail_width": 480,
                "thumbnail_height": 360
            }
        """.trimIndent()

        val ytMeta = JsoupMetadataExtractor.parseOEmbedJson(
            jsonBody = youtubeOEmbed,
            originalUrl = "https://www.youtube.com/watch?v=abc123xyz",
            domain = "youtube.com"
        )
        assertNotNull(ytMeta)
        assertEquals("Kotlin Coroutines Under the Hood", ytMeta?.title)
        assertEquals("By Android Developers", ytMeta?.description)
        assertEquals("YouTube", ytMeta?.siteName)
        assertEquals("https://i.ytimg.com/vi/abc123xyz/hqdefault.jpg", ytMeta?.previewImageUrl)
        assertEquals(ContentType.VIDEO, ytMeta?.contentType)

        val xOEmbed = """
            {
                "url": "https://twitter.com/androiddev/status/123456",
                "author_name": "Android Developers",
                "provider_name": "Twitter",
                "html": "<blockquote class=\"twitter-tweet\"><p lang=\"en\" dir=\"ltr\">Excited to announce Jetpack Compose performance improvements in the latest release!</p>&mdash; Android Developers (@AndroidDev)</blockquote>"
            }
        """.trimIndent()

        val xMeta = JsoupMetadataExtractor.parseOEmbedJson(
            jsonBody = xOEmbed,
            originalUrl = "https://x.com/androiddev/status/123456",
            domain = "x.com"
        )
        assertNotNull(xMeta)
        assertTrue(xMeta!!.title!!.contains("Excited to announce Jetpack Compose"))
        assertEquals("Excited to announce Jetpack Compose performance improvements in the latest release!", xMeta.description)
        assertNull(xMeta.previewImageUrl)
        assertEquals(ContentType.DISCUSSION, xMeta.contentType)
    }

    @Test
    fun httpErrorExposesStatusCodeInMetadataFetchException() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))
        val url = server.url("/protected").toString()

        val result = JsoupMetadataExtractor.extract(url)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is MetadataFetchException)
        assertEquals(403, (exception as MetadataFetchException).httpStatusCode)
    }
}


