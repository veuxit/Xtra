package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FfzChannelDeserializer : JsonDeserializer<FfzChannelResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FfzChannelResponse {
        val emotes = mutableListOf<FfzEmote>()
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("sets")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { set ->
            set.value.takeIf { it.isJsonObject }?.asJsonObject?.get("emoticons")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emote ->
                emote.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                    obj.get("name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                        val isAnimated = obj.get("animated")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.isNotEmpty() == true
                        obj.get(if (isAnimated) "animated" else "urls")?.takeIf { it.isJsonObject }?.asJsonObject?.let { urls ->
                            emotes.add(FfzEmote(
                                name = name,
                                url1x = urls.get("1")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                url2x = urls.get("2")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                url3x = urls.get("2")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                url4x = urls.get("4")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                type = if (isAnimated) "webp" else null,
                                isAnimated = isAnimated
                            ))
                        }
                    }
                }
            }
        }
        return FfzChannelResponse(emotes)
    }
}
