package com.github.andreyasadchy.xtra.model.helix.channel

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChannelSearchDeserializer : JsonDeserializer<ChannelSearchResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChannelSearchResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        val cursor = json.asJsonObject?.get("pagination")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("broadcaster_login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("display_name")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageUrl = obj.get("thumbnail_url")?.takeIf { !it.isJsonNull }?.asString,
                    isLive = obj.get("is_live")?.takeIf { !it.isJsonNull }?.asBoolean,
                    stream = Stream(
                        gameId = obj.get("game_id")?.takeIf { !it.isJsonNull }?.asString,
                        gameName = obj.get("game_name")?.takeIf { !it.isJsonNull }?.asString,
                        title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                        startedAt = obj.get("started_at")?.takeIf { !it.isJsonNull }?.asString,
                    )
                ))
            }
        }
        return ChannelSearchResponse(data, cursor)
    }
}
