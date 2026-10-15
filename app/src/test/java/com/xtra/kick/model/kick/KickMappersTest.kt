package com.xtra.kick.model.kick

import com.xtra.kick.util.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickMappersTest {

    @Test
    fun `toVideo keeps legacy thumbnail for probing`() {
        val item = KickChannelVideo(
            id = 131471718,
            startTime = "2026-10-09 21:41:34",
            thumbnail = KickVideoThumbnail("https://images.kick.com/video_thumbnails/nUENxjSFZPaB/vXSzyhcFphZZ/720.webp"),
            video = KickVideoReference(uuid = "ede931d6-0000-4000-8000-000000000000"),
        )
        val ui = item.toVideo(
            channelLogin = "itspennygg",
            legacyThumbnail = item.thumbnail.src,
        )

        assertEquals("ede931d6-0000-4000-8000-000000000000", ui.id)
        assertEquals("https://images.kick.com/video_thumbnails/nUENxjSFZPaB/vXSzyhcFphZZ/720.webp", ui.kickLegacyThumbnail)
    }

    @Test
    fun `findByStart matches sub-only v1 entry by epoch`() {
        val entries = listOf(
            KickChannelVideoV1(id = "01a13dc3-b718-71a7-b2d6-07ae27bd25e8", startTime = "2026-10-15T04:13:19Z", status = "public"),
            KickChannelVideoV1(id = "01a1229d-42b0-7d40-9a3f-e15fcb040042", startTime = "2026-10-09T21:41:34Z", status = "sub_only"),
        )
        val match = entries.findByStart("2026-10-09 21:41:34")

        assertEquals("01a1229d-42b0-7d40-9a3f-e15fcb040042", match?.id)
        assertTrue(match?.status.equals("sub_only", ignoreCase = true))
    }


    @Test
    fun `livestream maps to ui Stream`() {
        val stream = KickLivestream(
            id = "42",
            streamer = KickStreamer(
                user = KickStreamerUser(id = "7", username = "streamer", profilePicture = "pfp.png"),
                channel = KickStreamerChannel(id = "9", slug = "streamer"),
            ),
            metadata = KickLivestreamMetadata(
                title = "Live now",
                category = KickCategory(id = "3", name = "Games", slug = "games", tags = listOf("speedrun")),
            ),
            viewersCount = 1234,
            playbackUrl = "https://live-video.net/master.m3u8",
            thumbnailUrl = "thumb.jpg",
            startedAt = "2026-09-22T10:00:00Z",
        )

        val ui = stream.toStream()

        assertEquals("42", ui.id)
        assertEquals("user_7", ui.channelId)
        assertEquals("streamer", ui.channelLogin)
        assertEquals("streamer", ui.channelName)
        assertEquals("3", ui.gameId)
        assertEquals("games", ui.gameSlug)
        assertEquals("Games", ui.gameName)
        assertEquals("Live now", ui.title)
        assertEquals("thumb.jpg", ui.thumbnailURL)
        assertEquals(1234, ui.viewerCount)
        assertEquals(listOf("speedrun"), ui.tags)
    }

    @Test
    fun `kick mappers tag platform so image helpers pass raw urls`() {
        val stream = KickLivestream(
            id = "42",
            metadata = KickLivestreamMetadata(category = KickCategory(id = "3", name = "Games")),
            thumbnailUrl = "https://images.kick.com/stream-thumb.png",
        )
        val ui = stream.toStream()

        assertEquals("kick", ui.platform)
        assertTrue(ui.isKick)
        assertEquals("https://images.kick.com/stream-thumb.png", ui.thumbnail)

        val game = KickCategory(id = "1", name = "Games", imageUrl = "https://img.kick.com/cat.png").toGame()
        assertEquals("kick", game.platform)
        assertTrue(game.isKick)
        assertEquals("https://img.kick.com/cat.png", game.boxArt)

        val user = KickFollowedChannel(id = 99, username = "chan", slug = "chan").toUser()
        assertEquals("kick", user.platform)
        assertTrue(user.isKick)

        val clip = KickClip(id = "clip-1", channelId = 123, channel = KickClipChannel(id = 123, username = "streamer", slug = "streamer", profilePicture = "pfp.png"))
        val uiClip = clip.toClip()
        assertEquals("kick", uiClip.platform)
        assertTrue(uiClip.isKick)
        assertEquals("pfp.png", uiClip.channelImage)

        val video = KickChannelVideo(id = 1, slug = "v1", channelId = 123, video = KickVideoReference(id = 1, uuid = null))
        val uiVideo = video.toVideo()
        assertEquals("kick", uiVideo.platform)
        assertTrue(uiVideo.isKick)
        assertEquals(C.KICK, uiVideo.platform)
    }

    @Test
    fun `category maps to ui Game`() {
        val category = KickCategory(
            id = "1", name = "Just Chatting", slug = "just-chatting",
            imageUrl = "https://img.kick.com/cat.png", viewersCount = 55, tags = listOf("irl"),
        )

        val game = category.toGame()

        assertEquals("1", game.id)
        assertEquals("just-chatting", game.slug)
        assertEquals("Just Chatting", game.name)
        assertEquals("https://img.kick.com/cat.png", game.boxArtURL)
        assertEquals(55, game.viewerCount)
        assertEquals("irl", game.tags?.first()?.name)
    }

    @Test
    fun `followed channel maps to ui User with kick prefix id`() {
        val followed = KickFollowedChannel(id = 99, username = "chan", slug = "chan", profilePicture = "pfp.png")

        val user = followed.toUser()

        assertEquals("user_99", user.id)
        assertEquals("chan", user.login)
        assertEquals("chan", user.name)
        assertEquals("pfp.png", user.profileImageURL)
        assertTrue(user.accountFollow)
    }

    @Test
    fun `channel video maps to ui Video with kick prefix channel id`() {
        val video = KickChannelVideo(
            id = 1,
            slug = "v1",
            channelId = 123,
            sessionTitle = "VOD title",
            startTime = "2026-09-20T10:00:00Z",
            duration = 3600000,
            views = 1200,
            thumbnail = KickVideoThumbnail(src = "https://img.kick.com/vod.png"),
            video = KickVideoReference(id = 1, uuid = "uuid-1"),
            categories = listOf(KickVideoCategory(id = 3, name = "Games", slug = "games")),
        )

        val ui = video.toVideo()

        // Uses video.uuid when present
        assertEquals("uuid-1", ui.id)
        assertEquals("user_123", ui.channelId)
        assertEquals("VOD title", ui.title)
        assertEquals("https://img.kick.com/vod.png", ui.thumbnailURL)
        assertEquals(1200, ui.viewCount)
        assertEquals(3600, ui.durationSeconds)
        assertEquals("archive", ui.type)
        assertEquals("Games", ui.gameName)
    }

    @Test
    fun `channel video maps to ui Video falling back to slug id`() {
        val video = KickChannelVideo(
            id = 1, slug = "v1", channelId = 123, duration = 0,
            video = KickVideoReference(id = 1, uuid = null),
        )

        val ui = video.toVideo()

        assertEquals("v1", ui.id)
        assertEquals("user_123", ui.channelId)
    }

    @Test
    fun `clip maps to ui Clip`() {
        val clip = KickClip(
            id = "clip-1",
            channelId = 123,
            title = "Great clip",
            clipUrl = "https://clips.kick.com/clip-1.mp4",
            thumbnailUrl = "https://img.kick.com/clip-1.png",
            viewCount = 42,
            duration = 27,
            createdAt = "2026-09-21T10:00:00Z",
            category = KickClipCategory(id = 3, name = "Games", slug = "games"),
            channel = KickClipChannel(id = 123, username = "streamer", slug = "streamer", profilePicture = "pfp.png"),
        )

        val ui = clip.toClip()

        assertEquals("clip-1", ui.id)
        assertEquals("user_123", ui.channelId)
        assertEquals("streamer", ui.channelName)
        assertEquals("pfp.png", ui.channelImageURL)
        assertEquals("Great clip", ui.title)
        assertEquals("https://img.kick.com/clip-1.png", ui.thumbnailURL)
        assertEquals(42, ui.viewCount)
        assertEquals(27, ui.durationSeconds)
        assertFalse(ui.viewCount == 0)
    }

    @Test
    fun `clip maps to ui Clip ignoring creator when channel absent`() {
        val clip = KickClip(
            id = "clip-1",
            creator = KickClipUser(id = 1, username = "clipper", slug = "clipper"),
        )

        val ui = clip.toClip()

        assertEquals("user_0", ui.channelId)
        assertNull(ui.channelName)
        assertNull(ui.channelImageURL)
    }

    @Test
    fun `videos index by livestream id skipping invalid`() {
        val videos = listOf(
            KickChannelVideo(id = 131645855, video = KickVideoReference(uuid = "uuid-1")),
            KickChannelVideo(id = 0, video = KickVideoReference(uuid = "uuid-0")),
            KickChannelVideo(id = -5, video = KickVideoReference(uuid = "uuid-neg")),
        )

        val indexed = videos.byLivestreamId()

        assertEquals(1, indexed.size)
        assertEquals("uuid-1", indexed["131645855"]?.video?.uuid)
    }

    @Test
    fun `clip resolves VOD uuid offset and start from livestream`() {
        val clip = KickClip(
            id = "clip_01",
            livestreamId = "131645855",
            vodStartsAt = 488,
            duration = 17,
        )
        val videos = listOf(
            KickChannelVideo(
                id = 131645855,
                startTime = "2026-10-10 20:08:10",
                video = KickVideoReference(uuid = "d6768682-411f-4390-839f-3c63b5940cf2"),
            ),
        ).byLivestreamId()

        val ui = clip.toClipResolved(
            channelId = "user_668",
            channelLogin = "xqc",
            videosByLivestreamId = videos,
        )

        assertEquals("d6768682-411f-4390-839f-3c63b5940cf2", ui.videoId)
        assertEquals(488, ui.videoOffsetSeconds)
        assertEquals("2026-10-10 20:08:10", ui.videoCreatedAt)
        assertEquals("user_668", ui.channelId)
    }

    @Test
    fun `clip without livestream match leaves VOD empty`() {
        val clip = KickClip(id = "clip_02", livestreamId = "999", vodStartsAt = 10)

        val ui = clip.toClipResolved(videosByLivestreamId = emptyMap())

        assertNull(ui.videoId)
        assertEquals(10, ui.videoOffsetSeconds)
        assertNull(ui.videoCreatedAt)
    }

    @Test
    fun `game prefers live metadata category`() {
        val live = KickLivestream(
            metadata = KickLivestreamMetadata(
                title = "Live",
                category = KickCategory(id = "15", name = "Just Chatting", slug = "just-chatting"),
            ),
        )
        val channel = KickChannelResponse(
            livestream = KickChannelLivestream(
                category = KickCategory(id = "99", name = "Other", slug = "other"),
                categories = listOf(KickCategoryEntry(id = 15, categoryId = 2, name = "List", slug = "list")),
            ),
            recentCategories = listOf(KickCategoryEntry(id = 7, name = "Recent", slug = "recent")),
        )

        val game = resolveKickGame(live, channel)

        assertEquals("15", game.id)
        assertEquals("just-chatting", game.slug)
        assertEquals("Just Chatting", game.name)
    }

    @Test
    fun `game falls back to livestream categories then recent`() {
        val channel = KickChannelResponse(
            livestream = KickChannelLivestream(
                categories = listOf(KickCategoryEntry(id = 15, categoryId = 2, name = "Just Chatting", slug = "just-chatting")),
            ),
        )

        val fromList = resolveKickGame(null, channel)
        assertEquals("2", fromList.id)
        assertEquals("just-chatting", fromList.slug)

        val recentOnly = KickChannelResponse(
            recentCategories = listOf(KickCategoryEntry(id = 7, name = "Recent", slug = "recent")),
        )
        val fromRecent = resolveKickGame(null, recentOnly)
        assertEquals("7", fromRecent.id)
        assertEquals("Recent", fromRecent.name)

        val empty = resolveKickGame(null, KickChannelResponse())
        assertNull(empty.id)
        assertNull(empty.slug)
        assertNull(empty.name)
    }

    @Test
    fun `thumbnail prefers live then livestream`() {
        val live = KickLivestream(thumbnailUrl = "https://live.thumb/x.jpg")
        val channel = KickChannelResponse(
            livestream = KickChannelLivestream(thumbnail = KickVideoThumbnail(src = "https://v2.thumb/y.jpg")),
        )

        assertEquals("https://live.thumb/x.jpg", resolveKickThumbnail(live, channel))
        assertEquals("https://v2.thumb/y.jpg", resolveKickThumbnail(null, channel))
        assertNull(resolveKickThumbnail(null, KickChannelResponse()))
    }
}