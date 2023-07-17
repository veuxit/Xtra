package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GameVideosQueryDeserializer : JsonDeserializer<GameVideosQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameVideosQueryResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<Video>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("game").asJsonObject.get("videos").asJsonObject.get("edges").asJsonArray
        val cursor = dataJson?.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        val hasNextPage = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("videos")?.takeIf { it.isJsonObject }?.asJsonObject?.get("pageInfo")?.takeIf { it.isJsonObject }?.asJsonObject?.get("hasNextPage")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean
        dataJson?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("node")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                val tags = mutableListOf<Tag>()
                obj.get("contentTags")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { tagElement ->
                    tagElement.takeIf { it.isJsonObject }?.asJsonObject?.let { tag ->
                        tags.add(Tag(
                            id = tag.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                            name = tag.get("localizedName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                        ))
                    }
                }
                data.add(Video(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelId = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelLogin = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    channelName = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    type = obj.get("broadcastType")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    title = obj.get("title")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    uploadDate = obj.get("createdAt")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    thumbnailUrl = obj.get("previewThumbnailURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    viewCount = obj.get("viewCount")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                    duration = obj.get("lengthSeconds")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt?.toString(),
                    profileImageUrl = obj.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("profileImageURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    tags = tags,
                    animatedPreviewURL = obj.get("animatedPreviewURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
                ))
            }
        }
        return GameVideosQueryResponse(data, cursor, hasNextPage)
    }
}
