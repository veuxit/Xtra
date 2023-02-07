package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UsersStreamQueryDeserializer : JsonDeserializer<UsersStreamQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UsersStreamQueryResponse {
        val data = mutableListOf<Stream>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonArray("users")
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                if (obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("viewersCount")?.isJsonNull == false) {
                    data.add(Stream(
                        id = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                        channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                        channelLogin = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                        channelName = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                        gameId = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                        gameName = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                        type = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { !it.isJsonNull }?.asString,
                        title = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("broadcastSettings")?.takeIf { it.isJsonObject }?.asJsonObject?.get("title")?.takeIf { !it.isJsonNull }?.asString,
                        viewerCount = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("viewersCount")?.takeIf { !it.isJsonNull }?.asInt,
                        startedAt = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                        thumbnailUrl = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("previewImageURL")?.takeIf { !it.isJsonNull }?.asString,
                        profileImageUrl = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                        tags = obj.get("stream")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("freeformTags")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { tagElement ->
                            tagElement?.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.takeIf { !it.isJsonNull }?.asString
                        }
                    ))
                }
            }
        }
        return UsersStreamQueryResponse(data)
    }
}
