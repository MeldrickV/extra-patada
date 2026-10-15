package com.xtra.kick.util.m3u8

import java.io.InputStream
import java.io.OutputStream
import java.util.regex.Pattern

object PlaylistUtils {
    fun parseMediaPlaylist(input: InputStream): MediaPlaylist {
        var targetDuration = 10
        val dateRanges = mutableListOf<DateRange>()
        var programDateTime: String? = null
        var initSegmentUri: String? = null
        var mapByteRange: ByteRange? = null
        var keyMethod: String? = null
        val segments = mutableListOf<Segment>()
        var segmentInfo: Pair<Float, String?>? = null
        var pendingRange: ByteRange? = null
        val rangeEnds = mutableMapOf<String, Long>()
        var end = false
        input.bufferedReader().forEachLine { line ->
            if (line.isNotBlank()) {
                if (line.startsWith('#')) {
                    when {
                        line.startsWith("#EXT-X-TARGETDURATION") -> {
                            val matcher = Pattern.compile("#EXT-X-TARGETDURATION:(\\d+)\\b").matcher(line)
                            if (matcher.find()) {
                                matcher.group(1)?.toIntOrNull()?.let { targetDuration = it }
                            }
                        }
                        line.startsWith("#EXT-X-DATERANGE") -> {
                            val id = Pattern.compile("ID=\"(.+?)\"").matcher(line).let { if (it.find()) it.group(1) else null }
                            val startDate = Pattern.compile("START-DATE=\"(.+?)\"").matcher(line).let { if (it.find()) it.group(1) else null }
                            if (id != null && startDate != null) {
                                dateRanges.add(DateRange(
                                    id = id,
                                    rangeClass = Pattern.compile("CLASS=\"(.+?)\"").matcher(line).let { if (it.find()) it.group(1) else null },
                                    startDate = startDate,
                                    endDate = Pattern.compile("END-DATE=\"(.+?)\"").matcher(line).let { if (it.find()) it.group(1) else null },
                                    duration = Pattern.compile("DURATION=([\\d.]+)").matcher(line).let { if (it.find()) it.group(1)?.toFloatOrNull() else null },
                                    plannedDuration = Pattern.compile("PLANNED-DURATION=([\\d.]+)").matcher(line).let { if (it.find()) it.group(1)?.toFloatOrNull() else null },
                                    ad = Pattern.compile("X-TV-TWITCH-AD-.+?=\"(.+?)\"").matcher(line).let { if (it.find()) it.group(1) else null } != null
                                ))
                            }
                        }
                        line.startsWith("#EXT-X-PROGRAM-DATE-TIME") -> {
                            programDateTime = line.substringAfter("#EXT-X-PROGRAM-DATE-TIME:")
                        }
                        line.startsWith("#EXT-X-MAP") -> {
                            val matcher = Pattern.compile("URI=\"(.+?)\"").matcher(line)
                            if (matcher.find()) {
                                matcher.group(1)?.let { initSegmentUri = it }
                            }
                            val rangeMatcher = Pattern.compile("BYTERANGE=\"(\\d+)(?:@(\\d+))?\"").matcher(line)
                            if (rangeMatcher.find()) {
                                mapByteRange = ByteRange(
                                    start = rangeMatcher.group(2)?.toLongOrNull(),
                                    length = rangeMatcher.group(1)?.toLongOrNull() ?: 0L,
                                )
                            }
                        }
                        line.startsWith("#EXT-X-BYTERANGE") -> {
                            val matcher = Pattern.compile("#EXT-X-BYTERANGE:(\\d+)(?:@(\\d+))?").matcher(line)
                            if (matcher.find()) {
                                pendingRange = ByteRange(
                                    start = matcher.group(2)?.toLongOrNull(),
                                    length = matcher.group(1)?.toLongOrNull() ?: 0L,
                                )
                            }
                        }
                        line.startsWith("#EXT-X-KEY") -> {
                            val matcher = Pattern.compile("METHOD=([^,]+)").matcher(line)
                            if (matcher.find()) {
                                keyMethod = matcher.group(1)?.trim()
                            }
                        }
                        line.startsWith("#EXTINF") -> {
                            val durationMatcher = Pattern.compile("#EXTINF:([\\d.]+)\\b").matcher(line)
                            if (durationMatcher.find()) {
                                durationMatcher.group(1)?.toFloatOrNull()?.let { duration ->
                                    val titleMatcher = Pattern.compile("#EXTINF:[\\d.]+\\b,(.+)").matcher(line)
                                    val title = if (titleMatcher.find()) {
                                        titleMatcher.group(1)
                                    } else null
                                    segmentInfo = Pair(duration, title)
                                }
                            }
                        }
                        line.startsWith("#EXT-X-ENDLIST") -> {
                            end = true
                        }
                    }
                } else {
                    segmentInfo?.let {
                        val range = pendingRange?.let { pending ->
                            val resolved = pending.start ?: rangeEnds[line] ?: 0L
                            rangeEnds[line] = resolved + pending.length
                            ByteRange(resolved, pending.length)
                        }
                        pendingRange = null
                        segments.add(Segment(line, it.first, it.second, programDateTime, range))
                        segmentInfo = null
                    }
                }
            }
        }
        return MediaPlaylist(targetDuration, dateRanges, initSegmentUri, segments, end, keyMethod, mapByteRange)
    }

    fun writeMediaPlaylist(playlist: MediaPlaylist, output: OutputStream, endList: Boolean = true) {
        output.bufferedWriter().use { writer ->
            writer.write("#EXTM3U")
            writer.newLine()
            writer.write("#EXT-X-VERSION:${if (playlist.initSegmentUri != null) 6 else 3}")
            writer.newLine()
            writer.write("#EXT-X-PLAYLIST-TYPE:EVENT")
            writer.newLine()
            writer.write("#EXT-X-TARGETDURATION:${playlist.targetDuration}")
            writer.newLine()
            writer.write("#EXT-X-MEDIA-SEQUENCE:0")
            if (playlist.initSegmentUri != null) {
                writer.newLine()
                val mapLine = buildString {
                    append("#EXT-X-MAP:URI=\"${playlist.initSegmentUri}\"")
                    playlist.mapByteRange?.let { range ->
                        append(",BYTERANGE=\"${range.length}")
                        range.start?.let { append("@$it") }
                        append("\"")
                    }
                }
                writer.write(mapLine)
            }
            playlist.segments.forEach {
                it.byteRange?.let { range ->
                    writer.newLine()
                    writer.write(buildString {
                        append("#EXT-X-BYTERANGE:${range.length}")
                        range.start?.let { append("@$it") }
                    })
                }
                writer.newLine()
                writer.write("#EXTINF:${it.duration}")
                writer.newLine()
                writer.write(it.uri)
            }
            if (endList) {
                writer.newLine()
                writer.write("#EXT-X-ENDLIST")
            }
        }
    }

    /**
     * Sibling partial playlist for in-progress per-file downloads: players
     * open it to watch what's contiguously available while the rest arrives.
     * Returns null when [playlistUrl] is not a local m3u8.
     */
    fun partialPlaylistUriFor(playlistUrl: String): String? {
        if (playlistUrl.startsWith("http://") || playlistUrl.startsWith("https://")) return null
        val base = playlistUrl.substringBefore('?')
        if (!base.endsWith(".m3u8")) return null
        return if (base.contains("%2F")) {
            base.substringBeforeLast("%2F") + "%2Fpartial.m3u8"
        } else {
            base.substringBeforeLast('/') + "/partial.m3u8"
        }
    }
}