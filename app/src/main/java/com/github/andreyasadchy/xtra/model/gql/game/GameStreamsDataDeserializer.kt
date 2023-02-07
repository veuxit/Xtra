package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GameStreamsDataDeserializer : JsonDeserializer<GameStreamsDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameStreamsDataResponse {
        val data = mutableListOf<Stream>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("game")?.getAsJsonObject("streams")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("game")?.getAsJsonObject("streams")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("node")?.let { obj ->
                data.add(Stream(
                    id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelId = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    type = obj.get("type")?.takeIf { !it.isJsonNull }?.asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                    viewerCount = obj.get("viewersCount")?.takeIf { !it.isJsonNull }?.asInt,
                    thumbnailUrl = obj.get("previewImageURL")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageUrl = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                    tags = obj.get("freeformTags")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { tagElement ->
                        tagElement?.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.takeIf { !it.isJsonNull }?.asString
                    }
                ))
            }
        }
        return GameStreamsDataResponse(data, cursor, hasNextPage)
    }
}
