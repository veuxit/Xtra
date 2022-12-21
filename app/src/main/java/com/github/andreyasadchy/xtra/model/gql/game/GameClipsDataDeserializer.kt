package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GameClipsDataDeserializer : JsonDeserializer<GameClipsDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameClipsDataResponse {
        val data = mutableListOf<Clip>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("game")?.getAsJsonObject("clips")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("game")?.getAsJsonObject("clips")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("node")?.let { obj ->
                data.add(Clip(
                    id = obj.get("slug")?.takeIf { !it.isJsonNull }?.asString,
                    channelId = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    viewCount = obj.get("viewCount")?.takeIf { !it.isJsonNull }?.asInt,
                    uploadDate = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                    thumbnailUrl = obj.get("thumbnailURL")?.takeIf { !it.isJsonNull }?.asString,
                    duration = obj.get("durationSeconds")?.takeIf { !it.isJsonNull }?.asDouble,
                    profileImageUrl = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString
                ))
            }
        }
        return GameClipsDataResponse(data, cursor, hasNextPage)
    }
}
