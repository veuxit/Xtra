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
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("searchFor")?.getAsJsonObject("channels")
        val cursor = dataJson?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.getAsJsonArray("edges")?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("item")?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageUrl = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                    followersCount = obj.get("followers")?.takeIf { it.isJsonObject }?.asJsonObject?.get("totalCount")?.takeIf { !it.isJsonNull }?.asInt,
                    type = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { !it.isJsonNull }?.asString,
                    isLive = obj.get("stream")?.isJsonObject
                ))
            }
        }
        return SearchChannelDataResponse(data, cursor)
    }
}
