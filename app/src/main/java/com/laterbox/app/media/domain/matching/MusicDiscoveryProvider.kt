package com.laterbox.app.media.domain.matching

import com.laterbox.app.media.domain.model.SpotifyTrackMetadata

interface MusicDiscoveryProvider {
    suspend fun discoverCandidates(spotify: SpotifyTrackMetadata): List<RawMusicCandidate>
}
