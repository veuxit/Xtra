package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.ui.Game
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class VideoGamesDataDeserializer : JsonDeserializer<VideoGamesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VideoGamesDataResponse {
        val data = mutableListOf<Game>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("video")?.getAsJsonObject("moments")?.getAsJsonArray("edges")
        dataJson?.forEach { item ->
            item.asJsonObject.getAsJsonObject("node")?.let { obj ->
                data.add(Game(
                    gameId = obj.get("details")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    gameName = obj.get("details")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    boxArtUrl = obj.get("details")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("boxArtURL")?.takeIf { !it.isJsonNull }?.asString,
                    vodPosition = obj.get("positionMilliseconds")?.takeIf { !it.isJsonNull }?.asInt,
                    vodDuration = obj.get("durationMilliseconds")?.takeIf { !it.isJsonNull }?.asInt,
                ))
            }
        }
        return VideoGamesDataResponse(data)
    }
}
