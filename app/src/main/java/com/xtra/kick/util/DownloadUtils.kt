package com.xtra.kick.util

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * Retry and progress math shared by the download worker.
 */
object DownloadUtils {

    /**
     * Retries transient failures with exponential backoff. Auth failures
     * (401/403, handled by re-minting outside) and cancellation bypass retry.
     */
    suspend fun <T> retryWithBackoff(
        attempts: Int = 3,
        initialDelayMs: Long = 1_000,
        block: suspend (attempt: Int) -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SegmentHttpException) {
                if (e.code == 401 || e.code == 403) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
            if (attempt < attempts - 1) {
                delay(initialDelayMs shl attempt)
            }
        }
        throw lastError ?: IllegalStateException("retry exhausted")
    }

    fun updateEma(previous: Double, sample: Double, alpha: Double = 0.25): Double {
        return previous + alpha * (sample - previous)
    }

    /** Formats an ETA for [remaining] units at [perSecond] units/second. */
    fun formatEta(remaining: Int, perSecond: Double): String {
        if (remaining <= 0) return "0:00"
        if (perSecond <= 0 || !perSecond.isFinite()) return "--:--"
        var seconds = (remaining / perSecond).toLong()
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }

    /** Rough download size for [bitrateBps] over [durationMs], or null when unknown. */
    fun estimateSizeBytes(bitrateBps: Int?, durationMs: Long): Long? {
        if (bitrateBps == null || bitrateBps <= 0 || durationMs <= 0) return null
        return bitrateBps / 8L * (durationMs / 1000L)
    }

    fun formatSize(bytes: Long): String {
        var value = bytes.toDouble() / 1024.0
        var unit = 0 // 0 = KB, 1 = MB, 2 = GB
        while (value >= 1024 && unit < 2) {
            value /= 1024
            unit++
        }
        val number = when (unit) {
            2 -> String.format(java.util.Locale.US, "%.1f", value)
            1 -> String.format(java.util.Locale.US, "%.0f", value)
            else -> value.toLong().toString()
        }
        val suffix = when (unit) {
            2 -> "GB"
            1 -> "MB"
            else -> "KB"
        }
        return "$number $suffix"
    }
}
