package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.user.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserMessageClickedQueryDeserializer : JsonDeserializer<UserMessageClickedQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserMessageClickedQueryResponse {
        val data = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.let { obj ->
            User(
                id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                login = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                display_name = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                created_at = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                profile_image_url = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                bannerImageURL = obj.get("bannerImageURL")?.takeIf { !it.isJsonNull }?.asString,
                followedAt = obj.get("follow")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("followedAt")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        return UserMessageClickedQueryResponse(data)
    }
}
