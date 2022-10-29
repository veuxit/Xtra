package com.github.andreyasadchy.xtra.api

import com.github.andreyasadchy.xtra.model.chat.EmoteCardResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelHostingDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelViewerListDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipUrlsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.model.gql.emote.UserEmotesDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.*
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.points.ChannelPointsContextDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.*
import com.github.andreyasadchy.xtra.model.gql.vod.VodGamesDataResponse
import com.github.andreyasadchy.xtra.model.query.*
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST

@JvmSuppressWildcards
interface GraphQLApi {

    @POST(".")
    suspend fun getQueryCheerEmotes(@Header("Client-ID") clientId: String?, @Body json: JsonObject): CheerEmotesQueryResponse

    @POST(".")
    suspend fun getQueryFollowedGames(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedGamesQueryResponse

    @POST(".")
    suspend fun getQueryFollowedStreams(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedStreamsQueryResponse

    @POST(".")
    suspend fun getQueryFollowedUsers(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedUsersQueryResponse

    @POST(".")
    suspend fun getQueryFollowedVideos(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedVideosQueryResponse

    @POST(".")
    suspend fun getQueryGameBoxArt(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameBoxArtQueryResponse

    @POST(".")
    suspend fun getQueryGameClips(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameClipsQueryResponse

    @POST(".")
    suspend fun getQueryGameStreams(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameStreamsQueryResponse

    @POST(".")
    suspend fun getQueryGameVideos(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameVideosQueryResponse

    @POST(".")
    suspend fun getQuerySearchChannels(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchChannelsQueryResponse

    @POST(".")
    suspend fun getQuerySearchGames(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchGamesQueryResponse

    @POST(".")
    suspend fun getQuerySearchStreams(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchStreamsQueryResponse

    @POST(".")
    suspend fun getQuerySearchVideos(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchVideosQueryResponse

    @POST(".")
    suspend fun getQueryTopGames(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TopGamesQueryResponse

    @POST(".")
    suspend fun getQueryTopStreams(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TopStreamsQueryResponse

    @POST(".")
    suspend fun getQueryUser(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UserQueryResponse

    @POST(".")
    suspend fun getQueryUserChannelPage(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UserChannelPageQueryResponse

    @POST(".")
    suspend fun getQueryUserClips(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UserClipsQueryResponse

    @POST(".")
    suspend fun getQueryUserEmotes(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): UserEmotesQueryResponse

    @POST(".")
    suspend fun getQueryUserHosting(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UserHostingQueryResponse

    @POST(".")
    suspend fun getQueryUserMessageClicked(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UserMessageClickedQueryResponse

    @POST(".")
    suspend fun getQueryUserVideos(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UserVideosQueryResponse

    @POST(".")
    suspend fun getQueryUsersLastBroadcast(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UsersLastBroadcastQueryResponse

    @POST(".")
    suspend fun getQueryUsersStream(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UsersStreamQueryResponse

    @POST(".")
    suspend fun getQueryUsersType(@Header("Client-ID") clientId: String?, @Body json: JsonObject): UsersTypeQueryResponse

    @POST(".")
    suspend fun getQueryVideo(@Header("Client-ID") clientId: String?, @Body json: JsonObject): VideoQueryResponse

    @POST(".")
    suspend fun getPlaybackAccessToken(@Header("Client-ID") clientId: String?, @HeaderMap headers: Map<String, String>, @Body json: JsonObject): PlaybackAccessTokenResponse

    @POST(".")
    suspend fun getClipUrls(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ClipUrlsResponse

    @POST(".")
    suspend fun getClipData(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ClipDataResponse

    @POST(".")
    suspend fun getClipVideo(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ClipVideoResponse

    @POST(".")
    suspend fun getTopGames(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameDataResponse

    @POST(".")
    suspend fun getTopStreams(@Header("Client-ID") clientId: String?, @Body json: JsonObject): StreamDataResponse

    @POST(".")
    suspend fun getGameStreams(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameStreamsDataResponse

    @POST(".")
    suspend fun getGameVideos(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameVideosDataResponse

    @POST(".")
    suspend fun getGameClips(@Header("Client-ID") clientId: String?, @Body json: JsonObject): GameClipsDataResponse

    @POST(".")
    suspend fun getChannelVideos(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ChannelVideosDataResponse

    @POST(".")
    suspend fun getChannelClips(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ChannelClipsDataResponse

    @POST(".")
    suspend fun getChannelViewerList(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ChannelViewerListDataResponse

    @POST(".")
    suspend fun getSearchChannels(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchChannelDataResponse

    @POST(".")
    suspend fun getSearchGames(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchGameDataResponse

    @POST(".")
    suspend fun getSearchVideos(@Header("Client-ID") clientId: String?, @Body json: JsonObject): SearchVideosDataResponse

    @POST(".")
    suspend fun getGameTags(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TagGameDataResponse

    @POST(".")
    suspend fun getGameStreamTags(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TagGameStreamDataResponse

    @POST(".")
    suspend fun getStreamTags(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TagStreamDataResponse

    @POST(".")
    suspend fun getSearchGameTags(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TagSearchGameStreamDataResponse

    @POST(".")
    suspend fun getSearchStreamTags(@Header("Client-ID") clientId: String?, @Body json: JsonObject): TagSearchDataResponse

    @POST(".")
    suspend fun getVodGames(@Header("Client-ID") clientId: String?, @Body json: JsonObject): VodGamesDataResponse

    @POST(".")
    suspend fun getViewerCount(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ViewersDataResponse

    @POST(".")
    suspend fun getChannelHosting(@Header("Client-ID") clientId: String?, @Body json: JsonObject): ChannelHostingDataResponse

    @POST(".")
    suspend fun getEmoteCard(@Header("Client-ID") clientId: String?, @Body json: JsonObject): EmoteCardResponse

    @POST(".")
    suspend fun getFollowedStreams(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedStreamsDataResponse

    @POST(".")
    suspend fun getFollowedVideos(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedVideosDataResponse

    @POST(".")
    suspend fun getFollowedChannels(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedChannelsDataResponse

    @POST(".")
    suspend fun getFollowedGames(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowedGamesDataResponse

    @POST(".")
    suspend fun getFollowUser(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("Client-Integrity") integrityToken: String?, @Header("X-Device-Id") deviceId: String?, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getUnfollowUser(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("Client-Integrity") integrityToken: String?, @Header("X-Device-Id") deviceId: String?, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getFollowGame(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("Client-Integrity") integrityToken: String?, @Header("X-Device-Id") deviceId: String?, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getUnfollowGame(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("Client-Integrity") integrityToken: String?, @Header("X-Device-Id") deviceId: String?, @Body json: JsonObject): FollowDataResponse

    @POST(".")
    suspend fun getFollowingUser(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowingUserDataResponse

    @POST(".")
    suspend fun getFollowingGame(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): FollowingGameDataResponse

    @POST(".")
    suspend fun getChannelPointsContext(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): ChannelPointsContextDataResponse

    @POST(".")
    suspend fun getClaimPoints(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("Client-Integrity") integrityToken: String?, @Header("X-Device-Id") deviceId: String?, @Body json: JsonObject)

    @POST(".")
    suspend fun getJoinRaid(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Header("Client-Integrity") integrityToken: String?, @Header("X-Device-Id") deviceId: String?, @Body json: JsonObject)

    @POST(".")
    suspend fun getUserEmotes(@Header("Client-ID") clientId: String?, @Header("Authorization") token: String?, @Body json: JsonObject): UserEmotesDataResponse

    @POST(".")
    suspend fun getChannelPanel(@Body json: JsonArray): Response<ResponseBody>
}