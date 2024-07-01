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
import java.util.Random

class ChatReadWebSocket(
    private val loggedIn: Boolean,
    channelName: String,
    private val client: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val listener: ChatListener? = null,
    private val webSocketListener: WebSocketListener? = null) {
    val hashChannelName: String = "#$channelName"
    private var socket: WebSocket? = null
    var isActive = false
    var pongReceived = false

    fun connect() {
        socket = client.newWebSocket(Request.Builder().url("wss://irc-ws.chat.twitch.tv").build(), webSocketListener ?: ChatReadWebSocketListener())
    }

    fun disconnect() {
        isActive = false
        socket?.close(1000, null)
    }

    fun reconnect() {
        if (isActive) {
            coroutineScope.launch {
                disconnect()
                delay(1000)
                connect()
            }
        }
    }

    fun ping() {
        if (isActive) {
            write("PING")
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

    fun write(message: String) {
        socket?.send(message + System.lineSeparator())
    }

    private inner class ChatReadWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isActive = true
            write("CAP REQ :twitch.tv/tags twitch.tv/commands")
            write("NICK justinfan${Random().nextInt(((9999 - 1000) + 1)) + 1000}") //random number between 1000 and 9999
            write("JOIN $hashChannelName")
            listener?.onConnect()
            ping()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.removeSuffix("\r\n").split("\r\n").forEach {
                it.run {
                    when {
                        contains("PRIVMSG") -> listener?.onChatMessage(this, false)
                        contains("USERNOTICE") -> listener?.onChatMessage(this, true)
                        contains("CLEARMSG") -> listener?.onClearMessage(this)
                        contains("CLEARCHAT") -> listener?.onClearChat(this)
                        contains("NOTICE") -> {
                            if (!loggedIn) {
                                listener?.onNotice(this)
                            }
                        }
                        contains("ROOMSTATE") -> listener?.onRoomState(this)
                        startsWith("PING") -> write("PONG")
                        startsWith("PONG") -> pongReceived = true
                        startsWith("RECONNECT") -> reconnect()
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            listener?.onDisconnect(t.toString(), t.stackTraceToString())
        }
    }
}
