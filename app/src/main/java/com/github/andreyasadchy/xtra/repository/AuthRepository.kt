package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.api.IdApi
import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: IdApi) {

    suspend fun validate(token: String): ValidationResponse = withContext(Dispatchers.IO) {
        api.validateToken(token)
    }

    suspend fun revoke(clientId: String, token: String) = withContext(Dispatchers.IO) {
        api.revokeToken(clientId, token)
    }

    suspend fun getDeviceCode(body: RequestBody): DeviceCodeResponse = withContext(Dispatchers.IO) {
        api.getDeviceCode(body)
    }

    suspend fun getToken(body: RequestBody): TokenResponse = withContext(Dispatchers.IO) {
        api.getToken(body)
    }
}
