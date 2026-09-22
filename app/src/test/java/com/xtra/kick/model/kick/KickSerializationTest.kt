package com.xtra.kick.model.kick

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `livestreams response parses and maps fields`() {
        val payload = """
            {
              "status": {"error": false, "code": 200, "message": "OK"},
              "data": {
                "next_cursor": "abc123",
                "livestreams": [{
                  "id": "42",
                  "viewers_count": 1234,
                  "playback_url": "https://live-video.net/master.m3u8",
                  "thumbnail_url": "thumb.jpg",
                  "started_at": "2026-09-22T10:00:00Z",
                  "streamer": {
                    "user": {"id": "7", "username": "streamer", "is_verified": true, "profile_picture": "pfp.png"},
                    "channel": {"id": "9", "slug": "streamer", "banner_picture": "banner.png", "description": "desc"}
                  },
                  "metadata": {
                    "title": "Live now",
                    "language": "en",
                    "category": {"id": "3", "name": "Games", "slug": "games", "tags": ["speedrun"]}
                  }
                }]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<KickLivestreamsResponse>(payload)
        assertFalse(response.status.error)
        assertEquals("abc123", response.data.nextCursor)
        assertEquals(1, response.data.livestreams.size)

        val stream = response.data.livestreams.first()
        assertEquals("42", stream.id)
        assertEquals(1234, stream.viewersCount)
        assertEquals("https://live-video.net/master.m3u8", stream.playbackUrl)
        assertEquals("streamer", stream.streamer?.user?.username)
        assertTrue(stream.streamer?.user?.isVerified == true)
        assertEquals("streamer", stream.streamer?.channel?.slug)
        assertEquals("Live now", stream.metadata?.title)
        assertEquals("Games", stream.metadata?.category?.name)
        assertEquals(listOf("speedrun"), stream.metadata?.category?.tags)
    }

    @Test
    fun `livestreams response tolerates missing optional fields`() {
        val response = json.decodeFromString<KickLivestreamsResponse>("""{"data":{}}""")
        assertTrue(response.data.livestreams.isEmpty())
        assertNull(response.data.nextCursor)
    }

    @Test
    fun `categories response parses`() {
        val payload = """
            {
              "status": {"error": false, "code": 200, "message": "OK"},
              "data": {
                "categories": [{
                  "id": "1", "name": "Just Chatting", "slug": "just-chatting",
                  "image_url": "https://img.kick.com/cat.png", "viewers_count": 55, "tags": ["irl"]
                }],
                "next_cursor": "cur"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<KickCategoriesResponse>(payload)
        val category = response.data.categories.first()
        assertEquals("1", category.id)
        assertEquals("Just Chatting", category.name)
        assertEquals("https://img.kick.com/cat.png", category.imageUrl)
        assertEquals(55, category.viewersCount)
        assertEquals(listOf("irl"), category.tags)
        assertEquals("cur", response.data.nextCursor)
    }

    @Test
    fun `channel response parses playback url and chatroom`() {
        val payload = """
            {
              "id": 123, "slug": "streamer",
              "playback_url": "https://live-video.net/master.m3u8",
              "followers_count": 5000,
              "user": {"id": 123, "username": "streamer", "profile_picture": "pfp.png"},
              "chatroom": {"id": 456, "name": "streamer's chill zone", "slug": "streamer"},
              "livestream": {
                "id": 789, "session_title": "Live session", "is_live": true,
                "category": {"id": "3", "name": "Games", "slug": "games"},
                "viewer_count": 99, "started_at": "2026-09-22T10:00:00Z"
              }
            }
        """.trimIndent()

        val channel = json.decodeFromString<KickChannelResponse>(payload)
        assertEquals(123L, channel.id)
        assertEquals("https://live-video.net/master.m3u8", channel.playbackUrl)
        assertEquals(5000L, channel.followersCount)
        assertEquals(456L, channel.chatroom?.id)
        assertTrue(channel.livestream?.isLive == true)
        assertEquals("Live session", channel.livestream?.sessionTitle)
    }

    @Test
    fun `channel response handles offline channel`() {
        val channel = json.decodeFromString<KickChannelResponse>(
            """{"id": 123, "slug": "offline", "playback_url": null, "livestream": null}"""
        )
        assertNull(channel.playbackUrl)
        assertNull(channel.livestream)
    }

    @Test
    fun `channel videos response parses source and session fields`() {
        val payload = """
            {
              "videos": [{
                "id": 1, "slug": "v1", "channel_id": 123,
                "session_title": "VOD title", "is_live": false,
                "start_time": "2026-09-20T10:00:00Z",
                "source": "https://stream.kick.com/vod/master.m3u8",
                "duration": 3600000, "language": "en", "viewer_count": 8, "views": 1200,
                "thumbnail": {"src": "https://img.kick.com/vod.png"},
                "video": {"id": 1, "uuid": "uuid-1", "created_at": "2026-09-20T10:00:00Z", "status": "done"},
                "categories": [{"id": 3, "name": "Games", "slug": "games"}]
              }]
            }
        """.trimIndent()

        val video = json.decodeFromString<KickChannelsVideosResponse>(payload).videos.first()
        assertEquals(123L, video.channelId)
        assertEquals("VOD title", video.sessionTitle)
        assertEquals("uuid-1", video.video.uuid)
        assertEquals(3600000L, video.duration)
        assertEquals(1200L, video.views)
        assertEquals("https://img.kick.com/vod.png", video.thumbnail.src)
        assertEquals("Games", video.categories.first().name)
    }

    @Test
    fun `video response exposes source for downloads`() {
        val response = json.decodeFromString<KickVideoResponse>(
            """{"source": "https://stream.kick.com/vod/master.m3u8"}"""
        )
        assertEquals("https://stream.kick.com/vod/master.m3u8", response.source)
    }

    @Test
    fun `clips response parses and exposes clip url`() {
        val payload = """
            {
              "clips": [{
                "id": "clip-1", "livestream_id": "900", "channel_id": 123,
                "title": "Great clip", "clip_url": "https://clips.kick.com/clip-1.mp4",
                "thumbnail_url": "https://img.kick.com/clip-1.png",
                "video_url": "https://clips.kick.com/clip-1.mp4",
                "privacy": "public", "likes": 3, "views": 42, "view_count": 42,
                "duration": 27, "created_at": "2026-09-21T10:00:00Z",
                "category": {"id": 3, "name": "Games", "slug": "games"},
                "creator": {"id": 1, "username": "clipper", "slug": "clipper"},
                "channel": {"id": 123, "username": "streamer", "slug": "streamer", "profile_picture": "pfp.png"}
              }]
            }
        """.trimIndent()

        val clip = json.decodeFromString<KickChannelsClipsResponse>(payload).clips.first()
        assertEquals("clip-1", clip.id)
        assertEquals("https://clips.kick.com/clip-1.mp4", clip.clipUrl)
        assertEquals(27, clip.duration)
        assertEquals(42, clip.viewCount)
    }

    @Test
    fun `single clip response parses wrap`() {
        val wrap = json.decodeFromString<KickSingleClipResponse>(
            """{"clip": {"id": "x", "clip_url": "https://clips.kick.com/x.mp4"}}"""
        )
        assertEquals("x", wrap.clip.id)
        assertNotNull(wrap.clip.clipUrl)
    }

    @Test
    fun `oauth token response parses`() {
        val token = json.decodeFromString<KickOAuthTokenResponse>(
            """{"access_token": "at", "refresh_token": "rt", "expires_in": 3600,
                "refresh_expires_in": 15552000, "token_type": "Bearer", "scope": "user:read"}"""
        )
        assertEquals("at", token.accessToken)
        assertEquals("rt", token.refreshToken)
        assertEquals(3600L, token.expiresIn)
        assertEquals("Bearer", token.tokenType)
    }

    @Test
    fun `oauth introspection marks inactive tokens`() {
        val active = json.decodeFromString<KickOAuthIntrospection>(
            """{"active": true, "sub": "u1", "username": "user", "exp": 9999999999}"""
        )
        assertTrue(active.active)
        assertEquals("user", active.username)

        val inactive = json.decodeFromString<KickOAuthIntrospection>("""{"active": false}""")
        assertFalse(inactive.active)
        assertNull(inactive.sub)
    }

    @Test
    fun `self user response parses`() {
        val user = json.decodeFromString<KickSelfUserResponse>(
            """{"data": {"id": 7, "username": "me", "slug": "me", "email": "a@b.c", "profile_picture": "pfp.png"}}"""
        ).data
        assertEquals(7L, user.id)
        assertEquals("me", user.username)
        assertEquals("a@b.c", user.email)
    }
}