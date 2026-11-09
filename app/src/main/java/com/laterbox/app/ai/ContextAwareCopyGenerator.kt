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
        val cleanTitle = link.title.take(24).trim().removeSuffix("-").trim()
        val namePrefix = if (!userName.isNullOrBlank()) "$userName, " else ""

        return if (origin == ReminderOrigin.MANUAL) {
            when (Math.abs((link.id % 3).toInt())) {
                0 -> "Ready for $cleanTitle?"
                1 -> "${namePrefix}scheduled link ready"
                else -> "Revisiting $cleanTitle"
            }
        } else {
            when (link.contentType) {
                ContentType.ARTICLE -> "Ready for this read?"
                ContentType.VIDEO -> "Watch when you have a moment"
                ContentType.DISCUSSION -> "Catch up on this thread"
                ContentType.PRODUCT -> "Saved item in your inbox"
                ContentType.WEBSITE -> "${namePrefix}your link is waiting"
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
        val nameGreet = if (!userName.isNullOrBlank()) "Hey $userName, " else ""
        val cleanTitle = link.title.take(45).trim()

        if (hasNote) {
            val note = link.personalNote!!.trim()
            val lowerNote = note.lowercase()
            return when {
                lowerNote.contains("install") || lowerNote.contains("app") -> {
                    "${nameGreet}here is $cleanTitle — ready to test it out and see if you want to install it?"
                }
                lowerNote.contains("read") || lowerNote.contains("article") -> {
                    "${nameGreet}ready to dive into $cleanTitle from $platform?"
                }
                lowerNote.contains("watch") || lowerNote.contains("video") -> {
                    "${nameGreet}here's that video on $cleanTitle you wanted to watch."
                }
                lowerNote.contains("buy") || lowerNote.contains("price") || lowerNote.contains("shop") -> {
                    "${nameGreet}checking back in on $cleanTitle from $platform like you planned."
                }
                else -> {
                    val cleanNoteSummary = note.removePrefix("I want to ")
                        .removePrefix("Remember to ")
                        .removePrefix("Check out ")
                        .removePrefix("check out ")
                    "${nameGreet}here is $cleanTitle to $cleanNoteSummary."
                }
            }
        }

        if (origin == ReminderOrigin.MANUAL) {
            return "${nameGreet}here is $cleanTitle from $platform that you asked to revisit."
        }

        val greeting = if (!userName.isNullOrBlank()) "Hey $userName, seems" else "Seems"

        return when {
            platform.contains("Instagram", ignoreCase = true) || platform.contains("TikTok", ignoreCase = true) -> {
                "$greeting like you haven't checked the $platform post you saved $timeAgo."
            }
            link.contentType == ContentType.VIDEO || platform.contains("YouTube", ignoreCase = true) -> {
                "$greeting like you haven't watched the video from $platform you saved $timeAgo."
            }
            link.contentType == ContentType.ARTICLE -> {
                "$greeting like you haven't finished reading $cleanTitle from $platform."
            }
            link.contentType == ContentType.DISCUSSION -> {
                "$greeting like there's an unread discussion on $platform you saved $timeAgo."
            }
            else -> {
                "$greeting like you haven't checked $cleanTitle from $platform you saved $timeAgo."
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
