package com.github.andreyasadchy.xtra.model.gql.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChannelCheerEmotesDataDeserializer : JsonDeserializer<ChannelCheerEmotesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChannelCheerEmotesDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<GlobalCheerEmotesDataResponse.CheerTier>()
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("channel")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cheer")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cheerGroups")?.takeIf { it.isJsonArray }?.asJsonArray
        dataJson?.forEach { groupElement ->
            groupElement.takeIf { it.isJsonObject }?.asJsonObject?.let { group ->
                group.get("templateURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { template ->
                    group.get("nodes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emoteElement ->
                        emoteElement.takeIf { it.isJsonObject }?.asJsonObject?.let { emote ->
                            emote.get("prefix")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.lowercase()?.let { prefix ->
                                emote.get("tiers")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { tierElement ->
                                    tierElement.takeIf { it.isJsonObject }?.asJsonObject?.let { tier ->
                                        tier.get("bits")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt?.let { tierBits ->
                                            data.add(GlobalCheerEmotesDataResponse.CheerTier(
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
        return ChannelCheerEmotesDataResponse(data)
    }
}
