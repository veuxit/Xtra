package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class StvEmotesDeserializer : JsonDeserializer<StvEmotesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): StvEmotesResponse {
        val emotes = mutableListOf<StvEmote>()
        json.asJsonArray?.forEach { emote ->
            emote.asJsonObject?.let { obj ->
                obj.get("name")?.asString?.let { name ->
                    val urls = obj.getAsJsonArray("urls")
                    emotes.add(StvEmote(
                        name = name,
                        url1x = urls.get(0)?.takeIf { it.isJsonArray }?.asJsonArray?.get(1)?.takeIf { !it.isJsonNull }?.asString,
                        url2x = urls.get(1)?.takeIf { it.isJsonArray }?.asJsonArray?.get(1)?.takeIf { !it.isJsonNull }?.asString,
                        url3x = urls.get(2)?.takeIf { it.isJsonArray }?.asJsonArray?.get(1)?.takeIf { !it.isJsonNull }?.asString,
                        url4x = urls.get(3)?.takeIf { it.isJsonArray }?.asJsonArray?.get(1)?.takeIf { !it.isJsonNull }?.asString,
                        type = obj.get("mime")?.takeIf { !it.isJsonNull }?.asString,
                        isZeroWidth = obj.get("visibility_simple")?.takeIf { it.isJsonArray }?.asJsonArray?.toString()?.contains("ZERO_WIDTH") == true
                    ))
                }
            }
        }
        return StvEmotesResponse(emotes)
    }
}
