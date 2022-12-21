package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserClipsQueryDeserializer : JsonDeserializer<UserClipsQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserClipsQueryResponse {
        val data = mutableListOf<Clip>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("clips")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("clips")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("node")?.let { obj ->
                data.add(Clip(
                    id = obj.get("slug")?.takeIf { !it.isJsonNull }?.asString,
                    channelId = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    videoId = obj.get("video")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    vodOffset = obj.get("videoOffsetSeconds")?.takeIf { !it.isJsonNull }?.asInt,
                    gameId = obj.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    gameName = obj.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    viewCount = obj.get("viewCount")?.takeIf { !it.isJsonNull }?.asInt,
                    uploadDate = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                    thumbnailUrl = obj.get("thumbnailURL")?.takeIf { !it.isJsonNull }?.asString,
                    duration = obj.get("durationSeconds")?.takeIf { !it.isJsonNull }?.asDouble,
                    profileImageUrl = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                    videoAnimatedPreviewURL = obj.get("video")?.takeIf { it.isJsonObject }?.asJsonObject?.get("animatedPreviewURL")?.takeIf { !it.isJsonNull }?.asString
                ))
            }
        }
        return UserClipsQueryResponse(data, cursor, hasNextPage)
    }
}
