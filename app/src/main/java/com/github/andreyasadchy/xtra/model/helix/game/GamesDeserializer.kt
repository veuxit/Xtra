package com.github.andreyasadchy.xtra.model.helix.game

import com.github.andreyasadchy.xtra.model.ui.Game
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GamesDeserializer : JsonDeserializer<GamesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GamesResponse {
        val data = mutableListOf<Game>()
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        val cursor = json.asJsonObject?.get("pagination")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(Game(
                    gameId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    gameName = obj.get("name")?.takeIf { !it.isJsonNull }?.asString,
                    boxArtUrl = obj.get("box_art_url")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return GamesResponse(data, cursor)
    }
}
