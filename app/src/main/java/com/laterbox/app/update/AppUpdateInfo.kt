package com.laterbox.app.update

/**
 * Encapsulates metadata retrieved from a public GitHub release.
 */
data class AppUpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val apkSize: Long = 0L,
    val isNewer: Boolean = false
) {
    companion object {
        fun cleanVersion(raw: String): String {
            return raw.trim()
                .removePrefix("Letterbox version ")
                .removePrefix("Laterbox version ")
                .removePrefix("Version ")
                .removePrefix("version ")
                .removePrefix("v")
                .removePrefix("V")
                .trim()
        }

        fun isNewerVersion(remoteRaw: String, currentRaw: String): Boolean {
            val remote = cleanVersion(remoteRaw)
            val current = cleanVersion(currentRaw)
            if (remote == current || remote.isBlank()) return false

            val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }
}
