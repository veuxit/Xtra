package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class UsersTypeQueryDeserializer : JsonDeserializer<UsersTypeQueryResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): UsersTypeQueryResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonArray("users")
        dataJson?.forEach { item ->
            item?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    broadcasterType = when {
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isPartner")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "partner"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isAffiliate")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "affiliate"
                        else -> null
                    },
                    type = when {
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isStaff")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "staff"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isSiteAdmin")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "admin"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isGlobalMod")?.takeIf { !it.isJsonNull }?.asBoolean == true -> "global_mod"
                        else -> null
                    }
                ))
            }
        }
        return UsersTypeQueryResponse(data)
    }
}
