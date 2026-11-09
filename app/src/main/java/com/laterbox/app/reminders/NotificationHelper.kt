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
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.laterbox.app.R
import com.laterbox.app.ai.ReminderOrigin
import com.laterbox.app.ai.SmartReminderCopy
import com.laterbox.app.domain.model.SavedLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
        origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST,
        copy: SmartReminderCopy? = null
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

        val app = context.applicationContext as? com.laterbox.app.LaterboxApplication

        // 1. Re-read latest SavedLink from Room DB so recently enriched metadata is captured
        var effectiveLink = app?.container?.linkRepository?.let { repo ->
            repo.getLinkById(link.id).first() ?: link
        } ?: link

        // 2. Best-effort non-blocking metadata retrieval if preview image is missing
        if (effectiveLink.previewImageUrl.isNullOrBlank() && effectiveLink.id > 0) {
            withTimeoutOrNull(2500L) {
                app?.container?.linkRepository?.fetchMetadataNow(effectiveLink.id)
            }
            effectiveLink = app?.container?.linkRepository?.let { repo ->
                repo.getLinkById(effectiveLink.id).first() ?: effectiveLink
            } ?: effectiveLink
        }

        // Use AI copy if provided, otherwise warm personalized context title & body
        val title = copy?.title ?: if (origin == ReminderOrigin.MANUAL) {
            if (!userName.isNullOrBlank()) "Hi $userName, here is what you scheduled" else "Your scheduled reminder"
        } else {
            if (!userName.isNullOrBlank()) "Hi $userName, your link is waiting" else "Revisit your saved link"
        }

        val body = copy?.body ?: if (origin == ReminderOrigin.MANUAL) {
            if (!effectiveLink.personalNote.isNullOrBlank()) {
                "You asked to be reminded: \"${effectiveLink.personalNote}\" • ${effectiveLink.title}"
            } else {
                "You asked to revisit \"${effectiveLink.title}\" from ${effectiveLink.domain}."
            }
        } else {
            if (!effectiveLink.personalNote.isNullOrBlank()) {
                "\"${effectiveLink.personalNote}\" • ${effectiveLink.title}"
            } else {
                "${effectiveLink.title} • ${effectiveLink.domain}"
            }
        }

        val notificationId = effectiveLink.id.toInt()

        // 3. Record in-app notification history
        val historyRepo = app?.container?.notificationHistoryRepository
        val notificationRecordId = historyRepo?.recordNotification(
            linkId = effectiveLink.id,
            url = effectiveLink.originalUrl,
            title = title,
            body = body,
            origin = origin.name,
            domain = effectiveLink.domain,
            siteName = effectiveLink.siteName,
            previewImageUrl = effectiveLink.previewImageUrl,
            faviconUrl = effectiveLink.faviconUrl,
            isRead = false,
            createdAt = System.currentTimeMillis()
        ) ?: -1L

        // 4. Intent routing:
        // Body tap routes to in-app link detail sheet in Laterbox
        val openDetailIntent = NotificationOpenActivity.createIntent(
            context = context,
            linkId = effectiveLink.id,
            url = effectiveLink.originalUrl,
            notificationRecordId = notificationRecordId,
            notificationId = notificationId,
            mode = NotificationOpenActivity.MODE_OPEN_LINK_DETAIL
        )
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            effectiveLink.id.hashCode(),
            openDetailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: "Open Link" button routes directly to external browser
        val openBrowserIntent = NotificationOpenActivity.createIntent(
            context = context,
            linkId = effectiveLink.id,
            url = effectiveLink.originalUrl,
            notificationRecordId = notificationRecordId,
            notificationId = notificationId,
            mode = NotificationOpenActivity.MODE_OPEN_BROWSER
        )
        val openBrowserPendingIntent = PendingIntent.getActivity(
            context,
            (effectiveLink.id + 200_000L).hashCode(),
            openBrowserIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: Dismiss Reminder
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS_REMINDER
            putExtra(ReminderReceiver.EXTRA_LINK_ID, effectiveLink.id)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (effectiveLink.id + 100_000L).hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Image & Branding: fetch OG preview image, or recognized platform brand bitmap, or site logo
        val previewBitmap: Bitmap? = fetchPreviewImage(context, effectiveLink.previewImageUrl)
        val brand = com.laterbox.app.domain.util.PlatformBrandResolver.resolve(effectiveLink.domain)
        val fallbackLogoBitmap: Bitmap? = if (previewBitmap == null) {
            if (brand != null) {
                renderVectorToBitmap(context, brand.iconRes)
            } else {
                fetchSiteLogo(context, effectiveLink)
            }
        } else {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_laterbox) // Compliant monochrome small icon
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(
                R.drawable.ic_huge_arrow_up_right,
                "Open Link",
                openBrowserPendingIntent
            )
            .addAction(
                R.drawable.ic_huge_close,
                "Dismiss",
                dismissPendingIntent
            )

        if (previewBitmap != null) {
            builder.setLargeIcon(previewBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(previewBitmap)
                    .setBigContentTitle(title)
                    .setSummaryText(body)
            )
        } else {
            if (fallbackLogoBitmap != null) {
                builder.setLargeIcon(fallbackLogoBitmap)
            }
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        notificationManager.notify(notificationId, builder.build())
        return true
    }

    suspend fun showSmartDigestNotification(
        context: Context,
        link: SavedLink,
        copy: SmartReminderCopy
    ): Boolean {
        return showReminderNotification(
            context = context,
            link = link,
            origin = copy.origin,
            copy = copy
        )
    }

    @DrawableRes
    fun resolvePlatformIcon(domain: String): Int {
        return com.laterbox.app.domain.util.PlatformBrandResolver.resolve(domain)?.iconRes
            ?: R.drawable.ic_platform_web
    }

    private fun renderVectorToBitmap(context: Context, @DrawableRes drawableRes: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private suspend fun fetchPreviewImage(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withTimeoutOrNull(2_000L) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(1024, 512)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? BitmapDrawable)?.bitmap
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun fetchSiteLogo(context: Context, link: SavedLink): Bitmap? {
        return withTimeoutOrNull(1_500L) {
            val candidateUrls = listOfNotNull(
                link.faviconUrl,
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
