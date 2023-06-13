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
        json.asJsonObject.get("data").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { set ->
                set.get("prefix")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                    set.get("tiers")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { tierElement ->
                        tierElement.takeIf { it.isJsonObject }?.asJsonObject?.let { tier ->
                            tier.get("min_bits")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt?.let { minBits ->
                                val color = tier.get("color")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
                                tier.get("images")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { theme ->
                                    theme.value.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { format ->
                                        val urls = mutableMapOf<String, String>()
                                        format.value.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { scale ->
                                            scale.value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { url ->
                                                urls[scale.key] = url
                                            }
                                        }
                                        data.add(CheerEmotesResponse.CheerTemplate(
                                            name = name,
                                            format = format.key,
                                            theme = theme.key,
                                            urls = urls,
                                            minBits = minBits,
                                            color = color
                                        ))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return CheerEmotesResponse(data)
    }
}
