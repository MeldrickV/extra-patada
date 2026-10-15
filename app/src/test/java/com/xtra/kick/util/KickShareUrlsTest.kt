package com.xtra.kick.util

import org.junit.Assert.assertEquals
import org.junit.Test

class KickShareUrlsTest {

    @Test
    fun `builds canonical share urls`() {
        assertEquals("https://kick.com/xqc", KickShareUrls.channelShareUrl("xqc"))
        assertEquals(
            "https://kick.com/xqc/videos/01a1229d-42b0-7d40-9a3f-e15fcb040042",
            KickShareUrls.videoShareUrl("xqc", "01a1229d-42b0-7d40-9a3f-e15fcb040042"),
        )
        assertEquals(
            "https://kick.com/xqc/clips/abcd1234",
            KickShareUrls.clipShareUrl("xqc", "abcd1234"),
        )
    }
}
