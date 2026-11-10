package com.laterbox.app.media

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.laterbox.app.media.data.adapters.ytdlp.YtDlpRuntimeManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device instrumentation test suite for yt-dlp native library packaging,
 * Apache Commons Compress reflective class instantiation, and YtDlpRuntimeManager.
 */
@RunWith(AndroidJUnit4::class)
class YtDlpNativeInitInstrumentedTest {

    private lateinit var context: Context
    private lateinit var runtimeManager: YtDlpRuntimeManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runtimeManager = YtDlpRuntimeManager(context)
    }

    @Test
    fun verifyDeviceAbiIsSupportedArm() {
        val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()
        assertTrue("Device must declare supported ABIs", supportedAbis.isNotEmpty())
        val hasArm = supportedAbis.any { it.contains("arm", ignoreCase = true) }
        assertTrue("Device must support ARM architecture (got: ${supportedAbis.joinToString()})", hasArm)
    }

    @Test
    fun verifyNativeLibrariesPresentInNativeLibraryDir() {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        assertTrue("nativeLibraryDir must exist: ${libDir.absolutePath}", libDir.exists() && libDir.isDirectory)

        val files = libDir.list()?.toSet() ?: emptySet()
        assertTrue("nativeLibraryDir must contain libpython.zip.so", "libpython.zip.so" in files)
        assertTrue("nativeLibraryDir must contain libpython.so", "libpython.so" in files)
        assertTrue("nativeLibraryDir must contain libqjs.so", "libqjs.so" in files)
    }

    @Test
    fun verifyApacheCommonsCompressExtraFieldReflectionWorks() {
        // This directly verifies the R8 fix:
        // In unpatched release builds, AsiExtraField had no <init>()V constructor and was marked abstract,
        // causing InstantiationException -> ExceptionInInitializerError in ExtraFieldUtils.<clinit>.
        val extraFieldClass = Class.forName("org.apache.commons.compress.archivers.zip.AsiExtraField")
        val constructor = extraFieldClass.getDeclaredConstructor()
        assertNotNull("AsiExtraField must have a public no-arg constructor preserved by R8", constructor)
        val instance = constructor.newInstance()
        assertNotNull("AsiExtraField must instantiate successfully", instance)

        // ExtraFieldUtils should initialize reflectively without ExceptionInInitializerError
        val utilsClass = Class.forName("org.apache.commons.compress.archivers.zip.ExtraFieldUtils")
        val registerMethod = utilsClass.getMethod("register", Class::class.java)
        registerMethod.invoke(null, extraFieldClass)
    }

    @Test
    fun verifyDiagnosticReportGeneration() {
        val report = runtimeManager.captureDiagnosticReport()
        assertNotNull(report)
        assertTrue(report.supportedAbis.isNotEmpty())
        assertTrue(report.nativeLibraryDir.isNotBlank())
        val formatted = report.toFormattedString()
        assertTrue(formatted.contains("Laterbox Media Engine Diagnostics"))
    }
}
