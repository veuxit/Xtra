package com.github.andreyasadchy.xtra.model.gql.clip

import android.net.Uri
import com.github.andreyasadchy.xtra.model.PlaybackAccessToken
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ClipUrlsDeserializer : JsonDeserializer<ClipUrlsResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ClipUrlsResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<ClipUrlsResponse.ClipInfo>()
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("clip")?.takeIf { it.isJsonObject }?.asJsonObject
        val accessToken = dataJson?.get("playbackAccessToken")?.takeIf { it.isJsonObject }?.asJsonObject?.let { tokenJson ->
            PlaybackAccessToken(
                token = tokenJson.get("value")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                signature = tokenJson.get("signature")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
            )
        }
        dataJson?.get("videoQualities")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                obj.get("sourceURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { url ->
                    data.add(ClipUrlsResponse.ClipInfo(
                        frameRate = obj.get("frameRate")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                        quality = obj.get("quality")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                        url = "$url?sig=${Uri.encode(accessToken?.signature)}&token=${Uri.encode(accessToken?.token)}"
                    ))
                }
            }
        }
        return ClipUrlsResponse(data)
    }
}
