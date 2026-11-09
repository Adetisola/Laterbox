package com.laterbox.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.laterbox.app.MainActivity
import com.laterbox.app.R
import com.laterbox.app.domain.model.SavedLink

object NotificationHelper {

    const val CHANNEL_ID_REMINDERS = "channel_link_reminders"
    private const val CHANNEL_NAME = "Link Reminders"
    private const val CHANNEL_DESCRIPTION = "Quiet reminders for links you saved to revisit later"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    suspend fun showReminderNotification(
        context: Context,
        link: SavedLink,
        userName: String? = null
    ): Boolean {
        createNotificationChannels(context)

        // Android 13+ permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                return false
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        // Fetch site logo or favicon via Coil cache
        val logoBitmap: Bitmap? = fetchSiteLogo(context, link)

        // Tap content intent -> opens the link directly in Laterbox / MainActivity
        val contentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link.originalUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            link.id.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss Reminder
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS_REMINDER
            putExtra(ReminderReceiver.EXTRA_LINK_ID, link.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (link.id + 100_000L).hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Warm, natural title with personalization
        val title = if (!userName.isNullOrBlank()) {
            "Hi $userName, your saved link is waiting"
        } else {
            "Revisit your saved link"
        }

        // Context-aware text drawing from personal notes, domain, and title
        val body = if (!link.personalNote.isNullOrBlank()) {
            "\"${link.personalNote}\" • ${link.title}"
        } else {
            "${link.title} • ${link.domain}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_huge_clock)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(
                R.drawable.ic_huge_arrow_up_right,
                "Open Link",
                contentPendingIntent
            )
            .addAction(
                R.drawable.ic_huge_close,
                "Dismiss",
                dismissPendingIntent
            )

        if (logoBitmap != null) {
            builder.setLargeIcon(logoBitmap)
        }

        notificationManager.notify(link.id.toInt(), builder.build())
        return true
    }

    suspend fun showSmartDigestNotification(
        context: Context,
        link: SavedLink,
        copy: com.laterbox.app.ai.SmartReminderCopy
    ): Boolean {
        createNotificationChannels(context)

        // Android 13+ permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                return false
            }
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        // Fetch site logo or favicon via Coil cache
        val logoBitmap: Bitmap? = fetchSiteLogo(context, link)

        // Tap content intent -> opens the link directly in browser / app
        val contentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link.originalUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (link.id + 200_000L).hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss Reminder
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS_REMINDER
            putExtra(ReminderReceiver.EXTRA_LINK_ID, link.id)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (link.id + 300_000L).hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_huge_sparkles)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(
                R.drawable.ic_huge_arrow_up_right,
                "Open Link",
                contentPendingIntent
            )
            .addAction(
                R.drawable.ic_huge_close,
                "Dismiss",
                dismissPendingIntent
            )

        if (logoBitmap != null) {
            builder.setLargeIcon(logoBitmap)
        }

        notificationManager.notify((link.id + 500_000L).toInt(), builder.build())
        return true
    }

    private suspend fun fetchSiteLogo(context: Context, link: SavedLink): Bitmap? {
        val targetUrl = link.faviconUrl ?: link.previewImageUrl ?: return null
        return try {
            val imageLoader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(targetUrl)
                .allowHardware(false)
                .size(192, 192)
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
