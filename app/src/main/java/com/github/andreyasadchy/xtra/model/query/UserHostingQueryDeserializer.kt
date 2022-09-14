package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserHostingQueryDeserializer : JsonDeserializer<UserHostingQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserHostingQueryResponse {
        val data = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.get("hosting")?.takeIf { !it.isJsonNull }?.asJsonObject?.let { obj ->
            Stream(
                id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                user_login = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                user_name = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                profileImageURL = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        return UserHostingQueryResponse(data)
    }
}
