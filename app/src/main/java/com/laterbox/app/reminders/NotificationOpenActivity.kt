package com.laterbox.app.reminders

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.laterbox.app.LaterboxApplication
import com.laterbox.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Trampoline-compliant Activity that handles notification interactions.
 * Routes body taps to the in-app detail sheet and action buttons to external browser,
 * while updating read states asynchronously.
 */
class NotificationOpenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val linkId = intent.getLongExtra(EXTRA_LINK_ID, -1L)
        val targetUrl = intent.getStringExtra(EXTRA_URL)
        val notifRecordId = intent.getLongExtra(EXTRA_NOTIFICATION_RECORD_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val targetMode = intent.getStringExtra(EXTRA_TARGET_MODE) ?: MODE_OPEN_LINK_DETAIL

        // 1. Cancel the active status bar notification
        if (notificationId != -1) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        // 2. Mark the saved link and notification history record as read asynchronously
        val container = (applicationContext as? LaterboxApplication)?.container
        if (container != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (linkId > 0) {
                        container.linkRepository.setRead(linkId, true)
                    }
                    if (notifRecordId > 0) {
                        container.notificationHistoryRepository.markAsRead(notifRecordId)
                    } else if (linkId > 0) {
                        container.notificationHistoryRepository.markReadByLinkId(linkId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update link/notification read state", e)
                }
            }
        }

        // 3. Route according to interaction mode
        if (targetMode == MODE_OPEN_BROWSER) {
            // "Open Link" action button: Open original URL in external browser
            if (!targetUrl.isNullOrBlank()) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open target URL: $targetUrl", e)
                }
            }
        } else {
            // Notification body tap: Open Laterbox and navigate to the saved link's detail sheet
            try {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    if (linkId > 0) {
                        putExtra(MainActivity.EXTRA_OPEN_LINK_ID, linkId)
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(mainIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to navigate to MainActivity for link $linkId", e)
            }
        }

        // 4. Cleanly finish internal routing activity
        finish()
    }

    companion object {
        private const val TAG = "NotificationOpenActivity"
        const val EXTRA_LINK_ID = "extra_link_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_NOTIFICATION_RECORD_ID = "extra_notification_record_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TARGET_MODE = "extra_target_mode"

        const val MODE_OPEN_LINK_DETAIL = "mode_open_link_detail"
        const val MODE_OPEN_BROWSER = "mode_open_browser"

        fun createIntent(
            context: Context,
            linkId: Long,
            url: String,
            notificationRecordId: Long,
            notificationId: Int,
            mode: String = MODE_OPEN_LINK_DETAIL
        ): Intent {
            return Intent(context, NotificationOpenActivity::class.java).apply {
                putExtra(EXTRA_LINK_ID, linkId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_NOTIFICATION_RECORD_ID, notificationRecordId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_TARGET_MODE, mode)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }
}
