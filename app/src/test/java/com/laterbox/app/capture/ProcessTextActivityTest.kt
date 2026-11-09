package com.laterbox.app.capture

import android.app.Activity
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.LaterboxApplication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = LaterboxApplication::class, sdk = [34])
class ProcessTextActivityTest {

    @Test
    fun launchesWithProcessTextIntentAndSetsCanceledResult() = runTest {
        val app = ApplicationProvider.getApplicationContext<LaterboxApplication>()

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            setClass(app, ProcessTextActivity::class.java)
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, "Check https://example.com/selected-link")
        }

        val scenario = ActivityScenario.launch<ProcessTextActivity>(intent)

        scenario.onActivity { activity ->
            assertNotNull(activity)
            val shadow = shadowOf(activity)
            assertEquals(Activity.RESULT_CANCELED, shadow.resultCode)

            val extraction = UrlCaptureExtractor.extract(
                activity.intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            )
            assertTrue(extraction is UrlExtractionResult.SingleUrl)
            assertEquals("https://example.com/selected-link", (extraction as UrlExtractionResult.SingleUrl).normalizedUrl)
        }

        scenario.close()
    }
}
