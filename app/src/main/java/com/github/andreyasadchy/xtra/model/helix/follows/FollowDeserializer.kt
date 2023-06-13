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
        val total = json.takeIf { it.isJsonObject }?.asJsonObject?.get("total")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt
        val cursor = json.takeIf { it.isJsonObject }?.asJsonObject?.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        json.asJsonObject.get("data").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("broadcaster_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelLogin = obj.get("broadcaster_login")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelName = obj.get("broadcaster_name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    followedAt = obj.get("followed_at")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                ))
            }
        }
        return FollowResponse(data, total, cursor)
    }
}
