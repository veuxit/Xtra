package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class SearchChannelDataDeserializer : JsonDeserializer<SearchChannelDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SearchChannelDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("searchFor").asJsonObject.get("channels").asJsonObject
        val cursor = dataJson?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        dataJson.get("edges").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("item")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelLogin = obj.get("login")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelName = obj.get("displayName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    profileImageUrl = obj.get("profileImageURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    followersCount = obj.get("followers")?.takeIf { it.isJsonObject }?.asJsonObject?.get("totalCount")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                    type = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    isLive = obj.get("stream")?.isJsonObject
                ))
            }
        }
        return SearchChannelDataResponse(data, cursor)
    }
}
