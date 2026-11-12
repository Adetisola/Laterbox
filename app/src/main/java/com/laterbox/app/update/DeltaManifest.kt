package com.laterbox.app.update

import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject

/**
 * Metadata describing a differential binary patch between a base release APK and a target release APK.
 */
@Keep
data class DeltaPatchInfo(
    val architecture: String,
    val architectureLabel: String,
    val baseVersion: String,
    val baseVersionCode: Long,
    val baseApkSha256: String,
    val targetApkFilename: String,
    val targetApkSha256: String,
    val targetApkSize: Long,
    val patchFilename: String,
    val patchSha256: String,
    val patchSize: Long,
    val patchAlgorithm: String = "BSDIFF40",
    val downloadUrl: String = ""
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("architecture", architecture)
            put("architectureLabel", architectureLabel)
            put("baseVersion", baseVersion)
            put("baseVersionCode", baseVersionCode)
            put("baseApkSha256", baseApkSha256.lowercase())
            put("targetApkFilename", targetApkFilename)
            put("targetApkSha256", targetApkSha256.lowercase())
            put("targetApkSize", targetApkSize)
            put("patchFilename", patchFilename)
            put("patchSha256", patchSha256.lowercase())
            put("patchSize", patchSize)
            put("patchAlgorithm", patchAlgorithm)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject, downloadUrl: String = ""): DeltaPatchInfo? {
            val arch = obj.optString("architecture").trim()
            val baseVer = obj.optString("baseVersion").trim()
            val baseCode = obj.optLong("baseVersionCode", -1L)
            val baseSha = obj.optString("baseApkSha256").trim().lowercase()
            val targetName = obj.optString("targetApkFilename").trim()
            val targetSha = obj.optString("targetApkSha256").trim().lowercase()
            val targetSize = obj.optLong("targetApkSize", 0L)
            val patchName = obj.optString("patchFilename").trim()
            val patchSha = obj.optString("patchSha256").trim().lowercase()
            val patchSize = obj.optLong("patchSize", 0L)
            val algorithm = obj.optString("patchAlgorithm", "BSDIFF40").trim()
            val label = obj.optString("architectureLabel").ifBlank { arch }

            if (arch.isBlank() || baseVer.isBlank() || baseSha.isBlank() ||
                targetName.isBlank() || targetSha.isBlank() ||
                patchName.isBlank() || patchSha.isBlank() || patchSize <= 0L
            ) {
                return null
            }

            return DeltaPatchInfo(
                architecture = arch,
                architectureLabel = label,
                baseVersion = baseVer,
                baseVersionCode = baseCode,
                baseApkSha256 = baseSha,
                targetApkFilename = targetName,
                targetApkSha256 = targetSha,
                targetApkSize = targetSize,
                patchFilename = patchName,
                patchSha256 = patchSha,
                patchSize = patchSize,
                patchAlgorithm = algorithm,
                downloadUrl = downloadUrl
            )
        }
    }
}

/**
 * Top-level delta update manifest published alongside release APKs in GitHub Releases.
 */
@Keep
data class DeltaManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val targetVersion: String,
    val targetVersionCode: Long,
    val generatedAt: Long = System.currentTimeMillis(),
    val patches: List<DeltaPatchInfo> = emptyList()
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("targetVersion", targetVersion)
            put("targetVersionCode", targetVersionCode)
            put("generatedAt", generatedAt)
            val patchArray = JSONArray()
            for (patch in patches) {
                patchArray.put(patch.toJsonObject())
            }
            put("patches", patchArray)
        }
        return root.toString(2)
    }

    /**
     * Finds a compatible patch matching the installed APK's cryptographic SHA-256 hash
     * and target device architecture.
     *
     * Strict matching requirements:
     * 1. The installed APK SHA-256 must byte-for-byte equal [installedApkSha256].
     * 2. The patch algorithm must be supported ("BSDIFF40").
     * 3. The patch architecture must be compatible with [deviceSupportedAbis].
     */
    fun findCompatiblePatch(
        installedApkSha256: String,
        deviceSupportedAbis: Array<String>
    ): DeltaPatchInfo? {
        if (installedApkSha256.isBlank()) return null
        val normalizedInstalledSha = installedApkSha256.trim().lowercase()

        // Filter patches that match base APK hash and supported algorithm
        val hashMatching = patches.filter { patch ->
            patch.patchAlgorithm.equals("BSDIFF40", ignoreCase = true) &&
                patch.baseApkSha256.trim().lowercase() == normalizedInstalledSha
        }

        if (hashMatching.isEmpty()) return null

        val is64BitSupported = deviceSupportedAbis.any { it.equals("arm64-v8a", ignoreCase = true) }
        val isArm32Supported = deviceSupportedAbis.any { it.startsWith("armeabi", ignoreCase = true) }

        // Prefer exact ABI match
        return when {
            is64BitSupported -> {
                hashMatching.firstOrNull { it.architecture.equals("arm64-v8a", ignoreCase = true) }
                    ?: hashMatching.firstOrNull { it.architecture.equals("universal", ignoreCase = true) }
            }
            !is64BitSupported && isArm32Supported -> {
                hashMatching.firstOrNull {
                    it.architecture.equals("armeabi-v7a", ignoreCase = true) || it.architecture.equals("arm32", ignoreCase = true)
                } ?: hashMatching.firstOrNull { it.architecture.equals("universal", ignoreCase = true) }
            }
            else -> {
                hashMatching.firstOrNull { it.architecture.equals("universal", ignoreCase = true) }
            }
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MANIFEST_FILENAME = "delta-manifest.json"

        fun fromJson(jsonStr: String): DeltaManifest? {
            return try {
                val root = JSONObject(jsonStr)
                val schemaVer = root.optInt("schemaVersion", 1)
                if (schemaVer > CURRENT_SCHEMA_VERSION) {
                    // Fail closed on unsupported future schema version
                    return null
                }
                val targetVer = root.optString("targetVersion").trim()
                val targetCode = root.optLong("targetVersionCode", 0L)
                val generatedAt = root.optLong("generatedAt", 0L)

                val patchArray = root.optJSONArray("patches") ?: JSONArray()
                val patchList = mutableListOf<DeltaPatchInfo>()

                for (i in 0 until patchArray.length()) {
                    val pObj = patchArray.optJSONObject(i) ?: continue
                    val patch = DeltaPatchInfo.fromJsonObject(pObj)
                    if (patch != null) {
                        patchList.add(patch)
                    }
                }

                if (targetVer.isBlank()) null
                else DeltaManifest(
                    schemaVersion = schemaVer,
                    targetVersion = targetVer,
                    targetVersionCode = targetCode,
                    generatedAt = generatedAt,
                    patches = patchList
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
