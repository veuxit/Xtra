package com.github.andreyasadchy.xtra.util.chat

interface ChatListener {
    fun onConnect()
    fun onDisconnect(message: String, fullMsg: String)
    fun onSendMessageError(message: String, fullMsg: String)
    fun onChatMessage(message: String, userNotice: Boolean)
    fun onClearMessage(message: String)
    fun onClearChat(message: String)
    fun onNotice(message: String)
    fun onRoomState(message: String)
    fun onUserState(message: String)
}

data class RoomState(
    val emote: String?,
    val followers: String?,
    val unique: String?,
    val slow: String?,
    val subs: String?)
