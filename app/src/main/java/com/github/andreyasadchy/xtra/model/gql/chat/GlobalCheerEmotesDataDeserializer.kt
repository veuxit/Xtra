package com.github.andreyasadchy.xtra.model.gql.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class GlobalCheerEmotesDataDeserializer : JsonDeserializer<GlobalCheerEmotesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GlobalCheerEmotesDataResponse {
        val tiers = mutableListOf<GlobalCheerEmotesDataResponse.CheerTier>()
        val dataJson = json.asJsonObject.getAsJsonObject("data").getAsJsonObject("cheerConfig")
        val config = dataJson.getAsJsonObject("displayConfig")
        val cheerConfig = GlobalCheerEmotesDataResponse.CheerConfig(
            backgrounds = config.getAsJsonArray("backgrounds"),
            colors = config.getAsJsonArray("colors"),
            scales = config.getAsJsonArray("scales"),
            types = config.getAsJsonArray("types")
        )
        dataJson.getAsJsonArray("groups").forEach { groupElement ->
            groupElement.asJsonObject.let { group ->
                val template = group.get("templateURL").asString
                group.getAsJsonArray("nodes").forEach { emoteElement ->
                    emoteElement.asJsonObject.let { emote ->
                        val prefix = emote.get("prefix").asString.lowercase()
                        emote.getAsJsonArray("tiers").forEach { tierElement ->
                            tierElement.asJsonObject.let { tier ->
                                val tierBits = tier.get("bits").asInt
                                tiers.add(GlobalCheerEmotesDataResponse.CheerTier(
                                    template, prefix, tierBits
                                ))
                            }
                        }
                    }
                }
            }
        }
        return GlobalCheerEmotesDataResponse(cheerConfig, tiers)
    }
}
