package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UsersLastBroadcastQueryDeserializer : JsonDeserializer<UsersLastBroadcastQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UsersLastBroadcastQueryResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<User>()
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("users")?.takeIf { it.isJsonArray }?.asJsonArray
        dataJson?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    lastBroadcast = obj.get("lastBroadcast")?.takeIf { it.isJsonObject }?.asJsonObject?.get("startedAt")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    profileImageUrl = obj.get("profileImageURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
                ))
            }
        }
        return UsersLastBroadcastQueryResponse(data)
    }
}
