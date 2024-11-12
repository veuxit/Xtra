package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.gql.ErrorResponse
import com.github.andreyasadchy.xtra.model.gql.QueryResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelViewerListResponse
import com.github.andreyasadchy.xtra.model.gql.chat.BadgesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelCheerEmotesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointContextResponse
import com.github.andreyasadchy.xtra.model.gql.chat.EmoteCardResponse
import com.github.andreyasadchy.xtra.model.gql.chat.GlobalCheerEmotesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ModeratorsResponse
import com.github.andreyasadchy.xtra.model.gql.chat.UserEmotesResponse
import com.github.andreyasadchy.xtra.model.gql.chat.VipsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipUrlsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedChannelsResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedGamesResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedStreamsResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedVideosResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingGameResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingUserResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosResponse
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelsResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameTagsResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGamesResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchStreamTagsResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewerCountResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoGamesResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

@JvmSuppressWildcards
interface GraphQLApi {

    @POST(".")
    suspend fun getQueryBadges(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryGameBoxArt(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryGameClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryGameStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryGameVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQuerySearchChannels(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQuerySearchGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQuerySearchStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQuerySearchVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryTopGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryTopStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserBadges(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserChannelPage(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getUserCheerEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedUsers(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserMessageClicked(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserResultID(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserResultLogin(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUserVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUsersLastBroadcast(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUsersStream(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryUsersType(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getQueryVideo(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): QueryResponse

    @POST(".")
    suspend fun getPlaybackAccessToken(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): PlaybackAccessTokenResponse

    @POST(".")
    suspend fun getClipUrls(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ClipUrlsResponse

    @POST(".")
    suspend fun getClipData(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ClipDataResponse

    @POST(".")
    suspend fun getClipVideo(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ClipVideoResponse

    @POST(".")
    suspend fun getTopGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GamesResponse

    @POST(".")
    suspend fun getTopStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): StreamsResponse

    @POST(".")
    suspend fun getGameStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameStreamsResponse

    @POST(".")
    suspend fun getGameVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameVideosResponse

    @POST(".")
    suspend fun getGameClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameClipsResponse

    @POST(".")
    suspend fun getChannelVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelVideosResponse

    @POST(".")
    suspend fun getChannelClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelClipsResponse

    @POST(".")
    suspend fun getSearchChannels(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchChannelsResponse

    @POST(".")
    suspend fun getSearchGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchGamesResponse

    @POST(".")
    suspend fun getSearchVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchVideosResponse

    @POST(".")
    suspend fun getFreeformTags(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchStreamTagsResponse

    @POST(".")
    suspend fun getGameTags(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchGameTagsResponse

    @POST(".")
    suspend fun getChatBadges(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): BadgesResponse

    @POST(".")
    suspend fun getGlobalCheerEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GlobalCheerEmotesResponse

    @POST(".")
    suspend fun getChannelCheerEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelCheerEmotesResponse

    @POST(".")
    suspend fun getVideoMessages(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): VideoMessagesResponse

    @POST(".")
    suspend fun getVideoMessagesDownload(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): JsonElement

    @POST(".")
    suspend fun getVideoGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): VideoGamesResponse

    @POST(".")
    suspend fun getChannelViewerList(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelViewerListResponse

    @POST(".")
    suspend fun getViewerCount(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ViewerCountResponse

    @POST(".")
    suspend fun getEmoteCard(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): EmoteCardResponse

    @POST(".")
    suspend fun getChannelPanel(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ResponseBody>

    @POST(".")
    suspend fun getFollowedStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedStreamsResponse

    @POST(".")
    suspend fun getFollowedVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedVideosResponse

    @POST(".")
    suspend fun getFollowedChannels(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedChannelsResponse

    @POST(".")
    suspend fun getFollowedGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedGamesResponse

    @POST(".")
    suspend fun getFollowUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ErrorResponse

    @POST(".")
    suspend fun getUnfollowUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ErrorResponse

    @POST(".")
    suspend fun getToggleNotificationsUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ErrorResponse

    @POST(".")
    suspend fun getFollowGame(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ErrorResponse

    @POST(".")
    suspend fun getUnfollowGame(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ErrorResponse

    @POST(".")
    suspend fun getFollowingUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowingUserResponse

    @POST(".")
    suspend fun getFollowingGame(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowingGameResponse

    @POST(".")
    suspend fun getChannelPointsContext(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelPointContextResponse

    @POST(".")
    suspend fun getClaimPoints(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ErrorResponse

    @POST(".")
    suspend fun getJoinRaid(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun getUserEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserEmotesResponse

    @POST(".")
    suspend fun sendAnnouncement(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun banUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun unbanUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun updateChatColor(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun createStreamMarker(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun getModerators(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ModeratorsResponse>

    @POST(".")
    suspend fun addModerator(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun removeModerator(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun startRaid(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun cancelRaid(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun getVips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<VipsResponse>

    @POST(".")
    suspend fun addVip(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>

    @POST(".")
    suspend fun removeVip(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ErrorResponse>
}