package com.xtra.kick.util

object KickApiHelper {

    const val API_BASE_URL = "https://api.kick.com"
    const val WEBSITE_BASE_URL = "https://kick.com"
    const val REALTIME_BASE_URL = "https://web.kick.com/api/v1/realtime"
    const val OAUTH_BASE_URL = "https://id.kick.com"
    const val PUBLIC_API_BASE_URL = "https://api.kick.com/public/v1"

    const val PRIVATE_LIVESTREAMS_URL = "https://api.kick.com/private/v1/livestreams"
    const val PRIVATE_CATEGORIES_URL = "https://api.kick.com/private/v1/categories"
    const val PRIVATE_CLIPS_CHANNEL_URL = "$API_BASE_URL/private/v1/channels/{slug}/clips"

    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    fun channelUrl(slug: String): String {
        return "$WEBSITE_BASE_URL/api/v2/channels/$slug"
    }

    fun channelVideosUrl(slug: String): String {
        return "$WEBSITE_BASE_URL/api/v2/channels/$slug/videos"
    }

    fun channelClipsUrl(slug: String): String {
        return "$WEBSITE_BASE_URL/api/v2/channels/$slug/clips"
    }

    fun clipUrl(id: String): String {
        return "$WEBSITE_BASE_URL/api/v2/clips/$id"
    }

    fun videoUrl(uuid: String): String {
        return "$WEBSITE_BASE_URL/api/v1/video/$uuid"
    }

    fun followedChannelsUrl(): String {
        return "$WEBSITE_BASE_URL/api/v2/channels/followed"
    }

    fun followChannelUrl(channel: Long): String {
        return "$WEBSITE_BASE_URL/api/v2/channels/$channel/follow"
    }

    fun getBrowserHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Accept" to "application/json",
        )
    }

    fun getAuthHeaders(token: String): Map<String, String> {
        return getBrowserHeaders() + mapOf(
            "Authorization" to "Bearer $token",
        )
    }

    fun getRealtimeHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "x-app-platform" to "web",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Referer" to "$WEBSITE_BASE_URL/",
            "Origin" to WEBSITE_BASE_URL,
        )
    }
}
