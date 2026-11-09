package com.laterbox.app.domain.util

import androidx.annotation.DrawableRes
import com.laterbox.app.R
import java.net.URI

data class PlatformBrand(
    val id: String,
    val displayName: String,
    @DrawableRes val iconRes: Int,
    val brandColorHex: String
)

object PlatformBrandResolver {

    val YOUTUBE = PlatformBrand(
        id = "youtube",
        displayName = "YouTube",
        iconRes = R.drawable.ic_platform_youtube,
        brandColorHex = "#FF0000"
    )

    val REDDIT = PlatformBrand(
        id = "reddit",
        displayName = "Reddit",
        iconRes = R.drawable.ic_platform_reddit,
        brandColorHex = "#FF4500"
    )

    val GITHUB = PlatformBrand(
        id = "github",
        displayName = "GitHub",
        iconRes = R.drawable.ic_platform_github,
        brandColorHex = "#24292E"
    )

    val X_TWITTER = PlatformBrand(
        id = "x",
        displayName = "X",
        iconRes = R.drawable.ic_platform_x,
        brandColorHex = "#000000"
    )

    fun resolve(domainOrUrl: String?): PlatformBrand? {
        if (domainOrUrl.isNullOrBlank()) return null
        val host = extractCleanHost(domainOrUrl) ?: return null

        return when {
            isYouTube(host) -> YOUTUBE
            isReddit(host) -> REDDIT
            isGitHub(host) -> GITHUB
            isXTwitter(host) -> X_TWITTER
            else -> null
        }
    }

    fun isYouTube(host: String): Boolean =
        matchesHost(host, "youtube.com") || host == "youtu.be"

    fun isReddit(host: String): Boolean =
        matchesHost(host, "reddit.com") || host == "redd.it"

    fun isGitHub(host: String): Boolean =
        matchesHost(host, "github.com")

    fun isXTwitter(host: String): Boolean =
        matchesHost(host, "twitter.com") || matchesHost(host, "x.com") || host == "t.co"

    private fun matchesHost(host: String, domain: String): Boolean {
        return host == domain || host.endsWith(".$domain")
    }

    fun extractCleanHost(domainOrUrl: String): String? {
        val trimmed = domainOrUrl.trim().lowercase()
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return try {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            host.removePrefix("www.").trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            val noScheme = trimmed.removePrefix("http://").removePrefix("https://")
            val slashIdx = noScheme.indexOf('/')
            val hostPart = if (slashIdx != -1) noScheme.substring(0, slashIdx) else noScheme
            hostPart.removePrefix("www.").trim().takeIf { it.isNotEmpty() }
        }
    }
}
