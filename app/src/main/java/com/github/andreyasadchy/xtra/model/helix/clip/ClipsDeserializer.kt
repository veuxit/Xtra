package com.github.andreyasadchy.xtra.model.helix.clip

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ClipsDeserializer : JsonDeserializer<ClipsResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ClipsResponse {
        val data = mutableListOf<Clip>()
        val cursor = json.takeIf { it.isJsonObject }?.asJsonObject?.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        json.asJsonObject.get("data").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(Clip(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelId = obj.get("broadcaster_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelName = obj.get("broadcaster_name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    videoId = obj.get("video_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    gameId = obj.get("game_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    viewCount = obj.get("view_count")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                    uploadDate = obj.get("created_at")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    thumbnailUrl = obj.get("thumbnail_url")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    duration = obj.get("duration")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asDouble,
                    vodOffset = obj.get("vod_offset")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                ))
            }
        }
        return ClipsResponse(data, cursor)
    }
}
