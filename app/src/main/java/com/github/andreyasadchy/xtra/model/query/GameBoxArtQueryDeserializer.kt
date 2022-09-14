package com.github.andreyasadchy.xtra.model.query

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GameBoxArtQueryDeserializer : JsonDeserializer<GameBoxArtQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameBoxArtQueryResponse {
        return GameBoxArtQueryResponse(json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("game")?.get("boxArtURL")?.takeIf { !it.isJsonNull }?.asString)
    }
}
