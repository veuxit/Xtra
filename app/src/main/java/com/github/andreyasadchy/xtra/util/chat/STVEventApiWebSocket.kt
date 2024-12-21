package com.github.andreyasadchy.xtra.util.chat

import android.graphics.Color
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class STVEventApiWebSocket(
    private val channelId: String,
    private val client: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val onPaintUpdate: (NamePaint) -> Unit,
    private val onUserUpdate: (String, String) -> Unit,
    private val onUpdatePresence: (String) -> Unit) {
    private var socket: WebSocket? = null
    private var isActive = false

    fun connect() {
        socket = client.newWebSocket(Request.Builder().url("wss://events.7tv.io/v3").build(), STVEventApiWebSocketListener())
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

    private fun listen(type: String) {
        val message = JSONObject().apply {
            put("op", OPCODE_SUBSCRIBE)
            put("d", JSONObject().apply {
                put("type", type)
                put("condition", JSONObject().apply {
                    put("ctx", "channel")
                    put("platform", "TWITCH")
                    put("id", channelId)
                })
            })
        }.toString()
        socket?.send(message)
    }

    private inner class STVEventApiWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isActive = true
            listOf(
                "cosmetic.*",
                "entitlement.*",
            ).forEach {
                listen(it)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = if (text.isNotBlank()) JSONObject(text) else null
                when (json?.optInt("op")) {
                    OPCODE_DISPATCH -> {
                        val data = json.optJSONObject("d")
                        val type = data?.optString("type")
                        val body = data?.optJSONObject("body")
                        if (type != null && body != null) {
                            when (type) {
                                "cosmetic.create" -> {
                                    val obj = body.optJSONObject("object")
                                    val kind = obj?.optString("kind")
                                    val objectData = obj?.optJSONObject("data")
                                    if (kind != null && objectData != null) {
                                        when (kind) {
                                            "PAINT" -> {
                                                val id = objectData.optString("id")
                                                val function = objectData.optString("function")
                                                val shadows = mutableListOf<NamePaint.Shadow>()
                                                val shadowsArray = objectData.optJSONArray("shadows")
                                                if (shadowsArray != null) {
                                                    for (i in 0 until shadowsArray.length()) {
                                                        val shadowObject = shadowsArray.get(i) as? JSONObject
                                                        val xOffset = shadowObject?.optDouble("x_offset")?.toFloat()
                                                        val yOffset = shadowObject?.optDouble("y_offset")?.toFloat()
                                                        val radius = shadowObject?.optDouble("radius")?.toFloat()
                                                        val color = shadowObject?.optInt("color")
                                                        if (xOffset != null && yOffset != null && radius != null && color != null) {
                                                            shadows.add(NamePaint.Shadow(xOffset, yOffset, radius, parseRGBAColor(color)))
                                                        }
                                                    }
                                                }
                                                when (function) {
                                                    "LINEAR_GRADIENT", "RADIAL_GRADIENT" -> {
                                                        val colors = mutableListOf<Int>()
                                                        val positions = mutableListOf<Float>()
                                                        val stopsArray = objectData.optJSONArray("stops")
                                                        if (stopsArray != null) {
                                                            for (i in 0 until stopsArray.length()) {
                                                                val stopObject = stopsArray.get(i) as? JSONObject
                                                                val position = stopObject?.optDouble("at")?.toFloat()
                                                                val color = stopObject?.optInt("color")
                                                                if (color != null && position != null) {
                                                                    colors.add(parseRGBAColor(color))
                                                                    positions.add(position)
                                                                }
                                                            }
                                                        }
                                                        onPaintUpdate(NamePaint(
                                                            id = id,
                                                            type = function,
                                                            colors = colors.toIntArray(),
                                                            colorPositions = positions.toFloatArray(),
                                                            angle = objectData.optInt("angle"),
                                                            repeat = objectData.optBoolean("repeat"),
                                                            shadows = shadows,
                                                        ))
                                                    }
                                                    "URL" -> {
                                                        val imageUrl = objectData.optString("image_url")
                                                        if (imageUrl != null) {
                                                            onPaintUpdate(NamePaint(
                                                                id = id,
                                                                type = function,
                                                                imageUrl = imageUrl,
                                                                shadows = shadows,
                                                            ))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                "entitlement.create" -> {
                                    val obj = body.optJSONObject("object")
                                    val kind = obj?.optString("kind")
                                    val user = obj?.optJSONObject("user")
                                    if (kind != null && user != null) {
                                        var userId: String? = null
                                        val connections = user.optJSONArray("connections")
                                        if (connections != null) {
                                            for (i in 0 until connections.length()) {
                                                val connection = connections.get(i) as? JSONObject
                                                if (connection?.optString("platform") == "TWITCH") {
                                                    userId = connection.optString("id")
                                                    break
                                                }
                                            }
                                        }
                                        if (userId != null) {
                                            val style = user.optJSONObject("style")
                                            when (kind) {
                                                "PAINT" -> {
                                                    val paintId = style?.optString("paint_id")
                                                    if (paintId != null) {
                                                        onUserUpdate(userId, paintId)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OPCODE_HELLO -> {
                        val data = json.optJSONObject("d")
                        val sessionId = data?.optString("session_id")
                        if (sessionId != null) {
                            onUpdatePresence(sessionId)
                        }
                    }
                    OPCODE_RECONNECT -> reconnect()
                }
            } catch (e: Exception) {

            }
        }
    }

    private fun parseRGBAColor(value: Int): Int {
        return Color.argb(value and 0xFF, value shr 24 and 0xFF, value shr 16 and 0xFF, value shr 8 and 0xFF)
    }

    companion object {
        private const val OPCODE_DISPATCH = 0
        private const val OPCODE_HELLO = 1
        private const val OPCODE_RECONNECT = 4
        private const val OPCODE_SUBSCRIBE = 35
    }
}
