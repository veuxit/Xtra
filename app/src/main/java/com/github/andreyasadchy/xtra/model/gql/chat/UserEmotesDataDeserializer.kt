package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserEmotesDataDeserializer : JsonDeserializer<UserEmotesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserEmotesDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<TwitchEmote>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("channel").asJsonObject.get("self").asJsonObject.get("availableEmoteSets").asJsonArray
        dataJson?.forEach { setElement ->
            setElement.takeIf { it.isJsonObject }?.asJsonObject?.let { set ->
                set.get("emotes")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { emote ->
                    emote.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                        obj.get("token")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { name ->
                            data.add(TwitchEmote(
                                id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                name = name.removePrefix("\\").replace("?\\", ""),
                                setId = obj.get("setID")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                                ownerId = set.get("owner")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
                            ))
                        }
                    }
                }
            }
        }
        return UserEmotesDataResponse(data)
    }
}
