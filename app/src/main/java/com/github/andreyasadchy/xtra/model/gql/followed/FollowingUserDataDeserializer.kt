package com.github.andreyasadchy.xtra.model.gql.followed

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FollowingUserDataDeserializer : JsonDeserializer<FollowingUserDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FollowingUserDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val following = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("user")?.takeIf { it.isJsonObject }?.asJsonObject?.get("self")?.takeIf { it.isJsonObject }?.asJsonObject?.get("follower")?.isJsonObject == true
        return FollowingUserDataResponse(following)
    }
}
