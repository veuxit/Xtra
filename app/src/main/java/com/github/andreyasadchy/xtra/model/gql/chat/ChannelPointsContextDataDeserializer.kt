package com.github.andreyasadchy.xtra.model.gql.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ChannelPointsContextDataDeserializer : JsonDeserializer<ChannelPointsContextDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChannelPointsContextDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val obj = json.asJsonObject.get("data").asJsonObject.get("community").asJsonObject.get("channel").asJsonObject.get("self").asJsonObject.get("communityPoints").asJsonObject
        return ChannelPointsContextDataResponse(
            balance = obj?.get("balance")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
            availableClaimId = obj?.get("availableClaim")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        )
    }
}
