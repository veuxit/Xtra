package com.github.andreyasadchy.xtra.model.helix.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class EmoteSetDeserializer : JsonDeserializer<EmoteSetResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): EmoteSetResponse {
        val data = mutableListOf<EmoteSetResponse.EmoteTemplate>()
        val dataJson = json.asJsonObject.getAsJsonArray("data")
        if (dataJson != null && dataJson.size() > 0) {
            dataJson.forEach { emote ->
                val obj = emote.asJsonObject
                data.add(EmoteSetResponse.EmoteTemplate(
                    template = json.asJsonObject.get("template").asString,
                    id = obj.get("id").asString,
                    format = obj.getAsJsonArray("format"),
                    theme = obj.getAsJsonArray("theme_mode"),
                    scale = obj.getAsJsonArray("scale"),
                    name = obj.get("name").asString,
                    setId = obj.get("emote_set_id")?.takeIf { !it.isJsonNull }?.asString,
                    ownerId = obj.get("owner_id")?.takeIf { !it.isJsonNull }?.asString
                ))
            }
        }
        return EmoteSetResponse(data)
    }
}