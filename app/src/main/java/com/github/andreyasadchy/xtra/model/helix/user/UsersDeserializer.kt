package com.github.andreyasadchy.xtra.model.helix.user

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UsersDeserializer : JsonDeserializer<UsersResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UsersResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonArray("data")
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("display_name")?.takeIf { !it.isJsonNull }?.asString,
                    type = obj.get("type")?.takeIf { !it.isJsonNull }?.asString,
                    broadcasterType = obj.get("broadcaster_type")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageUrl = obj.get("profile_image_url")?.takeIf { !it.isJsonNull }?.asString,
                    createdAt = obj.get("created_at")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return UsersResponse(data)
    }
}
