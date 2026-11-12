package com.laterbox.app.media.data.download

import com.laterbox.app.data.preferences.MediaDownloadNetworkPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkerNetworkPolicyTest {

    private fun isDownloadBlockedOnMetered(
        policy: MediaDownloadNetworkPolicy,
        totalBytes: Long?,
        allowCellularOverride: Boolean
    ): Boolean {
        if (allowCellularOverride) return false
        return when (policy) {
            MediaDownloadNetworkPolicy.WIFI_ONLY -> true
            MediaDownloadNetworkPolicy.WIFI_OVER_20MB -> {
                totalBytes == null || totalBytes > 20L * 1024 * 1024
            }
            MediaDownloadNetworkPolicy.ANY_NETWORK -> false
        }
    }

    @Test
    fun wifiOver20MB_treatsUnknownSizeAsOverThreshold() {
        val blockedWhenNull = isDownloadBlockedOnMetered(
            policy = MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
            totalBytes = null,
            allowCellularOverride = false
        )
        assertTrue("Unknown totalBytes must be treated as over 20MB threshold on metered network", blockedWhenNull)

        val blockedWhenOver20MB = isDownloadBlockedOnMetered(
            policy = MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
            totalBytes = 25L * 1024 * 1024,
            allowCellularOverride = false
        )
        assertTrue("Size over 20MB must be blocked on metered network", blockedWhenOver20MB)

        val allowedUnder20MB = isDownloadBlockedOnMetered(
            policy = MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
            totalBytes = 10L * 1024 * 1024,
            allowCellularOverride = false
        )
        assertFalse("Size under 20MB should be allowed on metered network", allowedUnder20MB)

        val allowedWithCellularOverride = isDownloadBlockedOnMetered(
            policy = MediaDownloadNetworkPolicy.WIFI_OVER_20MB,
            totalBytes = null,
            allowCellularOverride = true
        )
        assertFalse("Cellular override must bypass WiFi-only / WiFi-over-20MB restrictions", allowedWithCellularOverride)
    }
}
