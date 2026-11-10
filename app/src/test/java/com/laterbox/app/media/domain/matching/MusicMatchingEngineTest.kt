package com.laterbox.app.media.domain.matching

import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicMatchingEngineTest {

    private fun sampleSpotify(
        title: String = "Blinding Lights",
        primaryArtist: String = "The Weeknd",
        additionalArtists: List<String> = emptyList(),
        durationMs: Long? = 200000L
    ) = SpotifyTrackMetadata(
        trackId = "0VjIjW4GlUZAMYd2vXMi3b",
        title = title,
        primaryArtist = primaryArtist,
        additionalArtists = additionalArtists,
        albumName = "After Hours",
        artworkUrl = "https://example.com/art.jpg",
        durationMs = durationMs,
        releaseDate = "2020-03-20",
        isExplicit = false,
        audioPreviewUrl = null
    )

    private fun sampleCandidate(
        id: String = "cand1",
        title: String = "The Weeknd - Blinding Lights (Official Audio)",
        artist: String = "The Weeknd",
        durationMs: Long? = 201000L
    ) = RawMusicCandidate(
        id = id,
        title = title,
        artist = artist,
        durationMs = durationMs,
        downloadCandidate = MediaCandidate(
            candidateId = id,
            mediaType = com.laterbox.app.media.domain.model.MediaType.AUDIO,
            formatLabel = "MP3 Audio",
            estimatedBytes = 4000000L,
            durationMs = durationMs,
            width = null,
            height = null,
            source = com.laterbox.app.media.domain.model.AcquisitionSource.DirectHttp("https://example.com/audio/$id")
        )
    )

    @Test
    fun testCleanStringStripsQualifiersAndDiacritics() {
        val raw = "Beyoncé - CUFF IT (Official Audio) [HD]!"
        val cleaned = MusicMatchingEngine.cleanString(raw)
        assertEquals("beyonce cuff it", cleaned)
    }

    @Test
    fun testTokenSortSimilarityMatchesReorderedWords() {
        val s1 = "Taylor Swift Anti-Hero"
        val s2 = "Anti-Hero Taylor Swift"
        val sim = MusicMatchingEngine.tokenSortSimilarity(s1, s2)
        assertEquals(1.0, sim, 0.01)
    }

    @Test
    fun testDurationSimilarityDeltas() {
        // Delta 1 second -> 1.0
        assertEquals(1.0, MusicMatchingEngine.calculateDurationSimilarity(200000L, 201000L), 0.01)
        // Delta 6 seconds -> 0.85
        assertEquals(0.85, MusicMatchingEngine.calculateDurationSimilarity(200000L, 206000L), 0.01)
        // Delta 12 seconds -> 0.50
        assertEquals(0.50, MusicMatchingEngine.calculateDurationSimilarity(200000L, 212000L), 0.01)
        // Delta 30 seconds -> 0.0
        assertEquals(0.0, MusicMatchingEngine.calculateDurationSimilarity(200000L, 230000L), 0.01)
        // Null duration -> 0.50 default
        assertEquals(0.50, MusicMatchingEngine.calculateDurationSimilarity(null, 200000L), 0.01)
    }

    @Test
    fun testArtistSimilarityMatchesPrimaryOrAdditional() {
        val spotify = sampleSpotify(
            primaryArtist = "Post Malone",
            additionalArtists = listOf("Swae Lee")
        )

        // Matches primary
        val scorePrimary = MusicMatchingEngine.calculateArtistSimilarity(spotify, "Post Malone", "Sunflower")
        assertEquals(1.0, scorePrimary, 0.01)

        // Matches additional / featured artist
        val scoreFeatured = MusicMatchingEngine.calculateArtistSimilarity(spotify, "Swae Lee", "Sunflower")
        assertEquals(0.85, scoreFeatured, 0.01)

        // Artist in title
        val scoreInTitle = MusicMatchingEngine.calculateArtistSimilarity(spotify, "Vevo", "Post Malone - Sunflower")
        assertEquals(1.0, scoreInTitle, 0.01)
    }

    @Test
    fun testVersionPenaltyForRemixWhenOriginalIsNotRemix() {
        val (penalty, tag) = MusicMatchingEngine.detectVersionModifierPenalty(
            spotifyTitle = "Levitating",
            candidateTitle = "Levitating (The Blessed Madonna Remix)"
        )
        assertEquals(0.40, penalty, 0.01)
        assertEquals("Remix", tag)
    }

    @Test
    fun testNoVersionPenaltyWhenOriginalSpotifyIsRemix() {
        val (penalty, tag) = MusicMatchingEngine.detectVersionModifierPenalty(
            spotifyTitle = "Levitating (feat. DaBaby) - Remix",
            candidateTitle = "Levitating - DaBaby Remix"
        )
        assertEquals(0.0, penalty, 0.01)
        assertNull(tag)
    }

    @Test
    fun testStrongMatchCandidate() {
        val spotify = sampleSpotify(
            title = "Flowers",
            primaryArtist = "Miley Cyrus",
            durationMs = 200000L
        )
        val candidate = sampleCandidate(
            id = "c1",
            title = "Miley Cyrus - Flowers (Official Video)",
            artist = "Miley Cyrus",
            durationMs = 201000L
        )

        val match = MusicMatchingEngine.matchCandidate(spotify, candidate)
        assertEquals(MatchConfidence.STRONG, match.confidence)
        assertTrue(match.score >= 0.85)
        assertNull(match.versionTag)
    }

    @Test
    fun testVersionMismatchConfidence() {
        val spotify = sampleSpotify(
            title = "Flowers",
            primaryArtist = "Miley Cyrus",
            durationMs = 200000L
        )
        val candidate = sampleCandidate(
            id = "c1",
            title = "Miley Cyrus - Flowers (Acoustic Live Session)",
            artist = "Miley Cyrus",
            durationMs = 202000L
        )

        val match = MusicMatchingEngine.matchCandidate(spotify, candidate)
        assertEquals(MatchConfidence.VERSION_MISMATCH, match.confidence)
        assertNotNull(match.versionTag)
    }

    @Test
    fun testRankCandidatesPrioritizesStrongMatchOverRemix() {
        val spotify = sampleSpotify(
            title = "Save Your Tears",
            primaryArtist = "The Weeknd",
            durationMs = 215000L
        )

        val official = sampleCandidate(
            id = "official",
            title = "The Weeknd - Save Your Tears (Official Audio)",
            artist = "The Weeknd",
            durationMs = 216000L
        )
        val remix = sampleCandidate(
            id = "remix",
            title = "The Weeknd - Save Your Tears (Remix)",
            artist = "The Weeknd",
            durationMs = 216000L
        )
        val live = sampleCandidate(
            id = "live",
            title = "The Weeknd - Save Your Tears (Live at the BRIT Awards)",
            artist = "The Weeknd",
            durationMs = 240000L
        )

        val ranked = MusicMatchingEngine.rankCandidates(spotify, listOf(live, remix, official))
        assertEquals(3, ranked.size)
        assertEquals("official", ranked[0].candidateId)
        assertEquals(MatchConfidence.STRONG, ranked[0].confidence)
    }
}
