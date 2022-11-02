package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.chat.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface MiscApi {

    @GET("https://www.twitch.tv/{channelLogin}")
    suspend fun getChannelPage(@Path("channelLogin") channelLogin: String): ResponseBody

    @GET
    suspend fun getUrl(@Url url: String): ResponseBody

    @POST
    suspend fun postUrl(@Url url: String, @Body body: RequestBody): Response<Unit>

    @POST("https://gql.twitch.tv/integrity")
    suspend fun getClientIntegrityToken(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("X-Device-Id") deviceId: String?): ResponseBody

    @GET("https://recent-messages.robotty.de/api/v2/recent-messages/{channelLogin}")
    suspend fun getRecentMessages(@Path("channelLogin") channelLogin: String, @Query("limit") limit: String): Response<RecentMessagesResponse>

    @GET("https://api.7tv.app/v2/emotes/global")
    suspend fun getGlobalStvEmotes(): Response<StvEmotesResponse>

    @GET("https://api.7tv.app/v2/users/{channelId}/emotes")
    suspend fun getStvEmotes(@Path("channelId") channelId: String): Response<StvEmotesResponse>

    @GET("https://api.betterttv.net/3/cached/emotes/global")
    suspend fun getGlobalBttvEmotes(): Response<BttvGlobalResponse>

    @GET("https://api.betterttv.net/3/cached/users/twitch/{channelId}")
    suspend fun getBttvEmotes(@Path("channelId") channelId: String): Response<BttvChannelResponse>

    @GET("https://api.betterttv.net/3/cached/frankerfacez/emotes/global")
    suspend fun getBttvGlobalFfzEmotes(): Response<BttvFfzResponse>

    @GET("https://api.betterttv.net/3/cached/frankerfacez/users/twitch/{channelId}")
    suspend fun getBttvFfzEmotes(@Path("channelId") channelId: String): Response<BttvFfzResponse>
}