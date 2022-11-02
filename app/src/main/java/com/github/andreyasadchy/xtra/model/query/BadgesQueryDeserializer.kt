package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class BadgesQueryDeserializer : JsonDeserializer<BadgesQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BadgesQueryResponse {
        val data = mutableListOf<TwitchBadge>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonArray("badges")
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(TwitchBadge(
                    setId = obj.get("setID").asString,
                    version = obj.get("version").asString,
                    url1x = obj.get("imageURL").asString,
                    url2x = obj.get("imageURL").asString,
                    url3x = obj.get("imageURL").asString,
                    url4x = obj.get("imageURL").asString,
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return BadgesQueryResponse(data)
    }
}
