package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class StvGlobalDeserializer : JsonDeserializer<StvGlobalResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): StvGlobalResponse {
        val emotes = mutableListOf<Emote>()
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("emotes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emote ->
            emote.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                obj.get("name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                    obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.let { data ->
                        data.get("host")?.takeIf { it.isJsonObject }?.asJsonObject?.let { host ->
                            host.get("url")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { template ->
                                val urls = mutableListOf<String>()
                                host.get("files")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { file ->
                                    file.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                                        obj.get("name")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                                            if (!name.contains("avif", true)) {
                                                urls.add("https:${template}/${name}")
                                            }
                                        }
                                    }
                                }
                                emotes.add(Emote(
                                    name = name,
                                    url1x = urls.getOrNull(0) ?: "https:${template}/1x.webp",
                                    url2x = urls.getOrNull(1) ?: if (urls.isEmpty()) "https:${template}/2x.webp" else null,
                                    url3x = urls.getOrNull(2) ?: if (urls.isEmpty()) "https:${template}/3x.webp" else null,
                                    url4x = urls.getOrNull(3) ?: if (urls.isEmpty()) "https:${template}/4x.webp" else null,
                                    format = urls.getOrNull(0)?.substringAfterLast(".") ?: "webp",
                                    isAnimated = data.get("animated")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean ?: true,
                                    isZeroWidth = obj.get("flags")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt == 1
                                ))
                            }
                        }
                    }
                }
            }
        }
        return StvGlobalResponse(emotes)
    }
}
