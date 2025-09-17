package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.util.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Timer
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.schedule

class ChatWriteWebSocket(
    private val userLogin: String?,
    private val userToken: String?,
    private val channelName: String,
    private val onConnect: (() -> Unit),
    private val onDisconnect: ((String, String) -> Unit),
    private val onNotice: (String) -> Unit,
    private val onUserState: (String) -> Unit,
    private val trustManager: X509TrustManager?,
    private val coroutineScope: CoroutineScope,
) {
    private var webSocket: WebSocket? = null
    private var pingTimer: Timer? = null
    private var pongTimer: Timer? = null

    fun connect() {
        webSocket = WebSocket("wss://irc-ws.chat.twitch.tv", trustManager, ChatWriteWebSocketListener())
        coroutineScope.launch {
            webSocket?.start()
        }
    }

    suspend fun disconnect() {
        pingTimer?.cancel()
        pongTimer?.cancel()
        webSocket?.stop()
    }

    private fun startPingTimer() {
        pingTimer = Timer().apply {
            schedule(270000) {
                coroutineScope.launch {
                    webSocket?.write("PING")
                }
                startPongTimer()
            }
        }
    }

    private fun startPongTimer() {
        pongTimer = Timer().apply {
            schedule(10000) {
                coroutineScope.launch {
                    webSocket?.disconnect()
                }
            }
        }
    }

    fun send(message: CharSequence, replyId: String?) {
        val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
        coroutineScope.launch {
            webSocket?.write("${reply}PRIVMSG #$channelName :$message")
        }
    }

    private inner class ChatWriteWebSocketListener : WebSocket.Listener {
        override fun onOpen(webSocket: WebSocket) {
            coroutineScope.launch {
                webSocket.write("CAP REQ :twitch.tv/tags twitch.tv/commands")
                webSocket.write("PASS oauth:$userToken")
                webSocket.write("NICK $userLogin")
                webSocket.write("JOIN #$channelName")
            }
            onConnect()
            pingTimer?.cancel()
            pongTimer?.cancel()
            startPingTimer()
        }

        override fun onMessage(webSocket: WebSocket, message: String) {
            message.removeSuffix("\r\n").split("\r\n").forEach {
                it.run {
                    when {
                        contains("PRIVMSG") -> {}
                        contains("USERNOTICE") -> {}
                        contains("CLEARMSG") -> {}
                        contains("CLEARCHAT") -> {}
                        contains("NOTICE") -> onNotice(this)
                        contains("ROOMSTATE") -> {}
                        contains("USERSTATE") -> onUserState(this)
                        startsWith("PING") -> {
                            coroutineScope.launch {
                                webSocket.write("PONG")
                            }
                        }
                        startsWith("PONG") -> {
                            pingTimer?.cancel()
                            pongTimer?.cancel()
                            startPingTimer()
                        }
                        startsWith("RECONNECT") -> {
                            pingTimer?.cancel()
                            pongTimer?.cancel()
                            coroutineScope.launch {
                                webSocket.disconnect()
                            }
                        }
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable) {
            onDisconnect(throwable.toString(), throwable.stackTraceToString())
        }
    }
}
