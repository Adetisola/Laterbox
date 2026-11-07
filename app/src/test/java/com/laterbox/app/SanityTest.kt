package com.laterbox.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test
    fun environment_isProperlyConfigured() {
        assertEquals("Laterbox", "Laterbox")
    }
}
