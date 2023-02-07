package com.github.andreyasadchy.xtra.model.helix.stream

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class StreamsDeserializer : JsonDeserializer<StreamsResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): StreamsResponse {
        val data = mutableListOf<Stream>()
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        val cursor = json.asJsonObject?.get("pagination")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(Stream(
                    id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelId = obj.get("user_id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("user_login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("user_name")?.takeIf { !it.isJsonNull }?.asString,
                    gameId = obj.get("game_id")?.takeIf { !it.isJsonNull }?.asString,
                    gameName = obj.get("game_name")?.takeIf { !it.isJsonNull }?.asString,
                    type = obj.get("type")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    viewerCount = obj.get("viewer_count")?.takeIf { !it.isJsonNull }?.asInt,
                    startedAt = obj.get("started_at")?.takeIf { !it.isJsonNull }?.asString,
                    thumbnailUrl = obj.get("thumbnail_url")?.takeIf { !it.isJsonNull }?.asString,
                    tags = obj.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it?.takeIf { !it.isJsonNull }?.asString }
                ))
            }
        }
        return StreamsResponse(data, cursor)
    }
}
