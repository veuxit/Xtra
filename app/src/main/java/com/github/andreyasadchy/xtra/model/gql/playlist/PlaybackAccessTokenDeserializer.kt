package com.github.andreyasadchy.xtra.model.gql.playlist

import com.github.andreyasadchy.xtra.model.PlaybackAccessToken
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class PlaybackAccessTokenDeserializer : JsonDeserializer<PlaybackAccessTokenResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): PlaybackAccessTokenResponse {
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val streamToken = dataJson?.get("streamPlaybackAccessToken")?.takeIf { it.isJsonObject }?.asJsonObject?.let { tokenJson ->
            PlaybackAccessToken(
                token = tokenJson.get("value")?.takeIf { !it.isJsonNull }?.asString,
                signature = tokenJson.get("signature")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        val videoToken = dataJson?.get("videoPlaybackAccessToken")?.takeIf { it.isJsonObject }?.asJsonObject?.let { tokenJson ->
            PlaybackAccessToken(
                token = tokenJson.get("value")?.takeIf { !it.isJsonNull }?.asString,
                signature = tokenJson.get("signature")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        return PlaybackAccessTokenResponse(streamToken, videoToken)
    }
}