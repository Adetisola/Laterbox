package com.laterbox.app.media.domain.matching

import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MusicMatchCandidate
import com.laterbox.app.media.domain.model.SpotifyTrackMetadata
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max

object MusicMatchingEngine {

    private const val WEIGHT_TITLE = 0.40
    private const val WEIGHT_ARTIST = 0.30
    private const val WEIGHT_DURATION = 0.30
    private const val VERSION_PENALTY = 0.40

    private val VERSION_KEYWORDS = listOf(
        "live",
        "remix",
        "acoustic",
        "cover",
        "instrumental",
        "karaoke",
        "slowed",
        "reverb",
        "sped up",
        "speed up",
        "orchestral"
    )

    private val BRACKETED_QUALIFIER_REGEX = Pattern.compile(
        """\s*(\(|\[)(?:official\s*(?:video|audio|music\s*video|hd|hq|lyrics|lyric\s*video)?|hd|hq|remaster(?:ed)?|lyrics|audio)(\)|\])""",
        Pattern.CASE_INSENSITIVE
    )

    fun cleanString(input: String): String {
        if (input.isBlank()) return ""
        var cleaned = BRACKETED_QUALIFIER_REGEX.matcher(input).replaceAll("")
        // Strip accents/diacritics
        cleaned = Normalizer.normalize(cleaned, Normalizer.Form.NFD)
            .replace("""\p{InCombiningDiacriticalMarks}+""".toRegex(), "")
        // Replace non-alphanumeric (except whitespace) with single space
        cleaned = cleaned.replace("""[^a-zA-Z0-9\s]""".toRegex(), " ")
        return cleaned.lowercase(Locale.US).trim().replace("""\s+""".toRegex(), " ")
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) {
                    prev
                } else {
                    minOf(prev + 1, dp[j] + 1, dp[j - 1] + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    fun stringSimilarity(s1: String, s2: String): Double {
        val c1 = cleanString(s1)
        val c2 = cleanString(s2)
        if (c1.isEmpty() && c2.isEmpty()) return 1.0
        if (c1.isEmpty() || c2.isEmpty()) return 0.0
        if (c1 == c2) return 1.0

        val dist = levenshteinDistance(c1, c2)
        val maxLen = max(c1.length, c2.length)
        return (1.0 - (dist.toDouble() / maxLen.toDouble())).coerceIn(0.0, 1.0)
    }

    fun tokenSortSimilarity(s1: String, s2: String): Double {
        val c1 = cleanString(s1)
        val c2 = cleanString(s2)
        if (c1 == c2) return 1.0

        val tokens1 = c1.split(" ").filter { it.isNotBlank() }.sorted().joinToString(" ")
        val tokens2 = c2.split(" ").filter { it.isNotBlank() }.sorted().joinToString(" ")
        return stringSimilarity(tokens1, tokens2)
    }

    fun calculateTitleSimilarity(
        spotifyTitle: String,
        candidateTitle: String,
        primaryArtist: String = ""
    ): Double {
        val cleanSpot = cleanString(spotifyTitle)
        val cleanCand = cleanString(candidateTitle)

        if (cleanSpot.isEmpty() && cleanCand.isEmpty()) return 1.0
        if (cleanSpot.isEmpty() || cleanCand.isEmpty()) return 0.0

        val directSim = stringSimilarity(cleanSpot, cleanCand)
        val tokenSim = tokenSortSimilarity(cleanSpot, cleanCand)
        var bestScore = max(directSim, tokenSim)

        // If candidate title contains artist name (common on YouTube e.g. "Artist - Song"),
        // strip artist name to compare the song portion.
        if (primaryArtist.isNotBlank()) {
            val cleanArtist = cleanString(primaryArtist)
            if (cleanArtist.isNotBlank() && cleanCand.contains(cleanArtist)) {
                val titleWithoutArtist = cleanCand.replace(cleanArtist, "").trim()
                if (titleWithoutArtist.isNotBlank()) {
                    val simWithoutArtist = max(
                        stringSimilarity(cleanSpot, titleWithoutArtist),
                        tokenSortSimilarity(cleanSpot, titleWithoutArtist)
                    )
                    bestScore = max(bestScore, simWithoutArtist)
                }
            }
        }

        // Check if candidate title contains delimiters like '-' or ':' separating artist and title
        val delimiters = listOf(" - ", " – ", " — ", ":", "|")
        for (delim in delimiters) {
            if (candidateTitle.contains(delim)) {
                val parts = candidateTitle.split(delim)
                for (part in parts) {
                    val cleanPart = cleanString(part)
                    if (cleanPart.isNotBlank()) {
                        val partSim = max(
                            stringSimilarity(cleanSpot, cleanPart),
                            tokenSortSimilarity(cleanSpot, cleanPart)
                        )
                        bestScore = max(bestScore, partSim)
                    }
                }
            }
        }

        // If candidate contains exact Spotify title as substring (with word boundaries)
        if (cleanCand.contains(cleanSpot)) {
            bestScore = max(bestScore, 0.90)
        }

        return bestScore.coerceIn(0.0, 1.0)
    }

    fun calculateArtistSimilarity(
        spotify: SpotifyTrackMetadata,
        candidateArtist: String,
        candidateTitle: String
    ): Double {
        val cleanCandArtist = cleanString(candidateArtist)
        val cleanPrimary = cleanString(spotify.primaryArtist)
        val cleanCandTitle = cleanString(candidateTitle)

        // Exact or near-exact match on primary artist
        val primarySim = stringSimilarity(cleanPrimary, cleanCandArtist)
        if (primarySim >= 0.85 || cleanCandArtist.contains(cleanPrimary) || cleanCandTitle.contains(cleanPrimary)) {
            return 1.0
        }

        // Check if any featured/collaborating artists match
        for (additional in spotify.additionalArtists) {
            val cleanAdd = cleanString(additional)
            if (cleanAdd.isNotBlank()) {
                val addSim = stringSimilarity(cleanAdd, cleanCandArtist)
                if (addSim >= 0.85 || cleanCandArtist.contains(cleanAdd) || cleanCandTitle.contains(cleanAdd)) {
                    return 0.85
                }
            }
        }

        return primarySim.coerceIn(0.0, 1.0)
    }

    fun calculateDurationSimilarity(spotifyDurationMs: Long?, candidateDurationMs: Long?): Double {
        if (spotifyDurationMs == null || spotifyDurationMs <= 0 || candidateDurationMs == null || candidateDurationMs <= 0) {
            return 0.50 // Unknown duration default
        }

        val deltaSeconds = abs(candidateDurationMs - spotifyDurationMs) / 1000.0
        return when {
            deltaSeconds <= 3.0 -> 1.0
            deltaSeconds <= 8.0 -> 0.85
            deltaSeconds <= 15.0 -> 0.50
            else -> 0.0
        }
    }

    fun detectVersionModifierPenalty(
        spotifyTitle: String,
        candidateTitle: String
    ): Pair<Double, String?> {
        val cleanSpot = cleanString(spotifyTitle)
        val cleanCand = cleanString(candidateTitle)

        for (kw in VERSION_KEYWORDS) {
            val inCand = cleanCand.contains(kw)
            val inSpot = cleanSpot.contains(kw)

            if (inCand && !inSpot) {
                // Version keyword in candidate but not in official Spotify track
                return Pair(VERSION_PENALTY, kw.replaceFirstChar { it.uppercase() })
            }
        }

        return Pair(0.0, null)
    }

    fun matchCandidate(
        spotify: SpotifyTrackMetadata,
        candidate: RawMusicCandidate
    ): MusicMatchCandidate {
        val titleScore = calculateTitleSimilarity(spotify.title, candidate.title, spotify.primaryArtist)
        val artistScore = calculateArtistSimilarity(spotify, candidate.artist, candidate.title)
        val durationScore = calculateDurationSimilarity(spotify.durationMs, candidate.durationMs)
        val (penalty, versionTag) = detectVersionModifierPenalty(spotify.title, candidate.title)

        val rawScore = (WEIGHT_TITLE * titleScore) +
                (WEIGHT_ARTIST * artistScore) +
                (WEIGHT_DURATION * durationScore) -
                penalty

        val finalScore = rawScore.coerceIn(0.0, 1.0)

        val confidence = when {
            penalty > 0 -> MatchConfidence.VERSION_MISMATCH
            finalScore >= 0.85 -> MatchConfidence.STRONG
            finalScore >= 0.65 -> MatchConfidence.POSSIBLE
            else -> MatchConfidence.WEAK
        }

        return MusicMatchCandidate(
            candidateId = candidate.id,
            title = candidate.title,
            artist = candidate.artist,
            durationMs = candidate.durationMs,
            score = finalScore,
            confidence = confidence,
            versionTag = versionTag,
            downloadCandidate = candidate.downloadCandidate,
            sourceDescription = candidate.sourceDescription
        )
    }

    fun rankCandidates(
        spotify: SpotifyTrackMetadata,
        candidates: List<RawMusicCandidate>
    ): List<MusicMatchCandidate> {
        if (candidates.isEmpty()) return emptyList()

        return candidates
            .map { matchCandidate(spotify, it) }
            .sortedWith(
                compareByDescending<MusicMatchCandidate> { it.confidence == MatchConfidence.STRONG }
                    .thenByDescending { it.score }
            )
    }
}
