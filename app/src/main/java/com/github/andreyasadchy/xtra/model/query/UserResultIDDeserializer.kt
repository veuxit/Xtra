package com.github.andreyasadchy.xtra.model.query

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserResultIDDeserializer : JsonDeserializer<UserResultIDQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserResultIDQueryResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("userResultByID")?.takeIf { it.isJsonObject }?.asJsonObject
        val data = androidx.core.util.Pair(if (dataJson?.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.isString == true) null else "userResultByID", dataJson?.get("reason")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString)
        return UserResultIDQueryResponse(data)
    }
}
