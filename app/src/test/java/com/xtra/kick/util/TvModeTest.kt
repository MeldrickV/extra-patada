package com.xtra.kick.util

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvModeTest {

    @Test
    fun `leanback feature means tv`() {
        assertTrue(TvMode.isTvDevice(true, Configuration.UI_MODE_TYPE_NORMAL))
        assertTrue(TvMode.isTvDevice(true, null))
    }

    @Test
    fun `television ui mode means tv`() {
        assertTrue(TvMode.isTvDevice(false, Configuration.UI_MODE_TYPE_TELEVISION))
    }

    @Test
    fun `phone without leanback is not tv`() {
        assertFalse(TvMode.isTvDevice(false, Configuration.UI_MODE_TYPE_NORMAL))
        assertFalse(TvMode.isTvDevice(false, null))
    }

    @Test
    fun `ui mode constants are distinct`() {
        assertEquals(4, Configuration.UI_MODE_TYPE_TELEVISION)
    }
}
