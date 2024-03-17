package com.github.andreyasadchy.xtra.model.helix.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserEmotesDeserializer : JsonDeserializer<UserEmotesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserEmotesResponse {
        val data = mutableListOf<EmoteSetResponse.EmoteTemplate>()
        val cursor = json.takeIf { it.isJsonObject }?.asJsonObject?.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("template")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { template ->
            json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                    obj.get("name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                        obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { id ->
                            val formats = mutableListOf<String>()
                            obj.get("format")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                                element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { formats.add(it) }
                            }
                            val themes = mutableListOf<String>()
                            obj.get("theme_mode")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                                element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { themes.add(it) }
                            }
                            val scales = mutableListOf<String>()
                            obj.get("scale")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                                element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { scales.add(it) }
                            }
                            data.add(EmoteSetResponse.EmoteTemplate(
                                template = template,
                                id = id,
                                formats = formats,
                                themes = themes,
                                scales = scales,
                                name = name.removePrefix("\\").replace("?\\", ""),
                                setId = obj.get("emote_set_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                ownerId = obj.get("owner_id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
                            ))
                        }
                    }
                }
            }
        }
        return UserEmotesResponse(data, cursor)
    }
}