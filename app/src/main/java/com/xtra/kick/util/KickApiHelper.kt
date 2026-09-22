package com.xtra.kick.util

object KickApiHelper {

    const val API_BASE_URL = "https://api.kick.com"
    const val WEBSITE_BASE_URL = "https://kick.com"

    const val PRIVATE_LIVESTREAMS_URL = "https://api.kick.com/private/v1/livestreams"
    const val PRIVATE_CATEGORIES_URL = "https://api.kick.com/private/v1/categories"

    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun channelUrl(slug: String): String {
        return "$WEBSITE_BASE_URL/api/v2/channels/$slug"
    }

    fun getBrowserHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Accept" to "application/json",
        )
    }
}