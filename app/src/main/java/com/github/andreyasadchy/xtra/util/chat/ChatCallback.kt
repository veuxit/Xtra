package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote

interface ChatCallback {
    fun onRewardMessage(message: ChatMessage)
    fun onCommand(list: Command)
    fun onRoomState(list: RoomState)
    fun onUserState(emoteSets: List<String>?)
}

data class Command(
    val message: String? = null,
    val duration: String? = null,
    val type: String? = null,
    val emotes: List<TwitchEmote>? = null,
    val timestamp: Long? = null,
    val fullMsg: String? = null)

data class RoomState(
    val emote: String?,
    val followers: String?,
    val unique: String?,
    val slow: String?,
    val subs: String?)
