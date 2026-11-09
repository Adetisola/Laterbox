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
        userName: String? = null,
        copy: com.laterbox.app.ai.SmartReminderCopy? = null
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

        // Fetch site logo or favicon via Coil cache / fallbacks
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

        // Use AI copy if provided, otherwise warm personalized context title & body
        val title = copy?.title ?: if (!userName.isNullOrBlank()) {
            "Hi $userName, your saved link is waiting"
        } else {
            "Revisit your saved link"
        }

        val body = copy?.body ?: if (!link.personalNote.isNullOrBlank()) {
            "\"${link.personalNote}\" • ${link.title}"
        } else {
            "${link.title} • ${link.domain}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(if (copy?.isAiGenerated == true) R.drawable.ic_huge_sparkles else R.drawable.ic_huge_clock)
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

        // Fetch site logo or favicon via Coil cache / fallbacks
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
        return kotlinx.coroutines.withTimeoutOrNull(1_500L) {
            val candidateUrls = listOfNotNull(
                link.faviconUrl,
                link.previewImageUrl,
                if (link.domain.isNotBlank()) "https://www.google.com/s2/favicons?domain=${link.domain}&sz=128" else null
            )

            val imageLoader = Coil.imageLoader(context)
            for (url in candidateUrls) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
                        .size(192, 192)
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) return@withTimeoutOrNull bitmap
                    }
                } catch (_: Exception) {
                    // Try next candidate
                }
            }

            // Fallback: Generate clean circular monogram bitmap
            createMonogramBitmap(link.domain)
        }
    }

    private fun createMonogramBitmap(domain: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#C25E40") // Laterbox Terracotta accent
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val cleanDomain = domain.removePrefix("www.")
        val letter = cleanDomain.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "L"
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 64f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val yPos = (canvas.height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, canvas.width / 2f, yPos, textPaint)
        return bitmap
    }
}
