package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.ui.view.chat.animateGifs
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class CheerEmotesQueryDeserializer : JsonDeserializer<CheerEmotesQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): CheerEmotesQueryResponse {
        val data = mutableListOf<CheerEmote>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("cheer")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("emotes")?.takeIf { !it.isJsonNull }?.asJsonArray
        dataJson?.forEach { item ->
            item?.takeIf { !it.isJsonNull }?.asJsonObject?.let { emote ->
                emote.get("tiers")?.takeIf { !it.isJsonNull }?.asJsonArray?.forEach { tiers ->
                    tiers?.asJsonObject?.let { obj ->
                        emote.get("prefix")?.takeIf { !it.isJsonNull }?.asString?.let { name ->
                            obj.get("bits")?.takeIf { !it.isJsonNull }?.asInt?.let { minBits ->
                                obj.get("images")?.takeIf { !it.isJsonNull }?.asJsonArray?.first()?.takeIf { !it.isJsonNull }?.asString?.let { url ->
                                    data.add(CheerEmote(
                                        name = name,
                                        url = url,
                                        type = if (animateGifs) "image/gif" else "image/png",
                                        minBits = minBits,
                                        color = obj.get("color")?.takeIf { !it.isJsonNull }?.asString
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        }
        return CheerEmotesQueryResponse(data)
    }
}
