package com.github.andreyasadchy.xtra.model.gql.clip

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ClipDataDeserializer : JsonDeserializer<ClipDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ClipDataResponse {
        val data = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("clip")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
            Clip(
                channelId = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                channelLogin = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                channelName = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                profileImageUrl = obj.get("broadcaster")?.takeIf { it.isJsonObject }?.asJsonObject?.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                vodOffset = obj.get("videoOffsetSeconds")?.takeIf { !it.isJsonNull }?.asInt,
            )
        }
        return ClipDataResponse(data)
    }
}
