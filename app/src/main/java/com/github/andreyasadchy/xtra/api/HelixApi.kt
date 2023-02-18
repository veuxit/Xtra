package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchResponse
import com.github.andreyasadchy.xtra.model.helix.chat.*
import com.github.andreyasadchy.xtra.model.helix.clip.ClipsResponse
import com.github.andreyasadchy.xtra.model.helix.follows.FollowResponse
import com.github.andreyasadchy.xtra.model.helix.game.GamesResponse
import com.github.andreyasadchy.xtra.model.helix.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.helix.user.UsersResponse
import com.github.andreyasadchy.xtra.model.helix.video.VideosResponse
import com.github.andreyasadchy.xtra.model.ui.BroadcastTypeEnum
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface HelixApi {

    @GET("games")
    suspend fun getGames(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("id") ids: List<String>? = null,
        @Query("name") names: List<String>? = null
    ): GamesResponse

    @GET("games/top")
    suspend fun getTopGames(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): GamesResponse

    @GET("streams")
    suspend fun getStreams(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("user_id") ids: List<String>? = null,
        @Query("user_login") logins: List<String>? = null,
        @Query("game_id") gameId: String? = null,
        @Query("language") languages: List<String>? = null,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): StreamsResponse

    @GET("streams/followed")
    suspend fun getFollowedStreams(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("user_id") userId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): StreamsResponse

    @GET("clips")
    suspend fun getClips(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("id") ids: List<String>? = null,
        @Query("broadcaster_id") channelId: String? = null,
        @Query("game_id") gameId: String? = null,
        @Query("started_at") started_at: String? = null,
        @Query("ended_at") ended_at: String? = null,
        @Query("first") limit: Int? = null,
        @Query("after") cursor: String? = null
    ): ClipsResponse

    @GET("videos")
    suspend fun getVideos(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("id") ids: List<String>? = null,
        @Query("game_id") gameId: String? = null,
        @Query("user_id") channelId: String? = null,
        @Query("period") period: VideoPeriodEnum? = null,
        @Query("type") broadcastType: BroadcastTypeEnum? = null,
        @Query("sort") sort: VideoSortEnum? = null,
        @Query("language") language: String? = null,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): VideosResponse

    @GET("users")
    suspend fun getUsers(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("id") ids: List<String>? = null,
        @Query("login") logins: List<String>? = null
    ): UsersResponse

    @GET("search/categories")
    suspend fun getSearchGames(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("query") query: String,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): GamesResponse

    @GET("search/channels")
    suspend fun getSearchChannels(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("query") query: String,
        @Query("first") limit: Int?,
        @Query("after") offset: String?,
        @Query("live_only") live: Boolean? = null
    ): ChannelSearchResponse

    @GET("channels/followed")
    suspend fun getUserFollows(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") targetId: String? = null,
        @Query("user_id") userId: String?,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): FollowResponse

    @GET("channels/followers")
    suspend fun getUserFollowers(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") userId: String?,
        @Query("user_id") targetId: String? = null,
        @Query("first") limit: Int? = null,
        @Query("after") offset: String? = null
    ): FollowResponse

    @GET("chat/emotes/set")
    suspend fun getEmotesFromSet(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("emote_set_id") setIds: List<String>
    ): EmoteSetResponse

    @GET("chat/badges/global")
    suspend fun getGlobalBadges(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
    ): ChatBadgesResponse

    @GET("chat/badges")
    suspend fun getChannelBadges(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") userId: String?
    ): ChatBadgesResponse

    @GET("bits/cheermotes")
    suspend fun getCheerEmotes(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") userId: String?
    ): CheerEmotesResponse

    @GET("chat/chatters")
    suspend fun getChatters(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): Response<ModeratorsResponse>

    @POST("chat/announcements")
    suspend fun sendAnnouncement(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @POST("moderation/bans")
    suspend fun banUser(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @DELETE("moderation/bans")
    suspend fun unbanUser(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("moderation/chat")
    suspend fun deleteMessages(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Query("message_id") messageId: String?
    ): Response<JsonElement>

    @GET("chat/color")
    suspend fun getChatColor(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("user_id") userId: String?
    ): Response<JsonElement>

    @PUT("chat/color")
    suspend fun updateChatColor(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("user_id") userId: String?,
        @Query("color") color: String?
    ): Response<JsonElement>

    @POST("channels/commercial")
    suspend fun startCommercial(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @PATCH("chat/settings")
    suspend fun updateChatSettings(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("moderator_id") userId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @POST("streams/markers")
    suspend fun createStreamMarker(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Body json: JsonObject
    ): Response<JsonElement>

    @GET("moderation/moderators")
    suspend fun getModerators(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): Response<ModeratorsResponse>

    @POST("moderation/moderators")
    suspend fun addModerator(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("moderation/moderators")
    suspend fun removeModerator(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @POST("raids")
    suspend fun startRaid(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("from_broadcaster_id") channelId: String?,
        @Query("to_broadcaster_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("raids")
    suspend fun cancelRaid(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
    ): Response<JsonElement>

    @GET("channels/vips")
    suspend fun getVips(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("first") limit: Int?,
        @Query("after") offset: String?
    ): Response<ModeratorsResponse>

    @POST("channels/vips")
    suspend fun addVip(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @DELETE("channels/vips")
    suspend fun removeVip(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("broadcaster_id") channelId: String?,
        @Query("user_id") targetId: String?
    ): Response<JsonElement>

    @POST("whispers")
    suspend fun sendWhisper(
        @Header("Client-ID") clientId: String?,
        @Header("Authorization") token: String?,
        @Query("from_user_id") userId: String?,
        @Query("to_user_id") targetId: String?,
        @Body json: JsonObject
    ): Response<JsonElement>
}