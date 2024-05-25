package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import kotlin.collections.set

class ChatListenerImpl(
    private val callbackMessage: OnChatMessageReceivedListener,
    private val callback: ChatCallback,
    private val showUserNotice: Boolean,
    private val showClearMsg: Boolean,
    private val showClearChat: Boolean,
    private val usePubSub: Boolean) : ChatReadIRC.OnMessageReceivedListener, ChatWriteIRC.OnMessageReceivedListener, ChatReadWebSocket.OnMessageReceivedListener, ChatWriteWebSocket.OnMessageReceivedListener {
    
    override fun onMessage(message: String, userNotice: Boolean) {
        if (!userNotice || showUserNotice) {
            val parts = message.substring(1).split(" ".toRegex(), 2)
            val prefixes = splitAndMakeMap(parts[0], ";", "=")
            val messageInfo = parts[1] //:<user>!<user>@<user>.tmi.twitch.tv PRIVMSG #<channelName> :<message>
            val userLogin = prefixes["login"] ?: try {
                messageInfo.substring(1, messageInfo.indexOf("!"))
            } catch (e: Exception) {
                null
            }
            val systemMsg = prefixes["system-msg"]?.replace("\\s", " ")
            val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
            if (msgIndex == -1 && userNotice) { // no user message & is user notice
                callback.onCommand(Command(
                    message = systemMsg ?: messageInfo,
                    timestamp = prefixes["tmi-sent-ts"]?.toLong(),
                    fullMsg = message
                ))
            } else {
                val userMessage: String
                val isAction: Boolean
                messageInfo.substring(msgIndex + 1).let { //from <message>
                    if (!it.startsWith(ACTION)) {
                        userMessage = it
                        isAction = false
                    } else {
                        userMessage = it.substring(8, it.lastIndex)
                        isAction = true
                    }
                }
                val emotesList = mutableListOf<TwitchEmote>()
                val emotes = prefixes["emotes"]
                if (emotes != null) {
                    val entries = splitAndMakeMap(emotes, "/", ":").entries
                    entries.forEach { emote ->
                        emote.value?.split(",")?.forEach { indexes ->
                            val index = indexes.split("-")
                            emotesList.add(TwitchEmote(id = emote.key, begin = index[0].toInt(), end = index[1].toInt()))
                        }
                    }
                }
                val badgesList = mutableListOf<Badge>()
                val badges = prefixes["badges"]
                if (badges != null) {
                    val entries = splitAndMakeMap(badges, ",", "/").entries
                    entries.forEach {
                        it.value?.let { value ->
                            badgesList.add(Badge(it.key, value))
                        }
                    }
                }
                val rewardId = prefixes["custom-reward-id"]
                val chatMessage = ChatMessage(
                    id = prefixes["id"],
                    userId = prefixes["user-id"],
                    userLogin = userLogin,
                    userName = prefixes["display-name"]?.replace("\\s", " "),
                    message = userMessage,
                    color = prefixes["color"],
                    emotes = emotesList,
                    badges = badgesList,
                    isAction = isAction,
                    isFirst = prefixes["first-msg"] == "1",
                    bits = prefixes["bits"]?.toIntOrNull(),
                    systemMsg = systemMsg,
                    msgId = prefixes["msg-id"],
                    reward = rewardId?.let { ChannelPointReward(id = it) },
                    timestamp = prefixes["tmi-sent-ts"]?.toLong(),
                    fullMsg = message
                )
                if (rewardId.isNullOrBlank() || !usePubSub) {
                    callbackMessage.onMessage(chatMessage)
                } else {
                    callback.onRewardMessage(chatMessage)
                }
            }
        }
    }

    override fun onCommand(message: String, duration: String?, type: String?, fullMsg: String?) {
        callback.onCommand(Command(
            message = message,
            duration = duration,
            type = type,
            fullMsg = fullMsg
        ))
    }

    override fun onClearMessage(message: String) {
        if (showClearMsg) {
            val parts = message.substring(1).split(" ".toRegex(), 2)
            val prefixes = splitAndMakeMap(parts[0], ";", "=")
            val user = prefixes["login"]
            val messageInfo = parts[1]
            val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
            val msg = if (msgIndex != -1) messageInfo.substring(msgIndex + 1) else null
            callback.onCommand(Command(
                message = user,
                duration = msg,
                type = "clearmsg",
                timestamp = prefixes["tmi-sent-ts"]?.toLong(),
                fullMsg = message
            ))
        }
    }

    override fun onClearChat(message: String) {
        if (showClearChat) {
            val parts = message.substring(1).split(" ".toRegex(), 2)
            val prefixes = splitAndMakeMap(parts[0], ";", "=")
            val duration = prefixes["ban-duration"]
            val messageInfo = parts[1]
            val userIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
            val user = if (userIndex != -1) messageInfo.substring(userIndex + 1) else null
            val type = if (user == null) { "clearchat" } else { if (duration != null) { "timeout" } else { "ban" } }
            callback.onCommand(Command(
                message = user,
                duration = duration,
                type = type,
                timestamp = prefixes["tmi-sent-ts"]?.toLong(),
                fullMsg = message
            ))
        }
    }

    override fun onNotice(message: String) {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        val messageInfo = parts[1]
        val msgId = prefixes["msg-id"]
        callback.onCommand(Command(
            message = messageInfo.substring(messageInfo.indexOf(":", messageInfo.indexOf(":") + 1) + 1),
            duration = msgId,
            type = "notice",
            fullMsg = message
        ))
    }

    override fun onRoomState(message: String) {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        callback.onRoomState(RoomState(
            emote = prefixes["emote-only"],
            followers = prefixes["followers-only"],
            unique = prefixes["r9k"],
            slow = prefixes["slow"],
            subs = prefixes["subs-only"]
        ))
    }

    override fun onUserState(message: String) {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        val sets = prefixes["emote-sets"]
        if (sets != null) {
            val list: List<String> = sets.split(",".toRegex()).dropLastWhile { it.isEmpty() }
            callback.onUserState(list)
        }
    }

    private fun splitAndMakeMap(string: String, splitRegex: String, mapRegex: String): Map<String, String?> {
        val list = string.split(splitRegex.toRegex()).dropLastWhile { it.isEmpty() }
        val map = LinkedHashMap<String, String?>()
        for (pair in list) {
            val kv = pair.split(mapRegex.toRegex()).dropLastWhile { it.isEmpty() }
            map[kv[0]] = if (kv.size == 2) kv[1] else null
        }
        return map
    }
    
    companion object {
        const val ACTION = "\u0001ACTION"
    }
}
