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
        webSocket?.disconnect()
    }

    private inner class WebSocketListener : WebSocket.Listener {
        override suspend fun onConnect(webSocket: WebSocket) {
            try {
                val token = kickRepository.getRealtimeAuthToken(clientId)
                webSocket.write("""{"connect":{"token":"$token","name":"js"},"id":1}""")
                webSocket.write("""{"subscribe":{"channel":"chatrooms.$chatroomId.v2","flag":1},"id":2}""")
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
            val push = root.optJSONObject("push") ?: return
            val channel = push.optString("channel")
            if (!channel.startsWith("chatrooms.")) {
                return
            }
            val pub = push.optJSONObject("pub") ?: return
            val data = pub.optJSONObject("data") ?: return
            if (data.optString("event") != "App\\Events\\ChatMessageEvent") {
                return
            }
            val payload = data.optString("data").takeIf { it.isNotBlank() }?.let { JSONObject(it) } ?: return
            listener.onChatMessage(payload)
        } catch (e: Exception) {
            listener.onDisconnect(e.toString(), e.stackTraceToString())
        }
    }

    interface Listener {
        suspend fun onConnect() {}
        suspend fun onChatMessage(event: JSONObject) {}
        suspend fun onDisconnect(message: String, fullMsg: String?) {}
    }
}