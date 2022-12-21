package com.github.andreyasadchy.xtra.model.helix.video

import com.github.andreyasadchy.xtra.model.ui.Video
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class VideosDeserializer : JsonDeserializer<VideosResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VideosResponse {
        val data = mutableListOf<Video>()
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        val cursor = json.asJsonObject?.get("pagination")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(Video(
                    id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelId = obj.get("user_id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("user_login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("user_name")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    uploadDate = obj.get("created_at")?.takeIf { !it.isJsonNull }?.asString,
                    thumbnailUrl = obj.get("thumbnail_url")?.takeIf { !it.isJsonNull }?.asString,
                    viewCount = obj.get("view_count")?.takeIf { !it.isJsonNull }?.asInt,
                    duration = obj.get("duration")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return VideosResponse(data, cursor)
    }
}
