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
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<User>()
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("users")?.takeIf { it.isJsonArray }?.asJsonArray
        dataJson?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    broadcasterType = when {
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isPartner")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true -> "partner"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isAffiliate")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true -> "affiliate"
                        else -> null
                    },
                    type = when {
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isStaff")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true -> "staff"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isSiteAdmin")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true -> "admin"
                        obj.get("roles")?.takeIf { it.isJsonObject }?.asJsonObject?.get("isGlobalMod")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean == true -> "global_mod"
                        else -> null
                    }
                ))
            }
        }
        return UsersTypeQueryResponse(data)
    }
}
