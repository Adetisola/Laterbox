package com.laterbox.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateInfoTest {

    @Test
    fun `cleanVersion strips leading v, version, and Letterbox prefix`() {
        assertEquals("1.0.15", AppUpdateInfo.cleanVersion("v1.0.15"))
        assertEquals("1.0.15", AppUpdateInfo.cleanVersion("V1.0.15"))
        assertEquals("1.0.15", AppUpdateInfo.cleanVersion("Version 1.0.15"))
        assertEquals("1.0.15", AppUpdateInfo.cleanVersion("Laterbox version 1.0.15"))
        assertEquals("1.0.10", AppUpdateInfo.cleanVersion("Letterbox version 1.0.10"))
    }

    @Test
    fun `isNewerVersion compares semver components correctly`() {
        // Newer patch
        assertTrue(AppUpdateInfo.isNewerVersion("1.0.15", "1.0.14"))
        assertTrue(AppUpdateInfo.isNewerVersion("v1.0.15", "1.0.14"))

        // Multi-digit version comparison (1.0.10 > 1.0.9)
        assertTrue(AppUpdateInfo.isNewerVersion("1.0.10", "1.0.9"))
        assertFalse(AppUpdateInfo.isNewerVersion("1.0.9", "1.0.10"))

        // Newer minor and major
        assertTrue(AppUpdateInfo.isNewerVersion("1.1.0", "1.0.15"))
        assertTrue(AppUpdateInfo.isNewerVersion("2.0.0", "1.0.15"))

        // Same version
        assertFalse(AppUpdateInfo.isNewerVersion("1.0.15", "1.0.15"))
        assertFalse(AppUpdateInfo.isNewerVersion("v1.0.15", "1.0.15"))

        // Older version
        assertFalse(AppUpdateInfo.isNewerVersion("1.0.13", "1.0.14"))
    }
}
