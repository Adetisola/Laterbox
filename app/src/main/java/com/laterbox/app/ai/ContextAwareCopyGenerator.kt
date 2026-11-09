package com.laterbox.app.ai

import com.laterbox.app.domain.model.ContentType
import com.laterbox.app.domain.model.SavedLink
import java.util.concurrent.TimeUnit

object ContextAwareCopyGenerator {

    fun generateCopy(
        link: SavedLink,
        userName: String? = null,
        now: Long = System.currentTimeMillis(),
        origin: ReminderOrigin = ReminderOrigin.SMART_DIGEST
    ): SmartReminderCopy {
        val cleanName = userName?.trim()?.takeIf { it.isNotEmpty() }
        val platform = detectPlatformName(link.domain, link.siteName)
        val timeAgo = calculateTimeAgo(link.createdAt, now)
        val hasNote = !link.personalNote.isNullOrBlank()

        val title = buildTitle(link, cleanName, origin)
        val body = buildBody(link, cleanName, platform, timeAgo, hasNote, origin)

        return SmartReminderCopy(
            title = title.take(50),
            body = body.take(150),
            isAiGenerated = false,
            origin = origin
        )
    }

    private fun buildTitle(link: SavedLink, userName: String?, origin: ReminderOrigin): String {
        return if (origin == ReminderOrigin.MANUAL) {
            if (!userName.isNullOrBlank()) {
                "Hi $userName, here is what you scheduled"
            } else {
                "Your scheduled reminder"
            }
        } else {
            if (!userName.isNullOrBlank()) {
                "Hi $userName, your link is waiting"
            } else {
                when (link.contentType) {
                    ContentType.ARTICLE -> "Ready for this read?"
                    ContentType.VIDEO -> "Watch when you have a moment"
                    ContentType.DISCUSSION -> "Catch up on this thread"
                    ContentType.PRODUCT -> "Saved item in your inbox"
                    ContentType.WEBSITE -> "Revisit your saved link"
                }
            }
        }
    }

    private fun buildBody(
        link: SavedLink,
        userName: String?,
        platform: String,
        timeAgo: String,
        hasNote: Boolean,
        origin: ReminderOrigin
    ): String {
        if (origin == ReminderOrigin.MANUAL) {
            val greeting = if (!userName.isNullOrBlank()) "Hi $userName, you" else "You"
            return if (hasNote) {
                val note = link.personalNote!!.trim()
                "$greeting asked to be reminded: \"$note\" • ${link.title}"
            } else {
                "$greeting asked to revisit \"${link.title}\" from $platform."
            }
        }

        if (hasNote) {
            val note = link.personalNote!!.trim()
            val greeting = if (!userName.isNullOrBlank()) "Hi $userName, you" else "You"
            return "$greeting noted: \"$note\" • ${link.title}"
        }

        val greeting = if (!userName.isNullOrBlank()) "Hi $userName, seems" else "Seems"

        return when {
            platform.contains("Instagram", ignoreCase = true) || platform.contains("TikTok", ignoreCase = true) -> {
                "$greeting like you haven't checked the $platform post you saved $timeAgo."
            }
            link.contentType == ContentType.VIDEO || platform.contains("YouTube", ignoreCase = true) -> {
                "$greeting like you haven't watched the video from $platform you saved $timeAgo."
            }
            link.contentType == ContentType.ARTICLE -> {
                "$greeting like you haven't finished reading \"${link.title}\" from $platform."
            }
            link.contentType == ContentType.DISCUSSION -> {
                "$greeting like there's an unread discussion on $platform you saved $timeAgo."
            }
            else -> {
                "$greeting like you haven't checked the link from $platform you saved $timeAgo."
            }
        }
    }

    private fun detectPlatformName(domain: String, siteName: String?): String {
        if (!siteName.isNullOrBlank() && siteName.length <= 25) {
            return siteName.trim()
        }
        val lower = domain.lowercase()
        return when {
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("twitter.com") || lower.contains("x.com") -> "X"
            lower.contains("reddit.com") -> "Reddit"
            lower.contains("github.com") -> "GitHub"
            lower.contains("medium.com") -> "Medium"
            lower.contains("substack.com") -> "Substack"
            lower.contains("linkedin.com") -> "LinkedIn"
            lower.contains("threads.net") -> "Threads"
            lower.contains("wikipedia.org") -> "Wikipedia"
            lower.contains("nytimes.com") -> "The New York Times"
            lower.contains("theverge.com") -> "The Verge"
            else -> domain.removePrefix("www.")
        }
    }

    private fun calculateTimeAgo(createdAt: Long, now: Long): String {
        val diffMs = (now - createdAt).coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val days = TimeUnit.MILLISECONDS.toDays(diffMs)

        return when {
            hours < 24 -> "earlier today"
            days == 1L -> "yesterday"
            days in 2..6 -> "$days days ago"
            days in 7..13 -> "a week ago"
            days in 14..29 -> "a couple of weeks ago"
            else -> "a while ago"
        }
    }
}
