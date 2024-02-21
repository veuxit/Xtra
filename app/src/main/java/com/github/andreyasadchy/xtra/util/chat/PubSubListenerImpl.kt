package com.github.andreyasadchy.xtra.util.chat

import com.github.andreyasadchy.xtra.model.chat.PubSubPointReward
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import org.json.JSONObject

class PubSubListenerImpl(
    private val callbackMessage: OnChatMessageReceivedListener,
    private val callback: PubSubCallback) : PubSubWebSocket.OnMessageReceivedListener {

    override fun onPlaybackMessage(message: JSONObject) {
        val messageType = message.optString("type")
        when {
            messageType.startsWith("viewcount") -> callback.onPlaybackMessage(PlaybackMessage(viewers = if (!message.isNull("viewers")) message.optInt("viewers") else null))
            messageType.startsWith("stream-up") -> callback.onPlaybackMessage(PlaybackMessage(true, if (!message.isNull("server_time")) message.optLong("server_time").takeIf { it > 0 } else null))
            messageType.startsWith("stream-down") -> callback.onPlaybackMessage(PlaybackMessage(false))
        }
    }

    override fun onTitleUpdate(message: JSONObject) {
        callback.onTitleUpdate(BroadcastSettings(
            title = if (!message.isNull("status")) message.optString("status").takeIf { it.isNotBlank() } else null,
            gameId = if (!message.isNull("game_id")) message.optInt("game_id").takeIf { it > 0 }?.toString() else null,
            gameName = if (!message.isNull("game")) message.optString("game").takeIf { it.isNotBlank() } else null,
        ))
    }

    override fun onRewardMessage(message: JSONObject) {
        val messageData = message.optJSONObject("data")
        val redemption = messageData?.optJSONObject("redemption")
        val user = redemption?.optJSONObject("user")
        val reward = redemption?.optJSONObject("reward")
        val rewardImage = reward?.optJSONObject("image")
        val defaultImage = reward?.optJSONObject("default_image")
        val input = if (redemption?.isNull("user_input") == false) redemption.optString("user_input").takeIf { it.isNotBlank() } else null
        val pointReward = PubSubPointReward(
            id = if (reward?.isNull("id") == false) reward.optString("id").takeIf { it.isNotBlank() } else null,
            userId = if (user?.isNull("id") == false) user.optString("id").takeIf { it.isNotBlank() } else null,
            userLogin = if (user?.isNull("login") == false) user.optString("login").takeIf { it.isNotBlank() } else null,
            userName = if (user?.isNull("display_name") == false) user.optString("display_name").takeIf { it.isNotBlank() } else null,
            message = input,
            fullMsg = message.toString(),
            rewardTitle = if (reward?.isNull("title") == false) reward.optString("title").takeIf { it.isNotBlank() } else null,
            rewardCost = if (reward?.isNull("cost") == false) reward.optInt("cost") else null,
            rewardImage = PubSubPointReward.RewardImage(
                url1 = if (rewardImage?.isNull("url_1x") == false) rewardImage.optString("url_1x").takeIf { it.isNotBlank() } else null
                    ?: if (defaultImage?.isNull("url_1x") == false) defaultImage.optString("url_1x").takeIf { it.isNotBlank() } else null,
                url2 = if (rewardImage?.isNull("url_2x") == false) rewardImage.optString("url_2x").takeIf { it.isNotBlank() } else null
                    ?: if (defaultImage?.isNull("url_2x") == false) defaultImage.optString("url_2x").takeIf { it.isNotBlank() } else null,
                url4 = if (rewardImage?.isNull("url_4x") == false) rewardImage.optString("url_4x").takeIf { it.isNotBlank() } else null
                    ?: if (defaultImage?.isNull("url_4x") == false) defaultImage.optString("url_4x").takeIf { it.isNotBlank() } else null,
            ),
            timestamp = if (messageData?.isNull("timestamp") == false) messageData.optString("timestamp").takeIf { it.isNotBlank() }?.let { TwitchApiHelper.parseIso8601Date(it) } else null,
        )
        if (input.isNullOrBlank()) {
            callbackMessage.onMessage(pointReward)
        } else {
            callback.onRewardMessage(pointReward)
        }
    }

    override fun onPointsEarned(message: JSONObject) {
        val messageData = message.optJSONObject("data")
        val pointGain = messageData?.optJSONObject("point_gain")
        callback.onPointsEarned(PointsEarned(
            pointsGained = pointGain?.optInt("total_points"),
            timestamp = if (messageData?.isNull("timestamp") == false) messageData.optString("timestamp").takeIf { it.isNotBlank() }?.let { TwitchApiHelper.parseIso8601Date(it) } else null,
            fullMsg = message.toString()
        ))
    }

    override fun onClaimAvailable() {
        callback.onClaimAvailable()
    }

    override fun onMinuteWatched() {
        callback.onMinuteWatched()
    }

    override fun onRaidUpdate(message: JSONObject, openStream: Boolean) {
        val raid = message.optJSONObject("raid")
        if (raid != null) {
            callback.onRaidUpdate(Raid(
                raidId = if (!raid.isNull("id")) raid.optString("id").takeIf { it.isNotBlank() } else null,
                targetId = if (!raid.isNull("target_id")) raid.optString("target_id").takeIf { it.isNotBlank() } else null,
                targetLogin = if (!raid.isNull("target_login")) raid.optString("target_login").takeIf { it.isNotBlank() } else null,
                targetName = if (!raid.isNull("target_display_name")) raid.optString("target_display_name").takeIf { it.isNotBlank() } else null,
                targetProfileImage = if (!raid.isNull("target_profile_image")) raid.optString("target_profile_image").takeIf { it.isNotBlank() }?.replace("profile_image-%s", "profile_image-300x300") else null,
                viewerCount = raid.optInt("viewer_count"),
                openStream = openStream
            ))
        }
    }
}
