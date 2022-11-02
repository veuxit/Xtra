package com.github.andreyasadchy.xtra.model.query

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserCheerEmotesQueryDeserializer : JsonDeserializer<UserCheerEmotesQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserCheerEmotesQueryResponse {
        val tiers = mutableListOf<UserCheerEmotesQueryResponse.CheerTier>()
        val dataJson = json.asJsonObject.getAsJsonObject("data").getAsJsonObject("cheerConfig")
        val config = dataJson.getAsJsonObject("displayConfig")
        val colors = mutableListOf<UserCheerEmotesQueryResponse.CheermoteColorConfig>()
        config.getAsJsonArray("colors").forEach { colorElement ->
            colorElement?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                obj.get("bits")?.takeIf { !it.isJsonNull }?.asInt?.let { bits ->
                    obj.get("color")?.takeIf { !it.isJsonNull }?.asString?.let { color ->
                        colors.add(UserCheerEmotesQueryResponse.CheermoteColorConfig(
                            bits, color
                        ))
                    }
                }
            }
        }
        val types = mutableListOf<UserCheerEmotesQueryResponse.CheermoteDisplayType>()
        config.getAsJsonArray("types").forEach { colorElement ->
            colorElement?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                types.add(UserCheerEmotesQueryResponse.CheermoteDisplayType(
                    animation = obj.get("animation")?.takeIf { !it.isJsonNull }?.asString,
                    extension = obj.get("extension")?.takeIf { !it.isJsonNull }?.asString
                ))
            }
        }
        val cheerConfig = UserCheerEmotesQueryResponse.CheerConfig(
            backgrounds = config.getAsJsonArray("backgrounds"),
            colors = colors,
            scales = config.getAsJsonArray("scales"),
            types = types
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
                                tiers.add(UserCheerEmotesQueryResponse.CheerTier(
                                    template, prefix, tierBits
                                ))
                            }
                        }
                    }
                }
            }
        }
        json.asJsonObject.getAsJsonObject("data").get("user")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cheer")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cheerGroups")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { groupElement ->
            groupElement.asJsonObject.let { group ->
                val template = group.get("templateURL").asString
                group.getAsJsonArray("nodes").forEach { emoteElement ->
                    emoteElement.asJsonObject.let { emote ->
                        val prefix = emote.get("prefix").asString.lowercase()
                        emote.getAsJsonArray("tiers").forEach { tierElement ->
                            tierElement.asJsonObject.let { tier ->
                                val tierBits = tier.get("bits").asInt
                                tiers.add(UserCheerEmotesQueryResponse.CheerTier(
                                    template, prefix, tierBits
                                ))
                            }
                        }
                    }
                }
            }
        }
        return UserCheerEmotesQueryResponse(cheerConfig, tiers)
    }
}
