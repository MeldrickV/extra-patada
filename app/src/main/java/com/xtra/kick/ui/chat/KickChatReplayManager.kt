package com.xtra.kick.ui.chat

import android.util.Log
import com.xtra.kick.model.chat.ChatMessage
import com.xtra.kick.model.chat.VideoChatMessage
import com.xtra.kick.repository.KickRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
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
    private var numericChannelId: Long? = null
    private var seedPosition = 0L
    private var started = false
    private var isLoading = false
    private var loadJob: Job? = null
    private var messageJob: Job? = null
    private var lastCheckedPosition = 0L
    private var anchorRetries = 0
    private var playbackSpeed: Float? = null
    var isActive = true

    fun start() {
        if (!started) {
            started = true
            val currentPosition = getCurrentPosition() ?: 0
            lastCheckedPosition = currentPosition
            playbackSpeed = getCurrentSpeed()
            list.clear()
            seenIds.clear()
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
                seedPosition = fromMs
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

    private suspend fun fetch(numericChannelId: Long, fromMs: Long): Pair<List<VideoChatMessage>, Long> {
        val result = mutableListOf<VideoChatMessage>()
        val startMs = resolveVideoStart()
        val floorMs = fromMs - FETCH_WINDOW_MS
        var cursorUs: Long? = fromMs.times(1000).coerceAtLeast(0L)
        var pages = 0
        while (cursorUs != null && pages < MAX_PAGES) {
            val page = kickRepository.getChannelMessages(numericChannelId, cursorUs)
            val messages = page.messages.mapNotNull { message ->
                KickChatUtils.parseKickMessage(message)?.takeIf { it.timestamp != null }
            }
            if (messages.isEmpty()) {
                cursorUs = null
                break
            }
            result.addAll(mapReplayWindow(messages, startMs, fromMs, seenIds))
            val oldestTimestamp = messages.minOfOrNull { it.timestamp ?: 0L } ?: 0L
            if (oldestTimestamp < floorMs || result.size >= MESSAGE_BUFFER) {
                cursorUs = null
                break
            }
            cursorUs = page.cursor
            pages++
        }
        result.sortBy { it.offsetSeconds }
        return result to (cursorUs ?: 0L)
    }

    /**
     * Fetches the window ending at the given player position and merges unseen messages
     * into the buffer (no clearing). Returns true when at least one new message arrived.
     */
    private suspend fun fetchMore(position: Long): Boolean {
        val numericChannelId = numericChannelId ?: return false
        if (resolveVideoStart() == 0L) return false
        val fromMs = resolveVideoStart().plus(position).coerceAtLeast(0L)
        seedPosition = fromMs
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
                    true
                }
            }
        } catch (e: Exception) {
            Log.w("XtraChat", "kick replay fetchMore failed: ${e.message}")
            false
        }
    }

    private fun startJob() {
        messageJob?.cancel()
        messageJob = coroutineScope.launch {
            var index = 0
            // Skip messages already behind on (re)start so seeks don't re-emit the buffer.
            val startPosition = (getCurrentPosition() ?: 0) + startTime
            while (true) {
                val offset = list.getOrNull(index)?.offsetSeconds?.times(1000L) ?: break
                if (offset > startPosition) break
                index++
            }
            var idleDelayMs = FORWARD_IDLE_DELAY_MS
            while (isActive) {
                val message = list.getOrNull(index)
                if (message == null) {
                    // Caught up with the buffer: pull the next window forward so replay
                    // keeps flowing instead of dying after the initial pages.
                    val added = fetchMore(getCurrentPosition() ?: 0)
                    if (added) {
                        idleDelayMs = FORWARD_IDLE_DELAY_MS
                        continue
                    }
                    delay(idleDelayMs.milliseconds)
                    idleDelayMs = (idleDelayMs * 2).coerceAtMost(MAX_IDLE_DELAY_MS)
                    continue
                }
                idleDelayMs = FORWARD_IDLE_DELAY_MS
                val messageOffset = message.offsetSeconds?.times(1000L)
                if (messageOffset == null) {
                    index++
                    continue
                }
                var currentPosition: Long
                val positionBeforeWait = (getCurrentPosition() ?: 0) + startTime
                while (
                    (getCurrentPosition() ?: 0).let { position ->
                        lastCheckedPosition = position
                        currentPosition = position + startTime
                        currentPosition < messageOffset
                    }
                ) {
                    if (!isActive) {
                        break
                    }
                    val timeLeft = (messageOffset - currentPosition).div(playbackSpeed ?: 1f).toLong()
                    val delay = max(timeLeft, 1) // ExoPlayer getCurrentPosition freezes the app if it's called too rapidly
                    delay(delay.milliseconds)
                }
                if (!isActive) {
                    break
                }
                if (messageOffset <= positionBeforeWait) {
                    delay(CATCH_UP_DELAY_MS.milliseconds)
                    if (!isActive) {
                        break
                    }
                }
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
                index++
            }
        }
    }

    fun updatePosition(position: Long) {
        if (started && lastCheckedPosition != position) {
            val ms = resolveVideoStart().plus(position).coerceAtLeast(0L)
            if (position < lastCheckedPosition || ms - seedPosition > FORWARD_BUFFER_MS) {
                loadJob?.cancel()
                messageJob?.cancel()
                list.clear()
                coroutineScope.launch {
                    listener.clearMessages()
                }
                load(position)
            } else {
                messageJob?.cancel()
                startJob()
            }
            lastCheckedPosition = position
        }
    }

    fun updateSpeed(speed: Float) {
        if (started && playbackSpeed != speed) {
            playbackSpeed = speed
            startJob()
        }
    }

    companion object {
        private const val FETCH_WINDOW_MS = 120_000L
        private const val FORWARD_BUFFER_MS = 15_000L
        private const val BEHIND_GRACE_MS = 10_000L
        private const val CATCH_UP_DELAY_MS = 200L
        private const val MAX_PAGES = 4
        private const val MESSAGE_BUFFER = 500
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