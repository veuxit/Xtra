package com.github.andreyasadchy.xtra.model.helix.chat

import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.ui.view.chat.emoteQuality
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChatBadgesDeserializer : JsonDeserializer<ChatBadgesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChatBadgesResponse {
        val data = mutableListOf<TwitchBadge>()
        val dataJson = json.asJsonObject.getAsJsonArray("data")
        if (dataJson != null && dataJson.size() > 0) {
            dataJson.forEach { setElement ->
                setElement.asJsonObject.let { set ->
                    set.getAsJsonArray("versions").forEach { versionElement ->
                        versionElement.asJsonObject.let { version ->
                            val url = version.get(when (emoteQuality) {"4" -> ("image_url_4x") "3" -> ("image_url_4x") "2" -> ("image_url_2x") else -> ("image_url_1x")}).takeUnless { it?.isJsonNull == true }?.asString ?: version.get("image_url_2x").takeUnless { it?.isJsonNull == true }?.asString ?: version.get("image_url_1x").asString
                            data.add(TwitchBadge(
                                setId = set.get("set_id").asString,
                                version = version.get("id").asString,
                                url = url,
                            ))
                        }
                    }
                }
            }
        }
        return ChatBadgesResponse(data)
    }
}