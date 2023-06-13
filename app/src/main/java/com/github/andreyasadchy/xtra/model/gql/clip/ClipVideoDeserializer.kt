package com.github.andreyasadchy.xtra.model.gql.clip

import com.github.andreyasadchy.xtra.model.ui.Clip
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ClipVideoDeserializer : JsonDeserializer<ClipVideoResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ClipVideoResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("clip")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
            Clip(
                videoId = obj.get("video")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                duration = obj.get("durationSeconds")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asDouble,
                vodOffset = obj.get("videoOffsetSeconds")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
            )
        }
        return ClipVideoResponse(data)
    }
}
