package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserBadgesQueryDeserializer : JsonDeserializer<UserBadgesQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserBadgesQueryResponse {
        val data = mutableListOf<TwitchBadge>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonArray("broadcastBadges")
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(TwitchBadge(
                    setId = obj.get("setID").asString,
                    version = obj.get("version").asString,
                    url = obj.get("imageURL").asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return UserBadgesQueryResponse(data)
    }
}
