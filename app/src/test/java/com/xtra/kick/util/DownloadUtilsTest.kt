package com.xtra.kick.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DownloadUtilsTest {

    @Test
    fun `retry succeeds after transient failures`() = runBlocking {
        var calls = 0
        val result = DownloadUtils.retryWithBackoff(attempts = 3, initialDelayMs = 1) {
            calls++
            if (calls < 3) throw IOException("flaky")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `retry rethrows after exhausting attempts`() = runBlocking {
        var calls = 0
        try {
            DownloadUtils.retryWithBackoff(attempts = 2, initialDelayMs = 1) {
                calls++
                throw IOException("down")
            }
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("down", e.message)
        }
        assertEquals(2, calls)
    }

    @Test
    fun `retry bypasses auth failures`() = runBlocking {
        var calls = 0
        try {
            DownloadUtils.retryWithBackoff(attempts = 3, initialDelayMs = 1) {
                calls++
                throw SegmentHttpException(403)
            }
            fail("expected SegmentHttpException")
        } catch (e: SegmentHttpException) {
            assertEquals(403, e.code)
        }
        assertEquals(1, calls)
    }

    @Test
    fun `retry retries non-auth http errors`() = runBlocking {
        var calls = 0
        try {
            DownloadUtils.retryWithBackoff(attempts = 2, initialDelayMs = 1) {
                calls++
                throw SegmentHttpException(500)
            }
            fail("expected SegmentHttpException")
        } catch (e: SegmentHttpException) {
            assertEquals(500, e.code)
        }
        assertEquals(2, calls)
    }

    @Test
    fun `ema smooths samples`() {
        assertEquals(10.0, DownloadUtils.updateEma(10.0, 20.0, 0.0), 0.0)
        assertEquals(20.0, DownloadUtils.updateEma(10.0, 20.0, 1.0), 0.0)
        assertEquals(12.5, DownloadUtils.updateEma(10.0, 20.0, 0.25), 0.0)
    }

    @Test
    fun `eta formats minutes seconds and hours`() {
        assertEquals("0:00", DownloadUtils.formatEta(0, 5.0))
        assertEquals("2:00", DownloadUtils.formatEta(120, 1.0))
        assertEquals("1:01", DownloadUtils.formatEta(61, 1.0))
        assertEquals("1:02:03", DownloadUtils.formatEta(3723, 1.0))
        assertEquals("--:--", DownloadUtils.formatEta(10, 0.0))
        assertEquals("--:--", DownloadUtils.formatEta(10, Double.NaN))
    }

    @Test
    fun `eta handles fractional rates`() {
        assertTrue(DownloadUtils.formatEta(7, 2.0) == "0:03")
    }
}
