package com.xtra.kick.ui.chat

import android.util.Log
import com.xtra.kick.model.chat.ChatMessage
import com.xtra.kick.model.chat.VideoChatMessage
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.util.KickApiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class KickChatReplayManager(
    private val kickRepository: KickRepository,
    private val channelLogin: String?,
    private val videoStart: Long?,
    private val getVideoStart: (() -> Long?)? = null,
    private val startTime: Long,
    private val getCurrentPosition: () -> Long?,
    private val getCurrentSpeed: () -> Float?,
    private val coroutineScope: CoroutineScope,
    private val listener: ChatReplayManager.Listener,
) {
    private val list = mutableListOf<VideoChatMessage>()
    private val seenIds = HashSet<String>()
    private val emittedKeys = HashSet<String>()
    private var numericChannelId: Long? = null
    private var started = false
    private var isLoading = false
    private var loadJob: Job? = null
    private var messageJob: Job? = null
    private var lastCheckedPosition = 0L
    private var anchorRetries = 0
    var isActive = true

    fun start() {
        if (!started) {
            started = true
            val currentPosition = getCurrentPosition() ?: 0
            lastCheckedPosition = currentPosition
            list.clear()
            seenIds.clear()
            emittedKeys.clear()
            coroutineScope.launch {
                listener.clearMessages()
            }
            load(currentPosition)
        }
    }

    fun stop() {
        loadJob?.cancel()
        messageJob?.cancel()
        isActive = false
    }

    private fun resolveVideoStart(): Long {
        // Read lazily: the service's createdAt may still be unset when the manager is
        // created, and is only repaired once loadVideo resolves the video over network.
        return getVideoStart?.invoke() ?: videoStart ?: 0L
    }

    private fun load(position: Long) {
        isLoading = true
        seenIds.clear()
        emittedKeys.clear()
        loadJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                var anchor = resolveVideoStart()
                while (anchor == 0L && anchorRetries < MAX_ANCHOR_RETRIES && isActive) {
                    anchorRetries += 1
                    delay(2000)
                    anchor = resolveVideoStart()
                }
                if (anchor == 0L) {
                    Log.w("XtraChat", "kick replay aborted: no video start anchor after retries")
                    isLoading = false
                    return@launch
                }
                anchorRetries = 0
                val channelId = ensureChannelId()
                val fromMs = anchor.plus(position).coerceAtLeast(0L)
                if (channelId == null) {
                    Log.w("XtraChat", "kick replay aborted: no channel id for $channelLogin")
                    isLoading = false
                    return@launch
                }
                val fetched = fetch(channelId, fromMs)
                Log.i("XtraChat", "kick replay window loaded: anchor=$anchor fromMs=$fromMs matched=${fetched.first.size}")
                list.clear()
                list.addAll(fetched.first)
                isLoading = false
                startJob()
            } catch (e: Exception) {
                Log.w("XtraChat", "kick replay load failed: ${e.message}")
                isLoading = false
            }
        }
    }

    private suspend fun ensureChannelId(): Long? {
        numericChannelId?.let { return it }
        val login = channelLogin
        if (login.isNullOrBlank()) return null
        numericChannelId = runCatching { kickRepository.getChannel(login).id.takeIf { it > 0 } }
            .onFailure { Log.w("XtraChat", "kick replay channel lookup failed for $login: ${it.message}") }
            .getOrNull()
        return numericChannelId
    }

    /**
     * Pages the replay window forward: the first page is anchored with
     * `?start_time=` at the current video position, then the server cursor is
     * followed while messages keep falling inside the forward window.
     */
    private suspend fun fetch(numericChannelId: Long, fromMs: Long): Pair<List<VideoChatMessage>, Long> {
        val result = mutableListOf<VideoChatMessage>()
        val startMs = resolveVideoStart()
        val ceilingMs = fromMs + FORWARD_WINDOW_MS
        val startIso = KickApiHelper.instantIsoUtc(fromMs)
        var cursorUs: Long? = null
        var pages = 0
        var lastCursor: Long = 0
        while (pages < MAX_PAGES) {
            val page = kickRepository.getChannelMessages(
                numericChannelId,
                cursorUs,
                startTimeIso = startIso.takeIf { pages == 0 },
            )
            val messages = page.messages.mapNotNull { message ->
                KickChatUtils.parseKickMessage(message)?.takeIf { it.timestamp != null }
            }
            if (messages.isEmpty()) {
                break
            }
            result.addAll(mapReplayWindow(messages, startMs, ceilingMs, seenIds))
            val newestTimestamp = messages.maxOfOrNull { it.timestamp ?: 0L } ?: 0L
            lastCursor = page.cursor ?: 0L
            pages++
            if (newestTimestamp >= ceilingMs || page.cursor == null || result.size >= MESSAGE_BUFFER) {
                break
            }
            cursorUs = page.cursor
        }
        result.sortBy { it.offsetSeconds }
        return result to lastCursor
    }

    /**
     * Fetches the window starting at the given player position and merges unseen
     * messages into the buffer. Returns true when at least one new message arrived.
     */
    private suspend fun fetchMore(position: Long): Boolean {
        val numericChannelId = numericChannelId ?: return false
        if (resolveVideoStart() == 0L) return false
        val fromMs = resolveVideoStart().plus(position).coerceAtLeast(0L)
        return try {
            val fetched = fetch(numericChannelId, fromMs).first
            if (fetched.isEmpty()) {
                false
            } else {
                val cutoff = position + startTime - BEHIND_GRACE_MS
                val fresh = fetched.filter { (it.offsetSeconds?.times(1000L) ?: Long.MAX_VALUE) >= cutoff }
                if (fresh.isEmpty()) {
                    false
                } else {
                    list.addAll(fresh)
                    list.sortBy { it.offsetSeconds }
                    trimBuffer(position)
                    true
                }
            }
        } catch (e: Exception) {
            Log.w("XtraChat", "kick replay fetchMore failed: ${e.message}")
            false
        }
    }

    /** Drops messages far behind the playhead so the buffer stays bounded. */
    private fun trimBuffer(position: Long) {
        if (list.size <= MESSAGE_BUFFER) return
        val cutoff = position + startTime - TRIM_BEHIND_MS
        val dropCount = list.count { (it.offsetSeconds?.times(1000L) ?: Long.MAX_VALUE) < cutoff }
        if (dropCount > 0) {
            repeat(minOf(dropCount, list.size - MESSAGE_BUFFER / 2)) { list.removeAt(0) }
        }
    }

    /**
     * Tick-based emitter: every tick renders the messages due at the current
     * playhead. When behind (seek forward, speed burst), only the most recent
     * batch is shown so the chat jumps to the present instead of slow-crawling.
     */
    private fun startJob() {
        messageJob?.cancel()
        messageJob = coroutineScope.launch {
            var idleDelayMs = FORWARD_IDLE_DELAY_MS
            while (isActive) {
                val position = getCurrentPosition() ?: 0
                val now = position + startTime
                val due = list.filter { message ->
                    val offset = message.offsetSeconds?.times(1000L) ?: return@filter false
                    offset <= now && emittedKeys.add(emissionKey(message))
                }
                val toEmit = if (due.size > MAX_EMIT_PER_TICK) due.takeLast(MAX_EMIT_PER_TICK) else due
                toEmit.forEach { message ->
                    listener.onChatMessage(
                        ChatMessage(
                            type = ChatMessage.USER_MESSAGE,
                            id = message.id,
                            userId = message.userId,
                            userLogin = message.userLogin,
                            userName = message.userName,
                            message = message.message,
                            color = message.color,
                            emotes = message.emotes,
                            badges = message.badges,
                            bits = 0,
                            fullMsg = message.fullMsg,
                        )
                    )
                }
                if (toEmit.isNotEmpty()) {
                    idleDelayMs = FORWARD_IDLE_DELAY_MS
                    delay(EMIT_TICK_MS.milliseconds)
                    continue
                }
                // Nothing due: pull the next window forward so replay keeps
                // flowing instead of stalling after the initial pages.
                val added = fetchMore(position)
                if (added) {
                    idleDelayMs = FORWARD_IDLE_DELAY_MS
                    continue
                }
                delay(idleDelayMs.milliseconds)
                idleDelayMs = (idleDelayMs * 2).coerceAtMost(MAX_IDLE_DELAY_MS)
            }
        }
    }

    private fun emissionKey(message: VideoChatMessage): String {
        return message.id ?: "${message.offsetSeconds}|${message.userLogin}|${message.message}"
    }

    fun updatePosition(position: Long) {
        if (started && lastCheckedPosition != position) {
            // Any seek invalidates everything: stale messages must never mix
            // with the new window.
            lastCheckedPosition = position
            loadJob?.cancel()
            messageJob?.cancel()
            list.clear()
            coroutineScope.launch {
                listener.clearMessages()
            }
            load(position)
        }
    }

    fun updateSpeed(speed: Float) {
        // Tick emission follows the playhead directly, so speed needs no
        // pacing state; restart the emitter to pick up the new position.
        if (started) {
            startJob()
        }
    }

    companion object {
        private const val FORWARD_WINDOW_MS = 120_000L
        private const val BEHIND_GRACE_MS = 10_000L
        private const val TRIM_BEHIND_MS = 60_000L
        private const val MAX_PAGES = 6
        private const val MESSAGE_BUFFER = 500
        private const val MAX_EMIT_PER_TICK = 75
        private const val EMIT_TICK_MS = 1_000L
        private const val FORWARD_IDLE_DELAY_MS = 30_000L
        private const val MAX_IDLE_DELAY_MS = 60_000L
        private const val MAX_ANCHOR_RETRIES = 5
    }
}

fun mapReplayWindow(
    messages: List<ChatMessage>,
    startMs: Long,
    fromMs: Long,
    seenIds: MutableSet<String>,
): List<VideoChatMessage> {
    return messages.mapNotNull { message ->
        val timestamp = message.timestamp ?: return@mapNotNull null
        if (timestamp in startMs..fromMs && seenIds.add(message.id ?: timestamp.toString())) {
            VideoChatMessage(
                id = message.id,
                offsetSeconds = (timestamp - startMs).div(1000).toInt().coerceAtLeast(0),
                createdAt = null,
                userId = message.userId,
                userLogin = message.userLogin,
                userName = message.userName,
                message = message.message,
                color = message.color,
                emotes = message.emotes,
                badges = message.badges,
                fullMsg = null,
            )
        } else {
            null
        }
    }
}