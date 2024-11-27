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

class ChatWriteWebSocket(
    private val userLogin: String?,
    private val userToken: String?,
    channelName: String,
    private val client: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val listener: ChatListener) {
    private val hashChannelName: String = "#$channelName"
    private var socket: WebSocket? = null
    private var isActive = false
    private var pongReceived = false

    fun connect() {
        socket = client.newWebSocket(Request.Builder().url("wss://irc-ws.chat.twitch.tv").build(), ChatWriteWebSocketListener())
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

    private fun ping() {
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

    fun send(message: CharSequence, replyId: String?) {
        val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
        write("${reply}PRIVMSG $hashChannelName :$message")
    }

    private fun write(message: String) {
        socket?.send(message + System.lineSeparator())
    }

    private inner class ChatWriteWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isActive = true
            write("CAP REQ :twitch.tv/tags twitch.tv/commands")
            write("PASS oauth:$userToken")
            write("NICK $userLogin")
            write("JOIN $hashChannelName")
            ping()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.removeSuffix("\r\n").split("\r\n").forEach {
                it.run {
                    when {
                        contains("PRIVMSG") -> {}
                        contains("USERNOTICE") -> {}
                        contains("CLEARMSG") -> {}
                        contains("CLEARCHAT") -> {}
                        contains("NOTICE") -> listener.onNotice(this)
                        contains("ROOMSTATE") -> {}
                        contains("USERSTATE") -> listener.onUserState(this)
                        startsWith("PING") -> write("PONG")
                        startsWith("PONG") -> pongReceived = true
                        startsWith("RECONNECT") -> reconnect()
                    }
                }
            }
        }
    }
}
