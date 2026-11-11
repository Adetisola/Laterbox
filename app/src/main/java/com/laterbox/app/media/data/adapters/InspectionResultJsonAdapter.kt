package com.laterbox.app.media.data.adapters

import com.laterbox.app.media.domain.model.AcquisitionSource
import com.laterbox.app.media.domain.model.FailureCode
import com.laterbox.app.media.domain.model.InspectionResult
import com.laterbox.app.media.domain.model.MatchConfidence
import com.laterbox.app.media.domain.model.MediaCandidate
import com.laterbox.app.media.domain.model.MediaType
import com.laterbox.app.media.domain.model.MusicMatchCandidate
import org.json.JSONArray
import org.json.JSONObject

object InspectionResultJsonAdapter {

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

                val candidatesArr = JSONArray()
                for (c in result.candidates) {
                    val cObj = JSONObject()
                    cObj.put("candidateId", c.candidateId)
                    cObj.put("mediaType", c.mediaType.name)
                    cObj.put("formatLabel", c.formatLabel)
                    cObj.put("estimatedBytes", c.estimatedBytes ?: JSONObject.NULL)
                    cObj.put("durationMs", c.durationMs ?: JSONObject.NULL)
                    cObj.put("width", c.width ?: JSONObject.NULL)
                    cObj.put("height", c.height ?: JSONObject.NULL)
                    cObj.put("isOgFallback", c.isOgFallback)

                    val srcObj = JSONObject()
                    when (val s = c.source) {
                        is AcquisitionSource.DirectHttp -> {
                            srcObj.put("type", "DirectHttp")
                            srcObj.put("url", s.url)
                        }
                        is AcquisitionSource.YtDlp -> {
                            srcObj.put("type", "YtDlp")
                            srcObj.put("url", s.url)
                            srcObj.put("formatSelector", s.formatSelector)
                            srcObj.put("expectedExt", s.expectedExt)
                        }
                    }
                    cObj.put("source", srcObj)
                    candidatesArr.put(cObj)
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

                    val dc = m.downloadCandidate
                    val dcObj = JSONObject().apply {
                        put("candidateId", dc.candidateId)
                        put("mediaType", dc.mediaType.name)
                        put("formatLabel", dc.formatLabel)
                        put("estimatedBytes", dc.estimatedBytes ?: JSONObject.NULL)
                        put("durationMs", dc.durationMs ?: JSONObject.NULL)
                        put("width", dc.width ?: JSONObject.NULL)
                        put("height", dc.height ?: JSONObject.NULL)
                        put("isOgFallback", dc.isOgFallback)
                        val sObj = JSONObject().apply {
                            when (val src = dc.source) {
                                is AcquisitionSource.DirectHttp -> {
                                    put("type", "DirectHttp")
                                    put("url", src.url)
                                }
                                is AcquisitionSource.YtDlp -> {
                                    put("type", "YtDlp")
                                    put("url", src.url)
                                    put("formatSelector", src.formatSelector)
                                    put("expectedExt", src.expectedExt)
                                }
                            }
                        }
                        put("source", sObj)
                    }
                    mObj.put("downloadCandidate", dcObj)
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
            }

            is InspectionResult.Unsupported -> {
                root.put("type", "Unsupported")
                root.put("reason", result.reason)
            }

            is InspectionResult.Failed -> {
                root.put("type", "Failed")
                root.put("code", result.code.name)
                root.put("message", result.message ?: JSONObject.NULL)
                root.put("retryable", result.retryable)
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

                    val candidates = mutableListOf<MediaCandidate>()
                    val cArr = root.optJSONArray("candidates") ?: JSONArray()
                    for (i in 0 until cArr.length()) {
                        val cObj = cArr.optJSONObject(i) ?: continue
                        val cId = cObj.optString("candidateId")
                        val mType = try { MediaType.valueOf(cObj.optString("mediaType")) } catch (_: Exception) { MediaType.OTHER }
                        val label = cObj.optString("formatLabel")
                        val bytes = if (cObj.isNull("estimatedBytes")) null else cObj.optLong("estimatedBytes")
                        val dur = if (cObj.isNull("durationMs")) null else cObj.optLong("durationMs")
                        val w = if (cObj.isNull("width")) null else cObj.optInt("width")
                        val h = if (cObj.isNull("height")) null else cObj.optInt("height")
                        val isOg = cObj.optBoolean("isOgFallback", false)

                        val sObj = cObj.optJSONObject("source") ?: continue
                        val src = if (sObj.optString("type") == "DirectHttp") {
                            AcquisitionSource.DirectHttp(url = sObj.optString("url"))
                        } else {
                            AcquisitionSource.YtDlp(
                                url = sObj.optString("url"),
                                formatSelector = sObj.optString("formatSelector"),
                                expectedExt = sObj.optString("expectedExt", "mp4")
                            )
                        }

                        candidates.add(
                            MediaCandidate(
                                candidateId = cId,
                                mediaType = mType,
                                formatLabel = label,
                                estimatedBytes = bytes,
                                durationMs = dur,
                                width = w,
                                height = h,
                                source = src,
                                isOgFallback = isOg
                            )
                        )
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
                        val dcId = dcObj.optString("candidateId")
                        val dcType = try { MediaType.valueOf(dcObj.optString("mediaType")) } catch (_: Exception) { MediaType.AUDIO }
                        val dcLabel = dcObj.optString("formatLabel")
                        val dcBytes = if (dcObj.isNull("estimatedBytes")) null else dcObj.optLong("estimatedBytes")
                        val dcDuration = if (dcObj.isNull("durationMs")) null else dcObj.optLong("durationMs")
                        val dcW = if (dcObj.isNull("width")) null else dcObj.optInt("width")
                        val dcH = if (dcObj.isNull("height")) null else dcObj.optInt("height")
                        val dcIsOg = dcObj.optBoolean("isOgFallback", false)
                        val dcSourceObj = dcObj.optJSONObject("source") ?: continue
                        val dcSource = if (dcSourceObj.optString("type") == "DirectHttp") {
                            AcquisitionSource.DirectHttp(url = dcSourceObj.optString("url"))
                        } else {
                            AcquisitionSource.YtDlp(
                                url = dcSourceObj.optString("url"),
                                formatSelector = dcSourceObj.optString("formatSelector"),
                                expectedExt = dcSourceObj.optString("expectedExt", "m4a")
                            )
                        }
                        val dc = MediaCandidate(
                            candidateId = dcId,
                            mediaType = dcType,
                            formatLabel = dcLabel,
                            estimatedBytes = dcBytes,
                            durationMs = dcDuration,
                            width = dcW,
                            height = dcH,
                            source = dcSource,
                            isOgFallback = dcIsOg
                        )

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

                    InspectionResult.Available(
                        platform = platform,
                        title = title,
                        thumbnailUrl = thumb,
                        candidates = candidates,
                        artist = artist,
                        album = album,
                        musicMatches = matches,
                        spotifyTrackId = spotifyId
                    )
                }

                "MetadataOnly" -> {
                    val title = if (root.isNull("title")) null else root.optString("title").takeIf { it.isNotBlank() }
                    val artist = if (root.isNull("artist")) null else root.optString("artist").takeIf { it.isNotBlank() }
                    val artwork = if (root.isNull("artworkUrl")) null else root.optString("artworkUrl").takeIf { it.isNotBlank() }
                    InspectionResult.MetadataOnly(title, artist, artwork)
                }

                "NeedsAuth" -> {
                    InspectionResult.NeedsAuth(reason = root.optString("reason"))
                }

                "Unsupported" -> {
                    InspectionResult.Unsupported(reason = root.optString("reason"))
                }

                "Failed" -> {
                    val code = try { FailureCode.valueOf(root.optString("code")) } catch (_: Exception) { FailureCode.UNKNOWN }
                    val msg = if (root.isNull("message")) null else root.optString("message").takeIf { it.isNotBlank() }
                    val retryable = root.optBoolean("retryable", code.isRetryable)
                    InspectionResult.Failed(code = code, message = msg, retryable = retryable)
                }

                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
