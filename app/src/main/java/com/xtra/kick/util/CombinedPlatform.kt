package com.xtra.kick.util

fun platformPrefIsCombined(platform: String?): Boolean = platform == C.PLATFORM_BOTH

fun <T> appendDeduplicated(
    seen: MutableSet<String>,
    target: MutableList<T>,
    source: List<T>,
    key: (T) -> String?,
    maxSize: Int,
) {
    for (item in source) {
        val k = key(item)
        if (k != null && seen.add(k)) {
            target.add(item)
            if (target.size >= maxSize) {
                return
            }
        }
    }
}