package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.clip.Clip
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
                    id = obj.get("slug")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    broadcaster_id = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    broadcaster_login = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    broadcaster_name = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    video_id = obj.get("video")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    vod_offset = obj.get("videoOffsetSeconds")?.takeIf { !it.isJsonNull }?.asInt,
                    game_id = obj.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    game_name = obj.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    view_count = obj.get("viewCount")?.takeIf { !it.isJsonNull }?.asInt,
                    created_at = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                    thumbnail_url = obj.get("thumbnailURL")?.takeIf { !it.isJsonNull }?.asString,
                    duration = obj.get("durationSeconds")?.takeIf { !it.isJsonNull }?.asDouble,
                    profileImageURL = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return UserClipsQueryResponse(data, cursor, hasNextPage)
    }
}
