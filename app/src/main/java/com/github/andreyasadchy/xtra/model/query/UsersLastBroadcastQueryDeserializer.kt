package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.follows.Follow
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UsersLastBroadcastQueryDeserializer : JsonDeserializer<UsersLastBroadcastQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UsersLastBroadcastQueryResponse {
        val data = mutableListOf<Follow>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonArray("users")
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(Follow(
                    to_id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    lastBroadcast = obj.get("lastBroadcast")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("startedAt")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageURL = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString
                ))
            }
        }
        return UsersLastBroadcastQueryResponse(data)
    }
}
