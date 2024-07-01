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
import org.json.JSONArray
import org.json.JSONObject

class PubSubWebSocket(
    private val channelId: String,
    private val userId: String?,
    private val gqlToken: String?,
    private val collectPoints: Boolean,
    private val notifyPoints: Boolean,
    private val showRaids: Boolean,
    private val client: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val listener: PubSubListener) {
    private var socket: WebSocket? = null
    private var isActive = false
    private var pongReceived = false

    fun connect() {
        socket = client.newWebSocket(Request.Builder().url("wss://pubsub-edge.twitch.tv").build(), PubSubWebSocketListener())
    }

    fun disconnect() {
        isActive = false
        socket?.close(1000, null)
    }

    private fun reconnect() {
        if (isActive) {
            coroutineScope.launch {
                disconnect()
                delay(1000)
                connect()
            }
        }
    }

    private fun listen() {
        val message = JSONObject().apply {
            put("type", "LISTEN")
            put("data", JSONObject().apply {
                put("topics", JSONArray().apply {
                    put("video-playback-by-id.$channelId")
                    put("broadcast-settings-update.$channelId")
                    put("community-points-channel-v1.$channelId")
                    if (showRaids) {
                        put("raid.$channelId")
                    }
                    if (!userId.isNullOrBlank() && !gqlToken.isNullOrBlank()) {
                        if (collectPoints) {
                            put("community-points-user-v1.$userId")
                        }
                    }
                })
                if (!userId.isNullOrBlank() && !gqlToken.isNullOrBlank() && collectPoints) {
                    put("auth_token", gqlToken)
                }
            })
        }.toString()
        socket?.send(message)
    }

    private fun ping() {
        if (isActive) {
            val ping = JSONObject().apply { put("type", "PING") }.toString()
            socket?.send(ping)
            checkPong()
        }
    }

    private fun checkPong() {
        tickerFlowPong().onCompletion {
            if (isActive) {
                if (pongReceived) {
                    pongReceived = false
                    checkPongWait()
                } else {
                    reconnect()
                }
            }
        }.launchIn(coroutineScope)
    }

    private fun tickerFlowPong() = flow {
        for (i in 10 downTo 0) {
            if (pongReceived || !isActive) {
                emit(i downTo 0)
            } else {
                emit(i)
                delay(1000)
            }
        }
    }

    private fun checkPongWait() {
        tickerFlowActive(270).onCompletion {
            if (isActive) {
                ping()
            }
        }.launchIn(coroutineScope)
    }

    private fun minuteWatched() {
        tickerFlowActive(60).onCompletion {
            if (isActive) {
                listener.onMinuteWatched()
                minuteWatched()
            }
        }.launchIn(coroutineScope)
    }

    private fun tickerFlowActive(seconds: Int) = flow {
        for (i in seconds downTo 0) {
            if (!isActive) {
                emit(i downTo 0)
            } else {
                emit(i)
                delay(1000)
            }
        }
    }

    private inner class PubSubWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isActive = true
            listen()
            ping()
            if (collectPoints && !userId.isNullOrBlank() && !gqlToken.isNullOrBlank()) {
                minuteWatched()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = if (text.isNotBlank()) JSONObject(text) else null
                when (json?.optString("type")) {
                    "MESSAGE" -> {
                        val data = json.optJSONObject("data")
                        val topic = data?.optString("topic")
                        val message = data?.optString("message")?.let { if (it.isNotBlank()) JSONObject(it) else null }
                        val messageType = message?.optString("type")
                        if (topic != null && messageType != null) {
                            when {
                                topic.startsWith("video-playback-by-id") -> listener.onPlaybackMessage(message)
                                topic.startsWith("broadcast-settings-update") && messageType.startsWith("broadcast_settings_update") -> listener.onTitleUpdate(message)
                                topic.startsWith("community-points-channel") && messageType.startsWith("reward-redeemed") -> listener.onRewardMessage(message)
                                topic.startsWith("community-points-user") -> {
                                    when {
                                        messageType.startsWith("points-earned") && notifyPoints -> {
                                            val messageData = message.optJSONObject("data")
                                            val messageChannelId = messageData?.optString("channel_id")
                                            if (channelId == messageChannelId) {
                                                listener.onPointsEarned(message)
                                            }
                                        }
                                        messageType.startsWith("claim-available") && collectPoints -> listener.onClaimAvailable()
                                    }
                                }
                                topic.startsWith("raid") && showRaids -> {
                                    when {
                                        messageType.startsWith("raid_update") -> listener.onRaidUpdate(message, false)
                                        messageType.startsWith("raid_go") -> listener.onRaidUpdate(message, true)
                                    }
                                }
                            }
                        }
                    }
                    "PONG" -> pongReceived = true
                    "RECONNECT" -> reconnect()
                }
            } catch (e: Exception) {

            }
        }
    }
}
