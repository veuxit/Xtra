package com.github.andreyasadchy.xtra.model.gql.clip

import android.net.Uri
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessToken
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ClipUrlsDeserializer : JsonDeserializer<ClipUrlsResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ClipUrlsResponse {
        val data = mutableListOf<ClipUrlsResponse.ClipInfo>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("clip")
        val accessToken = dataJson?.get("playbackAccessToken")?.takeIf { it.isJsonObject }?.asJsonObject?.let { tokenJson ->
            PlaybackAccessToken(
                token = tokenJson.get("value")?.takeIf { !it.isJsonNull }?.asString,
                signature = tokenJson.get("signature")?.takeIf { !it.isJsonNull }?.asString
            )
        }
        dataJson?.getAsJsonArray("videoQualities")?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                obj.get("sourceURL")?.takeIf { !it.isJsonNull }?.asString?.let { url ->
                    data.add(ClipUrlsResponse.ClipInfo(
                        frameRate = obj.get("frameRate")?.takeIf { !it.isJsonNull }?.asInt,
                        quality = obj.get("quality")?.takeIf { !it.isJsonNull }?.asString,
                        url = "$url?sig=${Uri.encode(accessToken?.signature)}&token=${Uri.encode(accessToken?.token)}"
                    ))
                }
            }
        }
        return ClipUrlsResponse(data)
    }
}
