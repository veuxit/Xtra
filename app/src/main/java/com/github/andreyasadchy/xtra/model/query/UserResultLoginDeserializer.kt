package com.github.andreyasadchy.xtra.model.query

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserResultLoginDeserializer : JsonDeserializer<UserResultLoginQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserResultLoginQueryResponse {
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("userResultByLogin")
        val data = androidx.core.util.Pair(if (dataJson?.get("id")?.isJsonNull == false) null else "userResultByLogin", dataJson?.get("reason")?.takeIf { !it.isJsonNull }?.asString)
        return UserResultLoginQueryResponse(data)
    }
}
