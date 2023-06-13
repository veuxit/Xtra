package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.gql.channel.*
import com.github.andreyasadchy.xtra.model.gql.chat.*
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipUrlsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.model.gql.followed.*
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.*
import com.github.andreyasadchy.xtra.model.gql.video.VideoGamesDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDataResponse
import com.github.andreyasadchy.xtra.model.query.BadgesQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameBoxArtQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameClipsQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchChannelsQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.TopGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.TopStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserBadgesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserChannelPageQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserCheerEmotesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserClipsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserEmotesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedUsersQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserMessageClickedQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserResultIDQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserResultLoginQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersLastBroadcastQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersStreamQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersTypeQueryResponse
import com.github.andreyasadchy.xtra.model.query.VideoQueryResponse
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

@JvmSuppressWildcards
interface GraphQLApi {

    @POST(".")
    suspend fun getQueryBadges(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): BadgesQueryResponse

    @POST(".")
    suspend fun getQueryGameBoxArt(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameBoxArtQueryResponse

    @POST(".")
    suspend fun getQueryGameClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameClipsQueryResponse

    @POST(".")
    suspend fun getQueryGameStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameStreamsQueryResponse

    @POST(".")
    suspend fun getQueryGameVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameVideosQueryResponse

    @POST(".")
    suspend fun getQuerySearchChannels(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchChannelsQueryResponse

    @POST(".")
    suspend fun getQuerySearchGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchGamesQueryResponse

    @POST(".")
    suspend fun getQuerySearchStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchStreamsQueryResponse

    @POST(".")
    suspend fun getQuerySearchVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchVideosQueryResponse

    @POST(".")
    suspend fun getQueryTopGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): TopGamesQueryResponse

    @POST(".")
    suspend fun getQueryTopStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): TopStreamsQueryResponse

    @POST(".")
    suspend fun getQueryUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserQueryResponse

    @POST(".")
    suspend fun getQueryUserBadges(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserBadgesQueryResponse

    @POST(".")
    suspend fun getQueryUserChannelPage(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserChannelPageQueryResponse

    @POST(".")
    suspend fun getUserCheerEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserCheerEmotesQueryResponse

    @POST(".")
    suspend fun getQueryUserClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserClipsQueryResponse

    @POST(".")
    suspend fun getQueryUserEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserEmotesQueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserFollowedGamesQueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserFollowedStreamsQueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedUsers(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserFollowedUsersQueryResponse

    @POST(".")
    suspend fun getQueryUserFollowedVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserFollowedVideosQueryResponse

    @POST(".")
    suspend fun getQueryUserMessageClicked(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserMessageClickedQueryResponse

    @POST(".")
    suspend fun getQueryUserVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserVideosQueryResponse

    @POST(".")
    suspend fun getQueryUsersLastBroadcast(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UsersLastBroadcastQueryResponse

    @POST(".")
    suspend fun getQueryUsersStream(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UsersStreamQueryResponse

    @POST(".")
    suspend fun getQueryUsersType(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UsersTypeQueryResponse

    @POST(".")
    suspend fun getQueryVideo(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): VideoQueryResponse

    @POST(".")
    suspend fun getQueryUserResultID(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserResultIDQueryResponse

    @POST(".")
    suspend fun getQueryUserResultLogin(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserResultLoginQueryResponse

    @POST(".")
    suspend fun getPlaybackAccessToken(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): PlaybackAccessTokenResponse

    @POST(".")
    suspend fun getClipUrls(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ClipUrlsResponse>

    @POST(".")
    suspend fun getClipData(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ClipDataResponse

    @POST(".")
    suspend fun getClipVideo(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ClipVideoResponse

    @POST(".")
    suspend fun getTopGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameDataResponse

    @POST(".")
    suspend fun getTopStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): StreamsDataResponse

    @POST(".")
    suspend fun getGameStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameStreamsDataResponse

    @POST(".")
    suspend fun getGameVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameVideosDataResponse

    @POST(".")
    suspend fun getGameClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GameClipsDataResponse

    @POST(".")
    suspend fun getChannelVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelVideosDataResponse

    @POST(".")
    suspend fun getChannelClips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelClipsDataResponse

    @POST(".")
    suspend fun getSearchChannels(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchChannelDataResponse

    @POST(".")
    suspend fun getSearchGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchGameDataResponse

    @POST(".")
    suspend fun getSearchVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): SearchVideosDataResponse

    @POST(".")
    suspend fun getFreeformTags(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FreeformTagDataResponse

    @POST(".")
    suspend fun getGameTags(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): TagGameDataResponse

    @POST(".")
    suspend fun getChatBadges(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChatBadgesDataResponse

    @POST(".")
    suspend fun getGlobalCheerEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): GlobalCheerEmotesDataResponse

    @POST(".")
    suspend fun getChannelCheerEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelCheerEmotesDataResponse

    @POST(".")
    suspend fun getVideoMessages(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): VideoMessagesDataResponse

    @POST(".")
    suspend fun getVideoGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): VideoGamesDataResponse

    @POST(".")
    suspend fun getChannelViewerList(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelViewerListDataResponse

    @POST(".")
    suspend fun getViewerCount(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ViewersDataResponse

    @POST(".")
    suspend fun getEmoteCard(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): EmoteCardResponse

    @POST(".")
    suspend fun getChannelPanel(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ResponseBody>

    @POST(".")
    suspend fun getFollowedStreams(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedStreamsDataResponse

    @POST(".")
    suspend fun getFollowedVideos(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedVideosDataResponse

    @POST(".")
    suspend fun getFollowedChannels(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedChannelsDataResponse

    @POST(".")
    suspend fun getFollowedGames(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowedGamesDataResponse

    @POST(".")
    suspend fun getFollowUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getUnfollowUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getFollowGame(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getUnfollowGame(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getFollowingUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowingUserDataResponse

    @POST(".")
    suspend fun getFollowingGame(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): FollowingGameDataResponse

    @POST(".")
    suspend fun getChannelPointsContext(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): ChannelPointsContextDataResponse

    @POST(".")
    suspend fun getClaimPoints(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun getJoinRaid(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun getUserEmotes(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): UserEmotesDataResponse

    @POST(".")
    suspend fun sendAnnouncement(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun banUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun unbanUser(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun updateChatColor(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun createStreamMarker(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun getModerators(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<ModeratorsDataResponse>

    @POST(".")
    suspend fun addModerator(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun removeModerator(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun startRaid(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun cancelRaid(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun getVips(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<VipsDataResponse>

    @POST(".")
    suspend fun addVip(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>

    @POST(".")
    suspend fun removeVip(@HeaderMap headers: Map<String, String>, @Body json: JsonObject): Response<JsonElement>
}