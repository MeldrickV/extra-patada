package com.xtra.kick.util.m3u8

data class MediaPlaylist(
    val targetDuration: Int,
    val dateRanges: List<DateRange>,
    val initSegmentUri: String?,
    val segments: List<Segment>,
    val end: Boolean
)