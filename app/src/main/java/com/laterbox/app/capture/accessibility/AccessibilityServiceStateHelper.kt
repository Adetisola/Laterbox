package com.laterbox.app.capture.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.text.TextUtils

data class AppExclusionItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
)

object AccessibilityServiceStateHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, LaterboxSelectionAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun getInstalledLaunchableApps(context: Context): List<AppExclusionItem> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val ownPackage = context.packageName
        return resolveInfos
            .filter { it.activityInfo.packageName != ownPackage }
            .map { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val name = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                AppExclusionItem(packageName = pkg, appName = name, icon = icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }
}
