package com.github.andreyasadchy.xtra.model.id

import com.google.gson.annotations.SerializedName

class TokenResponse(
        @SerializedName("access_token")
        val token: String?)
