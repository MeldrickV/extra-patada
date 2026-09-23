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
    fun `vodMasterUrl returns null for missing or irrelevant urls`() {
        assertNull(KickPlayback.vodMasterUrl(null))
        assertNull(KickPlayback.vodMasterUrl(""))
        assertNull(KickPlayback.vodMasterUrl("https://images.kick.com/stream-thumb.png"))
        assertNull(KickPlayback.vodMasterUrl("https://kx-uploads.s3.us-east-2.amazonaws.com/media/thumbnails/-thumb.png"))
    }
}