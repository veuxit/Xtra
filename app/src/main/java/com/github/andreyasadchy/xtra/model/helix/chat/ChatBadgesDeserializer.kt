package com.github.andreyasadchy.xtra.model.helix.chat

import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChatBadgesDeserializer : JsonDeserializer<ChatBadgesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChatBadgesResponse {
        val data = mutableListOf<TwitchBadge>()
        json.asJsonObject.get("data").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { set ->
                set.get("set_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { setId ->
                    set.get("versions")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { versionElement ->
                        versionElement.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                            obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { version ->
                                data.add(TwitchBadge(
                                    setId = setId,
                                    version = version,
                                    url1x = obj.get("image_url_1x")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                    url2x = obj.get("image_url_2x")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                    url3x = obj.get("image_url_4x")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                    url4x = obj.get("image_url_4x")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
                                ))
                            }
                        }
                    }
                }
            }
        }
        return ChatBadgesResponse(data)
    }
}