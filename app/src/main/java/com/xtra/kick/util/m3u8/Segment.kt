package com.xtra.kick.util.m3u8

/**
 * A byte range inside a (usually shared) resource. [start] is null while
 * parsing until it is resolved against the previous range of the same URI.
 */
data class ByteRange(
    val start: Long?,
    val length: Long,
)

data class Segment(
    val uri: String,
    val duration: Float,
    val title: String?,
    val programDateTime: String?,
    val byteRange: ByteRange? = null,
)