package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearch
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class SearchChannelsQueryDeserializer : JsonDeserializer<SearchChannelsQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SearchChannelsQueryResponse {
        val data = mutableListOf<ChannelSearch>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("searchUsers")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("searchUsers")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("node")?.let { obj ->
                data.add(ChannelSearch(
                    id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    broadcaster_login = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    display_name = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    is_live = obj.get("stream")?.isJsonObject,
                    thumbnail_url = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                    followers_count = obj.get("followers")?.takeIf { it.isJsonObject }?.asJsonObject?.get("totalCount")?.takeIf { !it.isJsonNull }?.asInt,
                    type = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return SearchChannelsQueryResponse(data, cursor, hasNextPage)
    }
}
