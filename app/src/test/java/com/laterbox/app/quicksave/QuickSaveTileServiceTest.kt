package com.laterbox.app.quicksave

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuickSaveTileServiceTest {

    @Test
    fun quickSaveTileService_isRegisteredInManifestWithTilePermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val componentName = ComponentName(context, QuickSaveTileService::class.java)

        val serviceInfo = packageManager.getServiceInfo(componentName, PackageManager.GET_META_DATA)
        assertNotNull(serviceInfo)
        assertTrue(serviceInfo.exported)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", serviceInfo.permission)
    }

    @Test
    fun quickSaveActivity_isRegisteredInManifestWithTransparentTheme() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val componentName = ComponentName(context, QuickSaveActivity::class.java)

        val activityInfo = packageManager.getActivityInfo(componentName, 0)
        assertNotNull(activityInfo)
        assertFalse(activityInfo.exported)
        assertEquals("com.laterbox.app.quicksave", activityInfo.taskAffinity)
    }
}
