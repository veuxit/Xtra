package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Video
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class VideoQueryDeserializer : JsonDeserializer<VideoQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VideoQueryResponse {
        val data = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("video")?.let { obj ->
            Video(
                id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                channelId = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                channelLogin = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                channelName = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                type = obj.get("broadcastType")?.takeIf { !it.isJsonNull }?.asString,
                title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                uploadDate = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                thumbnailUrl = obj.get("previewThumbnailURL")?.takeIf { !it.isJsonNull }?.asString,
                duration = obj.get("lengthSeconds")?.takeIf { !it.isJsonNull }?.asString,
                gameId = obj.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                gameName = obj.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                profileImageUrl = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                animatedPreviewURL = obj.get("animatedPreviewURL")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        return VideoQueryResponse(data)
    }
}
