package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchResponse
import com.github.andreyasadchy.xtra.model.helix.chat.BadgesResponse
import com.github.andreyasadchy.xtra.model.helix.chat.ChatUsersResponse
import com.github.andreyasadchy.xtra.model.helix.chat.CheerEmotesResponse
import com.github.andreyasadchy.xtra.model.helix.chat.EmoteSetsResponse
import com.github.andreyasadchy.xtra.model.helix.chat.UserEmotesResponse
import com.github.andreyasadchy.xtra.model.helix.clip.ClipsResponse
import com.github.andreyasadchy.xtra.model.helix.follows.FollowsResponse
import com.github.andreyasadchy.xtra.model.helix.game.GamesResponse
import com.github.andreyasadchy.xtra.model.helix.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.helix.user.UsersResponse
import com.github.andreyasadchy.xtra.model.helix.video.VideosResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface HelixApi {

    @GET("games")
    suspend fun getGames(
        @HeaderMap headers: Map<String, String>,
        @Query("id") ids: List<String>? = null,
        @Query("name") names: List<String>? = null
    ): GamesResponse

    @GET("games/top")
    suspend fun getTopGames(
        @HeaderMap headers: Map<String, String>,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): GamesResponse

    @GET("streams")
    suspend fun getStreams(
        @HeaderMap headers: Map<String, String>,
        @Query("user_id") ids: List<String>? = null,
        @Query("user_login") logins: List<String>? = null,
        @Query("game_id") gameId: String? = null,
        @Query("language") languages: List<String>? = null,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): StreamsResponse

    @GET("streams/followed")
    suspend fun getFollowedStreams(
        @HeaderMap headers: Map<String, String>,
        @Query("user_id") userId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): StreamsResponse

    @GET("clips")
    suspend fun getClips(
        @HeaderMap headers: Map<String, String>,
        @Query("id") ids: List<String>? = null,
        @Query("broadcaster_id") channelId: String? = null,
        @Query("game_id") gameId: String? = null,
        @Query("started_at") startedAt: String? = null,
        @Query("ended_at") endedAt: String? = null,
        @Query("first") limit: Int? = null,
        @Query("after") cursor: String? = null
    ): ClipsResponse

    @GET("videos")
    suspend fun getVideos(
        @HeaderMap headers: Map<String, String>,
        @Query("id") ids: List<String>? = null,
        @Query("game_id") gameId: String? = null,
        @Query("user_id") channelId: String? = null,
        @Query("period") period: String? = null,
        @Query("type") broadcastType: String? = null,
        @Query("sort") sort: String? = null,
        @Query("language") language: String? = null,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): VideosResponse

    @GET("users")
    suspend fun getUsers(
        @HeaderMap headers: Map<String, String>,
        @Query("id") ids: List<String>? = null,
        @Query("login") logins: List<String>? = null
    ): UsersResponse

    @GET("search/categories")
    suspend fun getSearchGames(
        @HeaderMap headers: Map<String, String>,
        @Query("query") query: String,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): GamesResponse

    @GET("search/channels")
    suspend fun getSearchChannels(
        @HeaderMap headers: Map<String, String>,
        @Query("query") query: String,
        @Query("first") limit: Int?,
        @Query("after") offset: String?,
        @Query("live_only") live: Boolean? = null
    ): ChannelSearchResponse

    @GET("channels/followed")
    suspend fun getUserFollows(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") targetId: String? = null,
        @Query("user_id") userId: String?,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): FollowsResponse

    @GET("channels/followers")
    suspend fun getUserFollowers(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") userId: String?,
        @Query("user_id") targetId: String? = null,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): FollowsResponse

    @GET("chat/emotes/user")
    suspend fun getUserEmotes(
        @HeaderMap headers: Map<String, String>,
        @Query("user_id") userId: String?,
        @Query("broadcaster_id") channelId: String? = null,
        @Query("after") offset: String? = null
    ): UserEmotesResponse

    @GET("chat/emotes/set")
    suspend fun getEmotesFromSet(
        @HeaderMap headers: Map<String, String>,
        @Query("emote_set_id") setIds: List<String>
    ): EmoteSetsResponse

    @GET("chat/badges/global")
    suspend fun getGlobalBadges(
        @HeaderMap headers: Map<String, String>,
    ): BadgesResponse

    @GET("chat/badges")
    suspend fun getChannelBadges(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") userId: String?
    ): BadgesResponse

    @GET("bits/cheermotes")
    suspend fun getCheerEmotes(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") userId: String?
    ): CheerEmotesResponse

    @GET("chat/chatters")
    suspend fun getChatters(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): Response<ChatUsersResponse>

    @POST("eventsub/subscriptions")
    suspend fun createEventSubSubscription(
        @HeaderMap headers: Map<String, String>,
        @Body json: JsonObject
    ): Response<JsonElement>

    @POST("chat/messages")
    suspend fun sendMessage(
        @HeaderMap headers: Map<String, String>,
        @Body json: JsonObject
    ): Response<JsonElement>

    @POST("chat/announcements")
    suspend fun sendAnnouncement(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @POST("moderation/bans")
    suspend fun banUser(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @DELETE("moderation/bans")
    suspend fun unbanUser(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("moderation/chat")
    suspend fun deleteMessages(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Query("message_id") messageId: String?
    ): Response<JsonElement>

    @GET("chat/color")
    suspend fun getChatColor(
        @HeaderMap headers: Map<String, String>,
        @Query("user_id") userId: String?
    ): Response<JsonElement>

    @PUT("chat/color")
    suspend fun updateChatColor(
        @HeaderMap headers: Map<String, String>,
        @Query("user_id") userId: String?,
        @Query("color") color: String?
    ): Response<JsonElement>

    @POST("channels/commercial")
    suspend fun startCommercial(
        @HeaderMap headers: Map<String, String>,
        @Body json: JsonObject
    ): Response<JsonElement>

    @PATCH("chat/settings")
    suspend fun updateChatSettings(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @POST("streams/markers")
    suspend fun createStreamMarker(
        @HeaderMap headers: Map<String, String>,
        @Body json: JsonObject
    ): Response<JsonElement>

    @GET("moderation/moderators")
    suspend fun getModerators(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): Response<ChatUsersResponse>

    @POST("moderation/moderators")
    suspend fun addModerator(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("moderation/moderators")
    suspend fun removeModerator(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @POST("raids")
    suspend fun startRaid(
        @HeaderMap headers: Map<String, String>,
        @Query("from_broadcaster_id") channelId: String?,
        @Query("to_broadcaster_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("raids")
    suspend fun cancelRaid(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
    ): Response<JsonElement>

    @GET("channels/vips")
    suspend fun getVips(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): Response<ChatUsersResponse>

    @POST("channels/vips")
    suspend fun addVip(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("channels/vips")
    suspend fun removeVip(
        @HeaderMap headers: Map<String, String>,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @POST("whispers")
    suspend fun sendWhisper(
        @HeaderMap headers: Map<String, String>,
        @Query("from_user_id") userId: String?,
        @Query("to_user_id") targetId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>
}