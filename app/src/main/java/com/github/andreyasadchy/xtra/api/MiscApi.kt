package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.misc.BttvResponse
import com.github.andreyasadchy.xtra.model.misc.FfzChannelResponse
import com.github.andreyasadchy.xtra.model.misc.FfzGlobalResponse
import com.github.andreyasadchy.xtra.model.misc.RecentMessagesResponse
import com.github.andreyasadchy.xtra.model.misc.StvChannelResponse
import com.github.andreyasadchy.xtra.model.misc.StvGlobalResponse
import kotlinx.serialization.json.JsonElement
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface MiscApi {

    @GET("https://www.twitch.tv/{channelLogin}")
    suspend fun getChannelPage(@Path("channelLogin") channelLogin: String): ResponseBody

    @GET
    suspend fun getUrl(@Url url: String): ResponseBody

    @POST
    suspend fun postUrl(@Url url: String, @Body body: RequestBody)

    @GET("https://recent-messages.robotty.de/api/v2/recent-messages/{channelLogin}")
    suspend fun getRecentMessages(@Path("channelLogin") channelLogin: String, @Query("limit") limit: String): RecentMessagesResponse

    @GET("https://7tv.io/v3/emote-sets/global")
    suspend fun getGlobalStvEmotes(): StvGlobalResponse

    @GET("https://7tv.io/v3/users/twitch/{channelId}")
    suspend fun getStvEmotes(@Path("channelId") channelId: String): StvChannelResponse

    @GET("https://7tv.io/v3/users/twitch/{userId}")
    suspend fun getStvUser(@Path("userId") userId: String): ResponseBody

    @POST("https://7tv.io/v3/users/{userId}/presences")
    suspend fun sendStvPresence(@Path("userId") userId: String, @Body body: RequestBody)

    @GET("https://api.betterttv.net/3/cached/emotes/global")
    suspend fun getGlobalBttvEmotes(): List<BttvResponse>

    @GET("https://api.betterttv.net/3/cached/users/twitch/{channelId}")
    suspend fun getBttvEmotes(@Path("channelId") channelId: String): Map<String, JsonElement>

    @GET("https://api.frankerfacez.com/v1/set/global")
    suspend fun getGlobalFfzEmotes(): FfzGlobalResponse

    @GET("https://api.frankerfacez.com/v1/room/id/{channelId}")
    suspend fun getFfzEmotes(@Path("channelId") channelId: String): FfzChannelResponse
}