package com.github.andreyasadchy.xtra.model.query

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UserResultIDDeserializer : JsonDeserializer<UserResultIDQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UserResultIDQueryResponse {
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("userResultByID")
        val data = androidx.core.util.Pair(if (dataJson?.get("id")?.isJsonNull == false) null else "userResultByID", dataJson?.get("reason")?.takeIf { !it.isJsonNull }?.asString)
        return UserResultIDQueryResponse(data)
    }
}
