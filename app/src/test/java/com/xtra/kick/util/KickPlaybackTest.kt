package com.xtra.kick.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KickPlaybackTest {

    @Test
    fun `vodMasterUrl derives hls url from thumbnail`() {
        assertEquals(
            "https://kx-uploads.s3.us-east-2.amazonaws.com/abc/media/hls/uuid-1/master.m3u8",
            KickPlayback.vodMasterUrl("https://kx-uploads.s3.us-east-2.amazonaws.com/abc/media/thumbnails/uuid-1-thumb.png")
        )
        assertEquals(
            "https://kx-uploads.s3.us-east-2.amazonaws.com/abc/media/hls/uuid-1/master.m3u8",
            KickPlayback.vodMasterUrl("https://kx-uploads.s3.us-east-2.amazonaws.com/abc/media/thumbnails/uuid-1.png")
        )
        assertEquals(
            "https://kx-uploads.s3.us-east-2.amazonaws.com/abc/media/hls/abc/master.m3u8",
            KickPlayback.vodMasterUrl("https://kx-uploads.s3.us-east-2.amazonaws.com/abc/media/thumbnails/abc-thumb.jpg")
        )
    }

    @Test
    fun `vodMasterUrl derives ivs hls url from full thumbnail path`() {
        assertEquals(
            "https://stream.kick.com/ivs/v1/aws1/sess1/2024/03/04/04/19/seg1/media/hls/master.m3u8",
            KickPlayback.vodMasterUrl("https://stream.kick.com/media/thumbnails/aws1/sess1/2024/3/4/4/19/seg1/thumbnail-320x180.png")
        )
        assertEquals(
            "https://stream.kick.com/ivs/v1/aws1/sess1/2024/12/31/23/59/seg9/media/hls/master.m3u8",
            KickPlayback.vodMasterUrl("https://stream.kick.com/media/thumbnails/aws1/sess1/2024/12/31/23/59/seg9/thumbnail-480x320.jpg?x=1")
        )
    }

    @Test
    fun `vodMasterUrl returns null for missing or irrelevant urls`() {
        assertNull(KickPlayback.vodMasterUrl(null))
        assertNull(KickPlayback.vodMasterUrl(""))
        assertNull(KickPlayback.vodMasterUrl("https://images.kick.com/stream-thumb.png"))
        assertNull(KickPlayback.vodMasterUrl("https://kx-uploads.s3.us-east-2.amazonaws.com/media/thumbnails/-thumb.png"))
    }
}