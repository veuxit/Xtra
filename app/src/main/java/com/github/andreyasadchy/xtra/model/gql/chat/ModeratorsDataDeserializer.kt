package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ModeratorsDataDeserializer : JsonDeserializer<ModeratorsDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ModeratorsDataResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("mods")?.getAsJsonArray("edges")
        dataJson?.forEach { element ->
            element?.asJsonObject?.let { obj ->
                obj.getAsJsonObject("node")?.let { node ->
                    data.add(User(
                        channelId = node.get("id")?.takeIf { !it.isJsonNull }?.asString,
                        channelLogin = node.get("login")?.takeIf { !it.isJsonNull }?.asString
                    ))
                }
            }
        }
        return ModeratorsDataResponse(data)
    }
}
