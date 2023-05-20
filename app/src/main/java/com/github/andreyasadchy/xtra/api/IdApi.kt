package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface IdApi {

    @GET("validate")
    suspend fun validateToken(@Header("Authorization") token: String): ValidationResponse?

    @POST("revoke")
    suspend fun revokeToken(@Query("client_id") clientId: String, @Query("token") token: String)

    @POST("device")
    suspend fun getDeviceCode(@Body body: RequestBody): DeviceCodeResponse

    @POST("token")
    suspend fun getToken(@Body body: RequestBody): TokenResponse
}