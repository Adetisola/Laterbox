package com.laterbox.app.media.data.adapters

import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.CandidateStatus
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.model.MusicMatchCandidate
import org.json.JSONArray
import org.json.JSONObject

object InspectionResultJsonAdapter {

    fun candidateToJson(c: MediaCandidate): JSONObject {
        val cObj = JSONObject()
        cObj.put("candidateId", c.candidateId)
        cObj.put("mediaType", c.mediaType.name)
        cObj.put("formatLabel", c.formatLabel)
        cObj.put("estimatedBytes", c.estimatedBytes ?: JSONObject.NULL)
        cObj.put("durationMs", c.durationMs ?: JSONObject.NULL)
        cObj.put("width", c.width ?: JSONObject.NULL)
        cObj.put("height", c.height ?: JSONObject.NULL)
        cObj.put("isOgFallback", c.isOgFallback)
        cObj.put("formatId", c.formatId ?: JSONObject.NULL)
        cObj.put("ext", c.ext ?: JSONObject.NULL)
        cObj.put("vcodec", c.vcodec ?: JSONObject.NULL)
        cObj.put("acodec", c.acodec ?: JSONObject.NULL)
        cObj.put("fps", c.fps ?: JSONObject.NULL)
        cObj.put("bitrate", c.bitrate ?: JSONObject.NULL)
        cObj.put("hasVideo", c.hasVideo)
        cObj.put("hasAudio", c.hasAudio)
        cObj.put("isBest", c.isBest)
        cObj.put("isThumbnail", c.isThumbnail)
        cObj.put("qualityLabel", c.qualityLabel ?: JSONObject.NULL)
        cObj.put("requiresMerge", c.requiresMerge)
        cObj.put("audioTrackMissing", c.audioTrackMissing)
        cObj.put("status", c.status.name)
        cObj.put("statusReason", c.statusReason ?: JSONObject.NULL)
        cObj.put("extractor", c.extractor ?: JSONObject.NULL)

        val srcObj = JSONObject()
        when (val s = c.source) {
            is AcquisitionSource.DirectHttp -> {
                srcObj.put("type", "DirectHttp")
                srcObj.put("url", s.url)
                if (s.headers.isNotEmpty()) {
                    val hObj = JSONObject()
                    s.headers.forEach { (k, v) -> hObj.put(k, v) }
                    srcObj.put("headers", hObj)
                }
                if (s.mimeType != null) {
                    srcObj.put("mimeType", s.mimeType)
                }
            }
            is AcquisitionSource.YtDlp -> {
                srcObj.put("type", "YtDlp")
                srcObj.put("url", s.url)
                srcObj.put("formatSelector", s.formatSelector)
                srcObj.put("expectedExt", s.expectedExt)
            }
        }
        cObj.put("source", srcObj)
        return cObj
    }

    fun candidateFromJson(cObj: JSONObject): MediaCandidate? {
        val cId = cObj.optString("candidateId").takeIf { it.isNotBlank() } ?: return null
        val mType = try { MediaType.valueOf(cObj.optString("mediaType")) } catch (_: Exception) { MediaType.OTHER }
        val label = cObj.optString("formatLabel", "")
        val bytes = if (cObj.isNull("estimatedBytes")) null else cObj.optLong("estimatedBytes")
        val dur = if (cObj.isNull("durationMs")) null else cObj.optLong("durationMs")
        val w = if (cObj.isNull("width")) null else cObj.optInt("width")
        val h = if (cObj.isNull("height")) null else cObj.optInt("height")
        val isOg = cObj.optBoolean("isOgFallback", false)

        val formatId = if (cObj.isNull("formatId")) null else cObj.optString("formatId").takeIf { it.isNotBlank() }
        val ext = if (cObj.isNull("ext")) null else cObj.optString("ext").takeIf { it.isNotBlank() }
        val vcodec = if (cObj.isNull("vcodec")) null else cObj.optString("vcodec").takeIf { it.isNotBlank() }
        val acodec = if (cObj.isNull("acodec")) null else cObj.optString("acodec").takeIf { it.isNotBlank() }
        val fps = if (cObj.isNull("fps")) null else cObj.optDouble("fps")
        val bitrate = if (cObj.isNull("bitrate")) null else cObj.optDouble("bitrate")
        val hasVideo = cObj.optBoolean("hasVideo", mType == MediaType.VIDEO)
        val hasAudio = cObj.optBoolean("hasAudio", mType == MediaType.AUDIO || (mType == MediaType.VIDEO && acodec != null))
        val isBest = cObj.optBoolean("isBest", false)
        val isThumbnail = cObj.optBoolean("isThumbnail", false)
        val qualityLabel = if (cObj.isNull("qualityLabel")) null else cObj.optString("qualityLabel").takeIf { it.isNotBlank() }
        val requiresMerge = cObj.optBoolean("requiresMerge", false)
        val audioTrackMissing = cObj.optBoolean("audioTrackMissing", false)
        val status = try {
            CandidateStatus.valueOf(cObj.optString("status", CandidateStatus.AVAILABLE.name))
        } catch (_: Exception) {
            CandidateStatus.AVAILABLE
        }
        val statusReason = if (cObj.isNull("statusReason")) null else cObj.optString("statusReason").takeIf { it.isNotBlank() }
        val extractor = if (cObj.isNull("extractor")) null else cObj.optString("extractor").takeIf { it.isNotBlank() }

        val sObj = cObj.optJSONObject("source") ?: return null
        val src = if (sObj.optString("type") == "DirectHttp") {
            val hObj = sObj.optJSONObject("headers")
            val headers = mutableMapOf<String, String>()
            if (hObj != null) {
                for (k in hObj.keys()) {
                    headers[k] = hObj.optString(k)
                }
            }
            val mimeType = if (sObj.isNull("mimeType")) null else sObj.optString("mimeType").takeIf { it.isNotBlank() }
            AcquisitionSource.DirectHttp(url = sObj.optString("url"), headers = headers, mimeType = mimeType)
        } else {
            AcquisitionSource.YtDlp(
                url = sObj.optString("url"),
                formatSelector = sObj.optString("formatSelector"),
                expectedExt = sObj.optString("expectedExt", "mp4")
            )
        }

        return MediaCandidate(
            candidateId = cId,
            mediaType = mType,
            formatLabel = label,
            estimatedBytes = bytes,
            durationMs = dur,
            width = w,
            height = h,
            source = src,
            isOgFallback = isOg,
            formatId = formatId,
            ext = ext,
            vcodec = vcodec,
            acodec = acodec,
            fps = fps,
            bitrate = bitrate,
            hasVideo = hasVideo,
            hasAudio = hasAudio,
            isBest = isBest,
            isThumbnail = isThumbnail,
            qualityLabel = qualityLabel,
            requiresMerge = requiresMerge,
            audioTrackMissing = audioTrackMissing,
            status = status,
            statusReason = statusReason,
            extractor = extractor
        )
    }

    fun toJson(result: InspectionResult): String {
        val root = JSONObject()
        when (result) {
            is InspectionResult.Available -> {
                root.put("type", "Available")
                root.put("platform", result.platform)
                root.put("title", result.title ?: JSONObject.NULL)
                root.put("thumbnailUrl", result.thumbnailUrl ?: JSONObject.NULL)
                root.put("artist", result.artist ?: JSONObject.NULL)
                root.put("album", result.album ?: JSONObject.NULL)
                root.put("spotifyTrackId", result.spotifyTrackId ?: JSONObject.NULL)
                root.put("extractorVersion", result.extractorVersion ?: JSONObject.NULL)

                root.put("isComplete", result.isComplete)
                val failuresArr = JSONArray()
                for (failure in result.partialFailures) {
                    failuresArr.put(failure)
                }
                root.put("partialFailures", failuresArr)

                val candidatesArr = JSONArray()
                for (c in result.candidates) {
                    candidatesArr.put(candidateToJson(c))
                }
                root.put("candidates", candidatesArr)

                val matchesArr = JSONArray()
                for (m in result.musicMatches) {
                    val mObj = JSONObject()
                    mObj.put("candidateId", m.candidateId)
                    mObj.put("title", m.title)
                    mObj.put("artist", m.artist)
                    mObj.put("durationMs", m.durationMs ?: JSONObject.NULL)
                    mObj.put("score", m.score)
                    mObj.put("confidence", m.confidence.name)
                    mObj.put("versionTag", m.versionTag ?: JSONObject.NULL)
                    mObj.put("sourceDescription", m.sourceDescription)
                    mObj.put("downloadCandidate", candidateToJson(m.downloadCandidate))
                    matchesArr.put(mObj)
                }
                root.put("musicMatches", matchesArr)
            }

            is InspectionResult.MetadataOnly -> {
                root.put("type", "MetadataOnly")
                root.put("title", result.title ?: JSONObject.NULL)
                root.put("artist", result.artist ?: JSONObject.NULL)
                root.put("artworkUrl", result.artworkUrl ?: JSONObject.NULL)
            }

            is InspectionResult.NeedsAuth -> {
                root.put("type", "NeedsAuth")
                root.put("reason", result.reason)
                result.previewCandidate?.let {
                    root.put("previewCandidate", candidateToJson(it))
                }
            }

            is InspectionResult.Unsupported -> {
                root.put("type", "Unsupported")
                root.put("reason", result.reason)
                result.previewCandidate?.let {
                    root.put("previewCandidate", candidateToJson(it))
                }
            }

            is InspectionResult.Failed -> {
                root.put("type", "Failed")
                root.put("code", result.code.name)
                root.put("message", result.message ?: JSONObject.NULL)
                root.put("retryable", result.retryable)
                result.previewCandidate?.let {
                    root.put("previewCandidate", candidateToJson(it))
                }
            }
        }
        return root.toString()
    }

    fun fromJson(jsonStr: String): InspectionResult? {
        return try {
            val root = JSONObject(jsonStr)
            when (root.optString("type")) {
                "Available" -> {
                    val platform = root.optString("platform", "Unknown")
                    val title = if (root.isNull("title")) null else root.optString("title").takeIf { it.isNotBlank() }
                    val thumb = if (root.isNull("thumbnailUrl")) null else root.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                    val artist = if (root.isNull("artist")) null else root.optString("artist").takeIf { it.isNotBlank() }
                    val album = if (root.isNull("album")) null else root.optString("album").takeIf { it.isNotBlank() }
                    val spotifyId = if (root.isNull("spotifyTrackId")) null else root.optString("spotifyTrackId").takeIf { it.isNotBlank() }
                    val extractorVersion = if (root.isNull("extractorVersion")) null else root.optString("extractorVersion").takeIf { it.isNotBlank() }

                    val partialFailures = mutableListOf<String>()
                    val fArr = root.optJSONArray("partialFailures")
                    if (fArr != null) {
                        for (i in 0 until fArr.length()) {
                            val fStr = fArr.optString(i)
                            if (fStr.isNotBlank()) {
                                partialFailures.add(fStr)
                            }
                        }
                    }

                    val candidates = mutableListOf<MediaCandidate>()
                    val cArr = root.optJSONArray("candidates") ?: JSONArray()
                    for (i in 0 until cArr.length()) {
                        val cObj = cArr.optJSONObject(i) ?: continue
                        val candidate = candidateFromJson(cObj) ?: continue
                        candidates.add(candidate)
                    }

                    val matches = mutableListOf<MusicMatchCandidate>()
                    val mArr = root.optJSONArray("musicMatches") ?: JSONArray()
                    for (i in 0 until mArr.length()) {
                        val mObj = mArr.optJSONObject(i) ?: continue
                        val mId = mObj.optString("candidateId")
                        val mTitle = mObj.optString("title")
                        val mArtist = mObj.optString("artist")
                        val mDur = if (mObj.isNull("durationMs")) null else mObj.optLong("durationMs")
                        val score = mObj.optDouble("score", 0.0)
                        val conf = try { MatchConfidence.valueOf(mObj.optString("confidence")) } catch (_: Exception) { MatchConfidence.WEAK }
                        val vTag = if (mObj.isNull("versionTag")) null else mObj.optString("versionTag")
                        val sDesc = mObj.optString("sourceDescription", "Authorized Audio Source")

                        val dcObj = mObj.optJSONObject("downloadCandidate") ?: continue
                        val dc = candidateFromJson(dcObj) ?: continue

                        matches.add(
                            MusicMatchCandidate(
                                candidateId = mId,
                                title = mTitle,
                                artist = mArtist,
                                durationMs = mDur,
                                score = score,
                                confidence = conf,
                                versionTag = vTag,
                                downloadCandidate = dc,
                                sourceDescription = sDesc
                            )
                        )
                    }

                    val isComplete = root.optBoolean("isComplete", true)

                    InspectionResult.Available(
                        platform = platform,
                        title = title,
                        thumbnailUrl = thumb,
                        candidates = candidates,
                        artist = artist,
                        album = album,
                        musicMatches = matches,
                        spotifyTrackId = spotifyId,
                        partialFailures = partialFailures,
                        extractorVersion = extractorVersion,
                        isComplete = isComplete
                    )
                }

                "MetadataOnly" -> {
                    val title = if (root.isNull("title")) null else root.optString("title").takeIf { it.isNotBlank() }
                    val artist = if (root.isNull("artist")) null else root.optString("artist").takeIf { it.isNotBlank() }
                    val artwork = if (root.isNull("artworkUrl")) null else root.optString("artworkUrl").takeIf { it.isNotBlank() }
                    InspectionResult.MetadataOnly(title, artist, artwork)
                }

                "NeedsAuth" -> {
                    val previewCandidate = root.optJSONObject("previewCandidate")?.let { candidateFromJson(it) }
                    InspectionResult.NeedsAuth(
                        reason = root.optString("reason"),
                        previewCandidate = previewCandidate
                    )
                }

                "Unsupported" -> {
                    val previewCandidate = root.optJSONObject("previewCandidate")?.let { candidateFromJson(it) }
                    InspectionResult.Unsupported(
                        reason = root.optString("reason"),
                        previewCandidate = previewCandidate
                    )
                }

                "Failed" -> {
                    val code = try { FailureCode.valueOf(root.optString("code")) } catch (_: Exception) { FailureCode.UNKNOWN }
                    val msg = if (root.isNull("message")) null else root.optString("message").takeIf { it.isNotBlank() }
                    val retryable = root.optBoolean("retryable", code.isRetryable)
                    val previewCandidate = root.optJSONObject("previewCandidate")?.let { candidateFromJson(it) }
                    InspectionResult.Failed(
                        code = code,
                        message = msg,
                        retryable = retryable,
                        previewCandidate = previewCandidate
                    )
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
