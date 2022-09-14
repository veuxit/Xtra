package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.follows.Follow
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FollowedUsersQueryDeserializer : JsonDeserializer<FollowedUsersQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FollowedUsersQueryResponse {
        val data = mutableListOf<Follow>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("follows")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("follows")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("node")?.let { obj ->
                data.add(Follow(
                    to_id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    to_login = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    to_name = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    followed_at = item.asJsonObject.get("followedAt")?.takeIf { !it.isJsonNull }?.asString,
                    lastBroadcast = obj.get("lastBroadcast")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("startedAt")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageURL = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return FollowedUsersQueryResponse(data, cursor, hasNextPage)
    }
}
