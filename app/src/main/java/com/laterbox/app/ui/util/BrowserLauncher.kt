package com.laterbox.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import com.laterbox.app.ui.theme.LaterboxColors

object BrowserLauncher {

    fun openUrl(context: Context, url: String, colors: LaterboxColors? = null) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        val validUri = try {
            val uri = Uri.parse(trimmed)
            if (uri.scheme == null || (!uri.scheme.equals("http", ignoreCase = true) && !uri.scheme.equals("https", ignoreCase = true))) {
                Uri.parse("https://$trimmed")
            } else {
                uri
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Invalid link URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val builder = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)

            if (colors != null) {
                val colorParams = CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(colors.backgroundPrimary.toArgb())
                    .setNavigationBarColor(colors.surfacePrimary.toArgb())
                    .build()
                builder.setDefaultColorSchemeParams(colorParams)
            }

            val customTabsIntent = builder.build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, validUri)
        } catch (e: Exception) {
            // Fallback to standard ACTION_VIEW if no Custom Tabs provider is available
            try {
                val intent = Intent(Intent.ACTION_VIEW, validUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (fallbackError: Exception) {
                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
