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
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        val cursor = json.asJsonObject?.get("pagination")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(Clip(
                    id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelId = obj.get("broadcaster_id")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("broadcaster_name")?.takeIf { !it.isJsonNull }?.asString,
                    videoId = obj.get("video_id")?.takeIf { !it.isJsonNull }?.asString,
                    gameId = obj.get("game_id")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    viewCount = obj.get("view_count")?.takeIf { !it.isJsonNull }?.asInt,
                    uploadDate = obj.get("created_at")?.takeIf { !it.isJsonNull }?.asString,
                    thumbnailUrl = obj.get("thumbnail_url")?.takeIf { !it.isJsonNull }?.asString,
                    duration = obj.get("duration")?.takeIf { !it.isJsonNull }?.asDouble,
                    vodOffset = obj.get("vod_offset")?.takeIf { !it.isJsonNull }?.asInt,
                ))
            }
        }
        return ClipsResponse(data, cursor)
    }
}
