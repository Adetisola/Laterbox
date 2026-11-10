package com.laterbox.app.media.data.adapters.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyUrlParserTest {

    @Test
    fun isSpotifyUrl_detectsVariousSpotifyHosts() {
        assertTrue(SpotifyUrlParser.isSpotifyUrl("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertTrue(SpotifyUrlParser.isSpotifyUrl("https://open.spotify.com/intl-fr/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertTrue(SpotifyUrlParser.isSpotifyUrl("https://spotify.link/AbcDef123"))
        assertTrue(SpotifyUrlParser.isSpotifyUrl("spotify:track:4cOdK2wGLETKBW3PvgPWqT"))
        assertFalse(SpotifyUrlParser.isSpotifyUrl("https://youtube.com/watch?v=123"))
        assertFalse(SpotifyUrlParser.isSpotifyUrl("https://example.com/not-spotify"))
    }

    @Test
    fun parse_standardTrackUrl_returnsTrackWithTrackId() {
        val result = SpotifyUrlParser.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(result is SpotifyLink.Track)
        val track = result as SpotifyLink.Track
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", track.trackId)
        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", track.canonicalUrl)
    }

    @Test
    fun parse_trackUrlWithQueryParams_stripsParamsAndExtractsTrackId() {
        val result = SpotifyUrlParser.parse("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=abc123xyz&context=spotify%3Aalbum%3A123")
        assertTrue(result is SpotifyLink.Track)
        val track = result as SpotifyLink.Track
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", track.trackId)
    }

    @Test
    fun parse_internationalTrackUrl_extractsTrackId() {
        val result = SpotifyUrlParser.parse("https://open.spotify.com/intl-de/track/5HCyWlXZPP0y6Gqq8TgA20?si=test")
        assertTrue(result is SpotifyLink.Track)
        val track = result as SpotifyLink.Track
        assertEquals("5HCyWlXZPP0y6Gqq8TgA20", track.trackId)
    }

    @Test
    fun parse_spotifyUri_extractsTrackId() {
        val result = SpotifyUrlParser.parse("spotify:track:4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(result is SpotifyLink.Track)
        val track = result as SpotifyLink.Track
        assertEquals("4cOdK2wGLETKBW3PvgPWqT", track.trackId)
    }

    @Test
    fun parse_shortLink_returnsShortLink() {
        val result = SpotifyUrlParser.parse("https://spotify.link/AbCdEf123")
        assertTrue(result is SpotifyLink.ShortLink)
        val short = result as SpotifyLink.ShortLink
        assertEquals("https://spotify.link/AbCdEf123", short.rawUrl)
    }

    @Test
    fun parse_nonTrackEntities_identifiesUnsupportedType() {
        val album = SpotifyUrlParser.parse("https://open.spotify.com/album/1DFixLWuPkv3KT3TnV35m3")
        assertTrue(album is SpotifyLink.UnsupportedType)
        assertEquals("album", (album as SpotifyLink.UnsupportedType).type)

        val playlist = SpotifyUrlParser.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        assertTrue(playlist is SpotifyLink.UnsupportedType)
        assertEquals("playlist", (playlist as SpotifyLink.UnsupportedType).type)

        val artistUri = SpotifyUrlParser.parse("spotify:artist:0gxyHStUsqpMadRV0Di1Qt")
        assertTrue(artistUri is SpotifyLink.UnsupportedType)
        assertEquals("artist", (artistUri as SpotifyLink.UnsupportedType).type)
    }

    @Test
    fun parse_invalidInput_returnsInvalid() {
        assertEquals(SpotifyLink.Invalid, SpotifyUrlParser.parse(""))
        assertEquals(SpotifyLink.Invalid, SpotifyUrlParser.parse("   "))
        assertEquals(SpotifyLink.Invalid, SpotifyUrlParser.parse("https://open.spotify.com/track/"))
        assertEquals(SpotifyLink.Invalid, SpotifyUrlParser.parse("https://youtube.com/watch?v=123"))
    }
}
