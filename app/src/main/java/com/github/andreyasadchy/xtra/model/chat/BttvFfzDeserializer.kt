package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class BttvFfzDeserializer : JsonDeserializer<BttvFfzResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BttvFfzResponse {
        val emotes = mutableListOf<FfzEmote>()
        json.asJsonArray?.forEach { emote ->
            emote.asJsonObject?.let { obj ->
                obj.get("code")?.asString?.let { name ->
                    val urls = obj.getAsJsonObject("images")
                    emotes.add(FfzEmote(
                        name = name,
                        url1x = urls.get("1x")?.takeIf { !it.isJsonNull }?.asString,
                        url2x = urls.get("2x")?.takeIf { !it.isJsonNull }?.asString,
                        url3x = urls.get("2x")?.takeIf { !it.isJsonNull }?.asString,
                        url4x = urls.get("4x")?.takeIf { !it.isJsonNull }?.asString,
                        type = obj.get("imageType")?.takeIf { !it.isJsonNull }?.asString
                    ))
                }
            }
        }
        return BttvFfzResponse(emotes)
    }
}
