package com.github.andreyasadchy.xtra.util.chat

import org.json.JSONObject

interface EventSubListener {
    fun onConnect()
    fun onWelcomeMessage(sessionId: String)
    fun onChatMessage(json: JSONObject, timestamp: String?)
    fun onUserNotice(json: JSONObject, timestamp: String?)
    fun onClearChat(json: JSONObject, timestamp: String?)
    fun onRoomState(json: JSONObject, timestamp: String?)
}
