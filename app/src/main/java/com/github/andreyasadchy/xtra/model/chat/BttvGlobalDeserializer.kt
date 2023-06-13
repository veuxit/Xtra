package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class BttvGlobalDeserializer : JsonDeserializer<BttvGlobalResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BttvGlobalResponse {
        val emotes = mutableListOf<BttvEmote>()
        json.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emote ->
            emote.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                obj.get("code")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                    obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { id ->
                        emotes.add(BttvEmote(
                            id = id,
                            name = name,
                            isAnimated = obj.get("animated")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean
                        ))
                    }
                }
            }
        }
        return BttvGlobalResponse(emotes)
    }
}
