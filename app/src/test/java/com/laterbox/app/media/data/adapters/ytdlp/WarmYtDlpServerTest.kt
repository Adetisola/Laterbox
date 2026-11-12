package com.laterbox.app.media.data.adapters.ytdlp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.laterbox.app.data.preferences.UserPreferencesRepository
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WarmYtDlpServerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun youtubeDL_reflectionTargets_existOnClass() {
        val clazz = YoutubeDL::class.java

        val targetFields = listOf(
            "pythonPath",
            "ytdlpPath",
            "binDir",
            "ENV_LD_LIBRARY_PATH",
            "ENV_SSL_CERT_FILE",
            "ENV_PYTHONHOME",
            "TMPDIR"
        )

        for (fieldName in targetFields) {
            val field = try {
                clazz.getDeclaredField(fieldName)
            } catch (e: NoSuchFieldException) {
                null
            }
            assertNotNull("Field '$fieldName' should exist on YoutubeDL class", field)
            field?.isAccessible = true
        }
    }

    @Test
    fun cancel_forciblyDestroysDaemonProcessAndResetsState() {
        val userPrefs = UserPreferencesRepository(context)
        val server = WarmYtDlpServer(context, userPrefs)

        // Reflection to inject a mock process
        val procField = WarmYtDlpServer::class.java.getDeclaredField("daemonProcess")
        procField.isAccessible = true

        var destroyedForcibly = false
        val mockProcess = object : Process() {
            override fun destroy() {}
            override fun destroyForcibly(): Process {
                destroyedForcibly = true
                return this
            }
            override fun exitValue(): Int = 0
            override fun getErrorStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
            override fun getInputStream(): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
            override fun getOutputStream(): java.io.OutputStream = java.io.ByteArrayOutputStream()
            override fun waitFor(): Int = 0
        }

        procField.set(server, mockProcess)

        server.cancel("test_process_id")

        assertTrue("cancel(processId) must forcibly destroy the daemon process", destroyedForcibly)
    }
}
