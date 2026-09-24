package com.xtra.kick.util.chat

import com.xtra.kick.repository.KickRepository
import com.xtra.kick.util.KickApiHelper
import com.xtra.kick.util.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class KickChatWebSocket(
    private val chatroomId: Long,
    private val kickRepository: KickRepository,
    private val trustManager: Lazy<X509TrustManager>,
    private val listener: Listener,
) {
    private var webSocket: WebSocket? = null
    private val clientId = UUID.randomUUID().toString()
    private var coroutineScope: CoroutineScope? = null
    private var reconnectJob: Job? = null

    fun connect(coroutineScope: CoroutineScope): Job {
        this.coroutineScope = coroutineScope
        return coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val info = kickRepository.getRealtimeConnectionInfo(clientId)
                    val connectedWebSocket = WebSocket(
                        url = info.url,
                        trustManager = trustManager,
                        listener = WebSocketListener(),
                        headers = mapOf(
                            "User-Agent" to KickApiHelper.BROWSER_USER_AGENT,
                            "Origin" to KickApiHelper.WEBSITE_BASE_URL,
                        ),
                        sendPings = true,
                    )
                    webSocket = connectedWebSocket
                    connectedWebSocket.coroutineScope = coroutineScope
                    connectedWebSocket.start()
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    listener.onDisconnect(e.toString(), e.stackTraceToString())
                }
                delay(5.seconds)
            }
        }
    }

    suspend fun disconnect(job: Job?) = withContext(Dispatchers.IO) {
        job?.cancel()
        reconnectJob?.cancel()
        webSocket?.disconnect()
    }

    private suspend fun refreshSubscription() {
        val ws = webSocket ?: return
        val token = kickRepository.getRealtimeAuthToken(clientId)
        ws.write("""{"connect":{"token":"$token","name":"js"},"id":1}""")
        ws.write("""{"subscribe":{"channel":"chatrooms.$chatroomId.v2","flag":1},"id":2}""")
        ws.write("""{"history":{"channel":"chatrooms.$chatroomId.v2","limit":25},"id":3}""")
    }

    private fun scheduleReconnect(ttlSeconds: Long) {
        reconnectJob?.cancel()
        if (ttlSeconds <= 0) {
            return
        }
        val scope = coroutineScope ?: return
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay((ttlSeconds - 5).coerceAtLeast(60).seconds)
            if (isActive) {
                runCatching { refreshSubscription() }
            }
        }
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            try {
                refreshSubscription()
                listener.onConnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                listener.onDisconnect(e.toString(), e.stackTraceToString())
            }
        }

        override suspend fun onMessage(webSocket: WebSocket, message: String) {
            message.split("\n").forEach { frame ->
                if (frame.isNotBlank()) {
                    handleFrame(frame)
                }
            }
        }

        override suspend fun onDisconnect(webSocket: WebSocket, message: String, fullMsg: String?) {
            listener.onDisconnect(message, fullMsg)
        }
    }

    private suspend fun handleFrame(frame: String) {
        try {
            val root = JSONObject(frame)
            val connect = root.optJSONObject("connect")
            if (connect != null) {
                scheduleReconnect(connect.optLong("ttl"))
                return
            }
            val history = root.optJSONObject("history")
            if (history != null) {
                val messages = history.optJSONArray("messages") ?: JSONArray()
                val events = buildList {
                    for (i in 0 until messages.length()) {
                        val item = messages.optJSONObject(i) ?: continue
                        parseChatEvent(item.optString("message").takeIf { it.isNotBlank() }?.let { JSONObject(it) } ?: item.optJSONObject("message"))?.let { add(it) }
                    }
                }
                if (events.isNotEmpty()) {
                    listener.onHistory(events)
                }
                for (i in 0 until messages.length()) {
                    val item = messages.optJSONObject(i) ?: continue
                    val payload = item.optString("message").takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: item.optJSONObject("message") ?: continue
                    when (payload.optString("event")) {
                        "App\\Events\\PinnedMessageCreatedEvent" -> parseEventData(payload)?.let { listener.onPinnedMessage(it) }
                        "App\\Events\\PinnedMessageDeletedEvent" -> listener.onPinnedMessageDeleted()
                    }
                }
                return
            }
            val push = root.optJSONObject("push") ?: return
            val channel = push.optString("channel")
            if (!channel.startsWith("chatrooms.")) {
                return
            }
            val pub = push.optJSONObject("pub") ?: return
            val payload = pub.optJSONObject("data") ?: return
            when (payload.optString("event")) {
                "App\\Events\\ChatMessageEvent" -> parseChatEvent(payload)?.let { listener.onChatMessage(it) }
                "App\\Events\\PinnedMessageCreatedEvent" -> parseEventData(payload)?.let { listener.onPinnedMessage(it) }
                "App\\Events\\PinnedMessageDeletedEvent" -> listener.onPinnedMessageDeleted()
            }
        } catch (e: Exception) {
            listener.onDisconnect(e.toString(), e.stackTraceToString())
        }
    }

    private fun parseChatEvent(payload: JSONObject?): JSONObject? {
        if (payload == null || payload.optString("event") != "App\\Events\\ChatMessageEvent") {
            return null
        }
        val data = payload.optString("data").takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: return null
        return data.optString("content").takeIf { it.isNotBlank() }?.let { data }
    }

    private fun parseEventData(payload: JSONObject?): JSONObject? {
        if (payload == null) {
            return null
        }
        return payload.optString("data").takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onChatMessage(event: JSONObject) {}
        suspend fun onHistory(messages: List<JSONObject>) {}
        suspend fun onPinnedMessage(event: JSONObject) {}
        suspend fun onPinnedMessageDeleted() {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }
}