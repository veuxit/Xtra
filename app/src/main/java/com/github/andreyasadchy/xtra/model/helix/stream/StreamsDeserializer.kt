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
        val cursor = json.takeIf { it.isJsonObject }?.asJsonObject?.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        json.asJsonObject.get("data").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(Stream(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelId = obj.get("user_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelLogin = obj.get("user_login")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelName = obj.get("user_name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    gameId = obj.get("game_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    gameName = obj.get("game_name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    viewerCount = obj.get("viewer_count")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                    startedAt = obj.get("started_at")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    thumbnailUrl = obj.get("thumbnail_url")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    tags = obj.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { tag -> tag.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString }
                ))
            }
        }
        return StreamsResponse(data, cursor)
    }
}
