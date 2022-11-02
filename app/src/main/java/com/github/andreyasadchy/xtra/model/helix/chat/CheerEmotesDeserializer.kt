package com.github.andreyasadchy.xtra.model.helix.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class CheerEmotesDeserializer : JsonDeserializer<CheerEmotesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CheerEmotesResponse {
        val data = mutableListOf<CheerEmotesResponse.CheerTemplate>()
        val dataJson = json.asJsonObject.getAsJsonArray("data")
        dataJson.forEach { setElement ->
            setElement.asJsonObject.let { set ->
                val name = set.get("prefix").asString
                set.getAsJsonArray("tiers")?.forEach { emote ->
                    emote.asJsonObject?.let { obj ->
                        val theme = obj.getAsJsonObject("images").getAsJsonObject("dark")
                        data.add(CheerEmotesResponse.CheerTemplate(
                            name = name,
                            static = theme.get("static")?.takeIf { it.isJsonObject }?.asJsonObject,
                            animated = theme.get("animated")?.takeIf { it.isJsonObject }?.asJsonObject,
                            minBits = obj.get("min_bits").asInt,
                            color = obj.get("color")?.takeIf { !it.isJsonNull }?.asString
                        ))
                    }
                }
            }
        }
        return CheerEmotesResponse(data)
    }
}
