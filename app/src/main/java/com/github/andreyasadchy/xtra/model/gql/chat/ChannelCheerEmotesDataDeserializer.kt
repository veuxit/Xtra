package com.github.andreyasadchy.xtra.model.gql.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChannelCheerEmotesDataDeserializer : JsonDeserializer<ChannelCheerEmotesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChannelCheerEmotesDataResponse {
        val data = mutableListOf<GlobalCheerEmotesDataResponse.CheerTier>()
        val dataJson = json.asJsonObject.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("channel")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cheer")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cheerGroups")?.takeIf { it.isJsonArray }?.asJsonArray
        dataJson?.forEach { groupElement ->
            groupElement.asJsonObject.let { group ->
                val template = group.get("templateURL").asString
                group.getAsJsonArray("nodes").forEach { emoteElement ->
                    emoteElement.asJsonObject.let { emote ->
                        val prefix = emote.get("prefix").asString.lowercase()
                        emote.getAsJsonArray("tiers").forEach { tierElement ->
                            tierElement.asJsonObject.let { tier ->
                                val tierBits = tier.get("bits").asInt
                                data.add(GlobalCheerEmotesDataResponse.CheerTier(
                                    template, prefix, tierBits
                                ))
                            }
                        }
                    }
                }
            }
        }
        return ChannelCheerEmotesDataResponse(data)
    }
}
