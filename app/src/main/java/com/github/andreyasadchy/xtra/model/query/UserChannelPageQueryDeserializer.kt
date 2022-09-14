package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.github.andreyasadchy.xtra.model.helix.user.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserChannelPageQueryDeserializer : JsonDeserializer<UserChannelPageQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserChannelPageQueryResponse {
        val data = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.let { obj ->
            Stream(
                id = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                user_id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                user_login = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                user_name = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                game_id = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                game_name = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("game")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                type = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")?.takeIf { !it.isJsonNull }?.asString,
                title = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("title")?.takeIf { !it.isJsonNull }?.asString,
                viewer_count = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("viewersCount")?.takeIf { !it.isJsonNull }?.asInt,
                started_at = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                thumbnail_url = obj.get("stream")?.takeIf { it.isJsonObject }?.asJsonObject?.get("previewImageURL")?.takeIf { !it.isJsonNull }?.asString,
                profileImageURL = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                channelUser = User(
                    bannerImageURL = obj.get("bannerImageURL")?.takeIf { !it.isJsonNull }?.asString,
                    view_count = obj.get("profileViewCount")?.takeIf { !it.isJsonNull }?.asInt,
                    created_at = obj.get("createdAt")?.takeIf { !it.isJsonNull }?.asString,
                    followers_count = obj.get("followers")?.takeIf { it.isJsonObject }?.asJsonObject?.get("totalCount")?.takeIf { !it.isJsonNull }?.asInt,
                    broadcaster_type = when {
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isPartner")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "partner"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isAffiliate")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "affiliate"
                        else -> null
                    },
                    type = when {
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isStaff")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "staff"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isSiteAdmin")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "admin"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isGlobalMod")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "global_mod"
                        else -> null
                    },
                ),
                lastBroadcast = obj.get("lastBroadcast")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("startedAt")?.takeIf { !it.isJsonNull }?.asString,
            )
        }
        return UserChannelPageQueryResponse(data)
    }
}
