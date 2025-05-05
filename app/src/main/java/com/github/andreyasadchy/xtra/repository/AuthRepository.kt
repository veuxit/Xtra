package com.github.andreyasadchy.xtra.repository

import com.github.andreyasadchy.xtra.model.id.DeviceCodeResponse
import com.github.andreyasadchy.xtra.model.id.TokenResponse
import com.github.andreyasadchy.xtra.model.id.ValidationResponse
import com.github.andreyasadchy.xtra.util.body
import com.github.andreyasadchy.xtra.util.code
import com.github.andreyasadchy.xtra.util.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UploadDataProviders
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    suspend fun validate(useCronet: Boolean, token: String): ValidationResponse = withContext(Dispatchers.IO) {
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://id.twitch.tv/oauth2/validate", request.callback, cronetExecutor).apply {
                addHeader("Authorization", token)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode != 401) {
                json.decodeFromString<ValidationResponse>(response.responseBody as String)
            } else {
                throw IllegalStateException("401")
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://id.twitch.tv/oauth2/validate")
                header("Authorization", token)
            }.build()).execute().use { response ->
                if (response.code != 401) {
                    json.decodeFromString<ValidationResponse>(response.body.string())
                } else {
                    throw IllegalStateException("401")
                }
            }
        }
    }

    suspend fun revoke(useCronet: Boolean, clientId: String, token: String) = withContext(Dispatchers.IO) {
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://id.twitch.tv/oauth2/revoke?client_id=${clientId}&token=${token}", request.callback, cronetExecutor).build().start()
        } else {
            okHttpClient.newCall(Request.Builder().url("https://id.twitch.tv/oauth2/revoke?client_id=${clientId}&token=${token}").build()).execute()
        }
    }

    suspend fun getDeviceCode(useCronet: Boolean, body: String): DeviceCodeResponse = withContext(Dispatchers.IO) {
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://id.twitch.tv/oauth2/device", request.callback, cronetExecutor).apply {
                addHeader("Content-Type", "application/x-www-form-urlencoded")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<DeviceCodeResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://id.twitch.tv/oauth2/device")
                header("Content-Type", "application/x-www-form-urlencoded")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<DeviceCodeResponse>(response.body.string())
            }
        }
    }

    suspend fun getToken(useCronet: Boolean, body: String): TokenResponse = withContext(Dispatchers.IO) {
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://id.twitch.tv/oauth2/token", request.callback, cronetExecutor).apply {
                addHeader("Content-Type", "application/x-www-form-urlencoded")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<TokenResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://id.twitch.tv/oauth2/token")
                header("Content-Type", "application/x-www-form-urlencoded")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<TokenResponse>(response.body.string())
            }
        }
    }
}
