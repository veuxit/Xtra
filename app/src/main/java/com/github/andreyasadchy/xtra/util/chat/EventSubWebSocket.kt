package com.github.andreyasadchy.xtra.util.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class EventSubWebSocket(
    private val client: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val listener: EventSubListener) {
    private var socket: WebSocket? = null
    var isActive = false
    private var pongReceived = false
    private var timeout = 10
    private var usingReconnectUrl = false
    private val handledMessageIds = mutableListOf<String>()

    fun connect(reconnectUrl: String? = null) {
        socket = client.newWebSocket(Request.Builder().url(reconnectUrl ?: "wss://eventsub.wss.twitch.tv/ws").build(), EventSubWebSocketListener())
    }

    fun disconnect() {
        isActive = false
        socket?.close(1000, null)
    }

    private fun reconnect(reconnectUrl: String? = null) {
        if (isActive) {
            coroutineScope.launch {
                disconnect()
                delay(1000)
                connect(reconnectUrl)
            }
        }
    }

    private fun checkPong() {
        tickerFlowPong().onCompletion {
            if (isActive) {
                if (pongReceived) {
                    pongReceived = false
                    checkPong()
                } else {
                    reconnect()
                }
            }
        }.launchIn(coroutineScope)
    }

    private fun tickerFlowPong() = flow {
        for (i in timeout downTo 0) {
            if (pongReceived || !isActive) {
                emit(i downTo 0)
            } else {
                emit(i)
                delay(1000)
            }
        }
    }

    private inner class EventSubWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isActive = true
            listener.onConnect()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = if (text.isNotBlank()) JSONObject(text) else null
                json?.let {
                    val metadata = json.optJSONObject("metadata")
                    val messageId = if (metadata?.isNull("message_id") == false) metadata.optString("message_id").takeIf { it.isNotBlank() } else null
                    val timestamp = if (metadata?.isNull("message_timestamp") == false) metadata.optString("message_timestamp").takeIf { it.isNotBlank() } else null
                    if (!messageId.isNullOrBlank()) {
                        if (handledMessageIds.contains(messageId)) {
                            return
                        } else {
                            if (handledMessageIds.size > 200) {
                                handledMessageIds.removeAt(0)
                            }
                            handledMessageIds.add(messageId)
                        }
                    }
                    when (metadata?.optString("message_type")) {
                        "notification" -> {
                            pongReceived = true
                            val payload = json.optJSONObject("payload")
                            val event = payload?.optJSONObject("event")
                            if (event != null) {
                                when (metadata.optString("subscription_type")) {
                                    "channel.chat.message" -> listener.onChatMessage(event, timestamp)
                                    "channel.chat.notification" -> listener.onUserNotice(event, timestamp)
                                    "channel.chat.clear" -> listener.onClearChat(event, timestamp)
                                    "channel.chat_settings.update" -> listener.onRoomState(event, timestamp)
                                }
                            }
                        }
                        "session_keepalive" -> pongReceived = true
                        "session_reconnect" -> {
                            val payload = json.optJSONObject("payload")
                            val session = payload?.optJSONObject("session")
                            val reconnectUrl = if (session?.isNull("reconnect_url") == false) session.optString("reconnect_url").takeIf { it.isNotBlank() } else null
                            usingReconnectUrl = reconnectUrl != null
                            reconnect(reconnectUrl)
                        }
                        "session_welcome" -> {
                            val payload = json.optJSONObject("payload")
                            val session = payload?.optJSONObject("session")
                            if (session?.isNull("keepalive_timeout_seconds") == false) {
                                session.optInt("keepalive_timeout_seconds").takeIf { it > 0 }?.let { timeout = it }
                            }
                            checkPong()
                            if (!usingReconnectUrl) {
                                val sessionId = session?.optString("id")
                                if (!sessionId.isNullOrBlank()) {
                                    listener.onWelcomeMessage(sessionId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}
