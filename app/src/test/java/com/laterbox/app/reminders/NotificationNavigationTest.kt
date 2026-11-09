package com.laterbox.app.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationNavigationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `createIntent configures MODE_OPEN_LINK_DETAIL for body tap`() {
        val intent = NotificationOpenActivity.createIntent(
            context = context,
            linkId = 42L,
            url = "https://example.com/test",
            notificationRecordId = 101L,
            notificationId = 1,
            mode = NotificationOpenActivity.MODE_OPEN_LINK_DETAIL
        )

        assertNotNull(intent)
        assertEquals(42L, intent.getLongExtra(NotificationOpenActivity.EXTRA_LINK_ID, -1L))
        assertEquals(NotificationOpenActivity.MODE_OPEN_LINK_DETAIL, intent.getStringExtra(NotificationOpenActivity.EXTRA_TARGET_MODE))
    }

    @Test
    fun `createIntent configures MODE_OPEN_BROWSER for action button`() {
        val intent = NotificationOpenActivity.createIntent(
            context = context,
            linkId = 42L,
            url = "https://example.com/test",
            notificationRecordId = 101L,
            notificationId = 1,
            mode = NotificationOpenActivity.MODE_OPEN_BROWSER
        )

        assertNotNull(intent)
        assertEquals(42L, intent.getLongExtra(NotificationOpenActivity.EXTRA_LINK_ID, -1L))
        assertEquals(NotificationOpenActivity.MODE_OPEN_BROWSER, intent.getStringExtra(NotificationOpenActivity.EXTRA_TARGET_MODE))
    }
}
