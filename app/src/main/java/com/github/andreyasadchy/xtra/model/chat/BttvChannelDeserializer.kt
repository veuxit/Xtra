package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class BttvChannelDeserializer : JsonDeserializer<BttvChannelResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BttvChannelResponse {
        val emotes = mutableListOf<Emote>()
        json.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { set ->
            if (set.key != "bots") {
                set.value.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emote ->
                    emote.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                        obj.get("code")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                            obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { id ->
                                emotes.add(Emote(
                                    name = name,
                                    url1x = "https://cdn.betterttv.net/emote/$id/1x.webp",
                                    url2x = "https://cdn.betterttv.net/emote/$id/2x.webp",
                                    url3x = "https://cdn.betterttv.net/emote/$id/2x.webp",
                                    url4x = "https://cdn.betterttv.net/emote/$id/3x.webp",
                                    format = "webp",
                                    isAnimated = obj.get("animated")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean ?: true,
                                    isZeroWidth = listOf("IceCold", "SoSnowy", "SantaHat", "TopHat", "CandyCane", "ReinDeer", "cvHazmat", "cvMask").contains(name)
                                ))
                            }
                        }
                    }
                }
            }
        }
        return BttvChannelResponse(emotes)
    }
}
