package com.github.andreyasadchy.xtra.model.helix.chat

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ModeratorsDeserializer : JsonDeserializer<ModeratorsResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ModeratorsResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject.getAsJsonArray("data")
        val cursor = json.asJsonObject.get("pagination")?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        dataJson.forEach { element ->
            element.asJsonObject.let { obj ->
                data.add(User(
                    channelId = obj.get("user_id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("user_login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("user_name")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return ModeratorsResponse(data, cursor)
    }
}