package com.github.andreyasadchy.xtra.model.gql.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GlobalCheerEmotesDataDeserializer : JsonDeserializer<GlobalCheerEmotesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GlobalCheerEmotesDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val tiers = mutableListOf<GlobalCheerEmotesDataResponse.CheerTier>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("cheerConfig").asJsonObject
        val backgrounds = mutableListOf<String>()
        val colors = mutableMapOf<Int, String>()
        val scales = mutableListOf<String>()
        val types = mutableMapOf<String, String>()
        dataJson.get("displayConfig").asJsonObject.let { config ->
            config.get("backgrounds")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { backgrounds.add(it) }
            }
            config.get("colors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                    obj.get("bits").takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt?.let { minBits ->
                        obj.get("color").takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { color ->
                            colors[minBits] = color
                        }
                    }
                }
            }
            config.get("scales")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { scales.add(it) }
            }
            config.get("types")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
                element.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                    obj.get("animation").takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { animation ->
                        obj.get("extension").takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { extension ->
                            types[animation] = extension
                        }
                    }
                }
            }
        }
        dataJson?.get("groups")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { groupElement ->
            groupElement.takeIf { it.isJsonObject }?.asJsonObject?.let { group ->
                group.get("templateURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { template ->
                    group.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emoteElement ->
                        emoteElement.takeIf { it.isJsonObject }?.asJsonObject?.let { emote ->
                            emote.get("prefix")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.lowercase()?.let { prefix ->
                                emote.get("tiers")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { tierElement ->
                                    tierElement.takeIf { it.isJsonObject }?.asJsonObject?.let { tier ->
                                        tier.get("bits")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt?.let { tierBits ->
                                            tiers.add(GlobalCheerEmotesDataResponse.CheerTier(
                                                template, prefix, tierBits
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return GlobalCheerEmotesDataResponse(GlobalCheerEmotesDataResponse.CheerConfig(backgrounds, colors, scales, types), tiers)
    }
}
