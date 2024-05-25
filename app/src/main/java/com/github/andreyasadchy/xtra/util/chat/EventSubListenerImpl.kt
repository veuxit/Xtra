package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import org.json.JSONObject

class EventSubListenerImpl(
    private val callbackMessage: OnChatMessageReceivedListener,
    private val callbackChat: ChatCallback,
    private val callback: EventSubCallback,
    private val showUserNotice: Boolean,
    private val showClearChat: Boolean,
    private val usePubSub: Boolean) : EventSubWebSocket.OnMessageReceivedListener {

    override fun onWelcomeMessage(sessionId: String) {
        callback.onWelcomeMessage(sessionId)
    }

    override fun onMessage(json: JSONObject, timestamp: String?) {
        val message = StringBuilder()
        val messageObj = json.optJSONObject("message")
        val messageText = if (messageObj?.isNull("text") == false) messageObj.optString("text").takeIf { it.isNotBlank() } else null
        val emotesList = mutableListOf<TwitchEmote>()
        val fragments = messageObj?.optJSONArray("fragments")
        if (fragments != null) {
            for (i in 0 until fragments.length()) {
                val fragment = fragments.get(i) as? JSONObject
                val type = if (fragment?.isNull("type") == false) fragment.optString("type").takeIf { it.isNotBlank() } else null
                val fragmentText = if (fragment?.isNull("text") == false) fragment.optString("text").takeIf { it.isNotBlank() } else null
                if (!fragmentText.isNullOrBlank()) {
                    if (type.equals("emote", true)) {
                        val emote = fragment?.optJSONObject("emote")
                        val id = if (emote?.isNull("id") == false) emote.optString("id").takeIf { it.isNotBlank() } else null
                        if (!id.isNullOrBlank()) {
                            emotesList.add(TwitchEmote(
                                id = id,
                                begin = message.codePointCount(0, message.length),
                                end = message.codePointCount(0, message.length) + fragmentText.lastIndex,
                                setId = if (emote?.isNull("emote_set_id") == false) emote.optString("emote_set_id").takeIf { it.isNotBlank() } else null,
                                ownerId = if (emote?.isNull("owner_id") == false) emote.optString("owner_id").takeIf { it.isNotBlank() } else null
                            ))
                        }
                    }
                    message.append(fragmentText)
                }
            }
        }
        val badgesList = mutableListOf<Badge>()
        val badges = json.optJSONArray("badges")
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val badge = badges.get(i) as? JSONObject
                val set = if (badge?.isNull("set_id") == false) badge.optString("set_id").takeIf { it.isNotBlank() } else null
                val id = if (badge?.isNull("id") == false) badge.optString("id").takeIf { it.isNotBlank() } else null
                if (!set.isNullOrBlank() && !id.isNullOrBlank()) {
                    badgesList.add(Badge(set, id))
                }
            }
        }
        val rewardId = if (!json.isNull("channel_points_custom_reward_id")) json.optString("channel_points_custom_reward_id").takeIf { it.isNotBlank() } else null
        val chatMessage = ChatMessage(
            id = if (!json.isNull("message_id")) json.optString("message_id").takeIf { it.isNotBlank() } else null,
            userId = if (!json.isNull("chatter_user_id")) json.optString("chatter_user_id").takeIf { it.isNotBlank() } else null,
            userLogin = if (!json.isNull("chatter_user_login")) json.optString("chatter_user_login").takeIf { it.isNotBlank() } else null,
            userName = if (!json.isNull("chatter_user_name")) json.optString("chatter_user_name").takeIf { it.isNotBlank() } else null,
            message = message.toString(),
            color = if (!json.isNull("color")) json.optString("color").takeIf { it.isNotBlank() } else null,
            emotes = emotesList,
            badges = badgesList,
            isAction = messageText?.startsWith(ChatListenerImpl.ACTION) == true,
            bits = json.optJSONObject("cheer")?.let { cheer -> if (!cheer.isNull("bits")) cheer.optInt("bits").takeIf { it > 0 } else null },
            msgId = if (!json.isNull("message_type")) json.optString("message_type").takeIf { it.isNotBlank() && !it.equals("text", true) } else null,
            reward = rewardId?.let { ChannelPointReward(id = it) },
            timestamp = timestamp?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
            fullMsg = json.toString()
        )
        if (rewardId.isNullOrBlank() || !usePubSub) {
            callbackMessage.onMessage(chatMessage)
        } else {
            callbackChat.onRewardMessage(chatMessage)
        }
    }

    override fun onUserNotice(json: JSONObject, timestamp: String?) {
        if (showUserNotice) {
            val message = StringBuilder()
            val messageObj = json.optJSONObject("message")
            val messageText = if (messageObj?.isNull("text") == false) messageObj.optString("text").takeIf { it.isNotBlank() } else null
            val systemMsg = if (!json.isNull("system_message")) json.optString("system_message").takeIf { it.isNotBlank() } else null
            if (messageText != null) {
                val emotesList = mutableListOf<TwitchEmote>()
                val fragments = messageObj?.optJSONArray("fragments")
                if (fragments != null) {
                    for (i in 0 until fragments.length()) {
                        val fragment = fragments.get(i) as? JSONObject
                        val type = if (fragment?.isNull("type") == false) fragment.optString("type").takeIf { it.isNotBlank() } else null
                        val fragmentText = if (fragment?.isNull("text") == false) fragment.optString("text").takeIf { it.isNotBlank() } else null
                        if (!fragmentText.isNullOrBlank()) {
                            if (type.equals("emote", true)) {
                                val emote = fragment?.optJSONObject("emote")
                                val id = if (emote?.isNull("id") == false) emote.optString("id").takeIf { it.isNotBlank() } else null
                                if (!id.isNullOrBlank()) {
                                    emotesList.add(TwitchEmote(
                                        id = id,
                                        begin = message.codePointCount(0, message.length),
                                        end = message.codePointCount(0, message.length) + fragmentText.lastIndex,
                                        setId = if (emote?.isNull("emote_set_id") == false) emote.optString("emote_set_id").takeIf { it.isNotBlank() } else null,
                                        ownerId = if (emote?.isNull("owner_id") == false) emote.optString("owner_id").takeIf { it.isNotBlank() } else null
                                    ))
                                }
                            }
                            message.append(fragmentText)
                        }
                    }
                }
                val badgesList = mutableListOf<Badge>()
                val badges = json.optJSONArray("badges")
                if (badges != null) {
                    for (i in 0 until badges.length()) {
                        val badge = badges.get(i) as? JSONObject
                        val set = if (badge?.isNull("set_id") == false) badge.optString("set_id").takeIf { it.isNotBlank() } else null
                        val id = if (badge?.isNull("id") == false) badge.optString("id").takeIf { it.isNotBlank() } else null
                        if (!set.isNullOrBlank() && !id.isNullOrBlank()) {
                            badgesList.add(Badge(set, id))
                        }
                    }
                }
                callbackMessage.onMessage(ChatMessage(
                    id = if (!json.isNull("message_id")) json.optString("message_id").takeIf { it.isNotBlank() } else null,
                    userId = if (!json.isNull("chatter_user_id")) json.optString("chatter_user_id").takeIf { it.isNotBlank() } else null,
                    userLogin = if (!json.isNull("chatter_user_login")) json.optString("chatter_user_login").takeIf { it.isNotBlank() } else null,
                    userName = if (!json.isNull("chatter_user_name")) json.optString("chatter_user_name").takeIf { it.isNotBlank() } else null,
                    message = message.toString(),
                    color = if (!json.isNull("color")) json.optString("color").takeIf { it.isNotBlank() } else null,
                    emotes = emotesList,
                    badges = badgesList,
                    isAction = messageText.startsWith(ChatListenerImpl.ACTION),
                    systemMsg = systemMsg,
                    msgId = if (!json.isNull("notice_type")) json.optString("notice_type").takeIf { it.isNotBlank() } else null,
                    timestamp = timestamp?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                    fullMsg = json.toString()
                ))
            } else {
                callbackChat.onCommand(Command(
                    message = systemMsg,
                    timestamp = timestamp?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                    fullMsg = json.toString()
                ))
            }
        }
    }

    override fun onClearChat(json: JSONObject, timestamp: String?) {
        if (showClearChat) {
            callbackChat.onCommand(Command(
                type = "clearchat",
                timestamp = timestamp?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                fullMsg = json.toString()
            ))
        }
    }

    override fun onRoomState(json: JSONObject, timestamp: String?) {
        val emote = if (!json.isNull("emote_mode")) json.optBoolean("emote_mode") else null
        val followers = if (!json.isNull("follower_mode")) json.optBoolean("follower_mode") else null
        val followersDuration = if (!json.isNull("follower_mode_duration_minutes")) json.optInt("follower_mode_duration_minutes") else null
        val slow = if (!json.isNull("slow_mode")) json.optBoolean("slow_mode") else null
        val slowDuration = if (!json.isNull("slow_mode_wait_time_seconds")) json.optInt("slow_mode_wait_time_seconds").takeIf { it > 0 } else null
        val subs = if (!json.isNull("subscriber_mode")) json.optBoolean("subscriber_mode") else null
        val unique = if (!json.isNull("unique_chat_mode")) json.optBoolean("unique_chat_mode") else null
        callbackChat.onRoomState(RoomState(
            emote = if (emote == true) "1" else "0",
            followers = if (followers == true) (followersDuration ?: 0).toString() else "-1",
            unique = if (unique == true) "1" else "0",
            slow = if (slow == true && slowDuration != null) slowDuration.toString() else "0",
            subs = if (subs == true) "1" else "0"
        ))
    }

    override fun onCommand(message: String, duration: String?, type: String?, fullMsg: String?) {
        callbackChat.onCommand(Command(
            message = message,
            duration = duration,
            type = type,
            fullMsg = fullMsg
        ))
    }
}
