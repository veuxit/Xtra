package com.github.andreyasadchy.xtra.model.helix.follows

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FollowDeserializer : JsonDeserializer<FollowResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FollowResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        val total = json.asJsonObject?.get("total")?.takeIf { !it.isJsonNull }?.asInt
        val cursor = json.asJsonObject?.get("pagination")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("to_id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("to_login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("to_name")?.takeIf { !it.isJsonNull }?.asString,
                    followedAt = obj.get("followed_at")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return FollowResponse(data, total, cursor)
    }
}
