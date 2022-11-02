package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.ui.view.chat.emoteQuality
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChatBadgesDataDeserializer : JsonDeserializer<ChatBadgesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChatBadgesDataResponse {
        val global = mutableListOf<TwitchBadge>()
        val channel = mutableListOf<TwitchBadge>()
        val dataJson = json.asJsonObject.getAsJsonObject("data")
        dataJson.get("badges")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.asJsonObject.let { obj ->
                obj.get("setID")?.takeIf { !it.isJsonNull }?.asString?.let { setId ->
                    obj.get("version")?.takeIf { !it.isJsonNull }?.asString?.let { version ->
                        val url = obj.get(when (emoteQuality) {"4" -> ("image4x") "3" -> ("image4x") "2" -> ("image2x") else -> ("image1x")}).takeUnless { it?.isJsonNull == true }?.asString ?: obj.get("image2x").takeUnless { it?.isJsonNull == true }?.asString ?: obj.get("image1x").asString
                        global.add(TwitchBadge(
                            setId = setId,
                            version = version,
                            url = url,
                            title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString
                        ))
                    }
                }
            }
        }
        dataJson.get("user")?.takeIf { it.isJsonObject }?.asJsonObject?.get("broadcastBadges")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.asJsonObject.let { obj ->
                obj.get("setID")?.takeIf { !it.isJsonNull }?.asString?.let { setId ->
                    obj.get("version")?.takeIf { !it.isJsonNull }?.asString?.let { version ->
                        val url = obj.get(when (emoteQuality) {"4" -> ("image4x") "3" -> ("image4x") "2" -> ("image2x") else -> ("image1x")}).takeUnless { it?.isJsonNull == true }?.asString ?: obj.get("image2x").takeUnless { it?.isJsonNull == true }?.asString ?: obj.get("image1x").asString
                        channel.add(TwitchBadge(
                            setId = setId,
                            version = version,
                            url = url,
                            title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString
                        ))
                    }
                }
            }
        }
        return ChatBadgesDataResponse(global, channel)
    }
}
