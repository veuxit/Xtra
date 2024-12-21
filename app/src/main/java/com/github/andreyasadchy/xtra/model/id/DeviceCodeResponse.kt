package com.github.andreyasadchy.xtra.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DeviceCodeResponse(
    @SerialName("device_code")
    val deviceCode: String
)