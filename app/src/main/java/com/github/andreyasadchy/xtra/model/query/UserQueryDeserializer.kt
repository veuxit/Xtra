package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserQueryDeserializer : JsonDeserializer<UserQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserQueryResponse {
        val data = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.let { obj ->
            User(
                channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                channelLogin = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                channelName = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                profileImageUrl = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        return UserQueryResponse(data)
    }
}
