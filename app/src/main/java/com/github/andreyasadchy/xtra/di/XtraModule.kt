package com.github.andreyasadchy.xtra.di

import android.app.Application
import android.os.Build
import android.util.Log
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.api.GraphQLApi
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.api.IdApi
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.api.UsherApi
import com.github.andreyasadchy.xtra.model.chat.BttvChannelDeserializer
import com.github.andreyasadchy.xtra.model.chat.BttvChannelResponse
import com.github.andreyasadchy.xtra.model.chat.BttvGlobalDeserializer
import com.github.andreyasadchy.xtra.model.chat.BttvGlobalResponse
import com.github.andreyasadchy.xtra.model.chat.FfzChannelDeserializer
import com.github.andreyasadchy.xtra.model.chat.FfzChannelResponse
import com.github.andreyasadchy.xtra.model.chat.FfzGlobalDeserializer
import com.github.andreyasadchy.xtra.model.chat.FfzGlobalResponse
import com.github.andreyasadchy.xtra.model.chat.RecentMessagesDeserializer
import com.github.andreyasadchy.xtra.model.chat.RecentMessagesResponse
import com.github.andreyasadchy.xtra.model.chat.StvChannelDeserializer
import com.github.andreyasadchy.xtra.model.chat.StvChannelResponse
import com.github.andreyasadchy.xtra.model.chat.StvGlobalDeserializer
import com.github.andreyasadchy.xtra.model.chat.StvGlobalResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelViewerListDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.channel.ChannelViewerListDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelCheerEmotesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelCheerEmotesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointsContextDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointsContextDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ChatBadgesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.ChatBadgesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.EmoteCardDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.EmoteCardResponse
import com.github.andreyasadchy.xtra.model.gql.chat.GlobalCheerEmotesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.GlobalCheerEmotesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.ModeratorsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.ModeratorsDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.UserEmotesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.UserEmotesDataResponse
import com.github.andreyasadchy.xtra.model.gql.chat.VipsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.chat.VipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.clip.ClipDataResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipUrlsDeserializer
import com.github.andreyasadchy.xtra.model.gql.clip.ClipUrlsResponse
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoDeserializer
import com.github.andreyasadchy.xtra.model.gql.clip.ClipVideoResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedChannelsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedChannelsDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedGamesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedGamesDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedStreamsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedVideosDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowedVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingGameDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingUserDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.followed.FollowingUserDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.game.GameClipsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.game.GameDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.game.GameStreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.game.GameVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenDeserializer
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.search.SearchChannelDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.search.SearchGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.search.SearchVideosDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.FreeformTagDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.tag.FreeformTagDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.TagGameDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.tag.TagGameDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoGamesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.video.VideoGamesDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDownloadDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDownloadDataResponse
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchDeserializer
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchResponse
import com.github.andreyasadchy.xtra.model.helix.chat.ChatBadgesDeserializer
import com.github.andreyasadchy.xtra.model.helix.chat.ChatBadgesResponse
import com.github.andreyasadchy.xtra.model.helix.chat.CheerEmotesDeserializer
import com.github.andreyasadchy.xtra.model.helix.chat.CheerEmotesResponse
import com.github.andreyasadchy.xtra.model.helix.chat.EmoteSetDeserializer
import com.github.andreyasadchy.xtra.model.helix.chat.EmoteSetResponse
import com.github.andreyasadchy.xtra.model.helix.chat.ModeratorsDeserializer
import com.github.andreyasadchy.xtra.model.helix.chat.ModeratorsResponse
import com.github.andreyasadchy.xtra.model.helix.chat.UserEmotesDeserializer
import com.github.andreyasadchy.xtra.model.helix.chat.UserEmotesResponse
import com.github.andreyasadchy.xtra.model.helix.clip.ClipsDeserializer
import com.github.andreyasadchy.xtra.model.helix.clip.ClipsResponse
import com.github.andreyasadchy.xtra.model.helix.follows.FollowDeserializer
import com.github.andreyasadchy.xtra.model.helix.follows.FollowResponse
import com.github.andreyasadchy.xtra.model.helix.game.GamesDeserializer
import com.github.andreyasadchy.xtra.model.helix.game.GamesResponse
import com.github.andreyasadchy.xtra.model.helix.stream.StreamsDeserializer
import com.github.andreyasadchy.xtra.model.helix.stream.StreamsResponse
import com.github.andreyasadchy.xtra.model.helix.user.UsersDeserializer
import com.github.andreyasadchy.xtra.model.helix.user.UsersResponse
import com.github.andreyasadchy.xtra.model.helix.video.VideosDeserializer
import com.github.andreyasadchy.xtra.model.helix.video.VideosResponse
import com.github.andreyasadchy.xtra.model.query.BadgesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.BadgesQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameBoxArtQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.GameBoxArtQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameClipsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.GameClipsQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameStreamsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.GameStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.GameVideosQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.GameVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchChannelsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.SearchChannelsQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchGamesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.SearchGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchStreamsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.SearchStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.SearchVideosQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.SearchVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.TopGamesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.TopGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.TopStreamsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.TopStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserBadgesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserBadgesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserChannelPageQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserChannelPageQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserCheerEmotesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserCheerEmotesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserClipsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserClipsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserEmotesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserEmotesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedGamesQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserFollowedGamesQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedStreamsQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserFollowedStreamsQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedUsersQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserFollowedUsersQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserFollowedVideosQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserFollowedVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserMessageClickedQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserMessageClickedQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserResultIDDeserializer
import com.github.andreyasadchy.xtra.model.query.UserResultIDQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserResultLoginDeserializer
import com.github.andreyasadchy.xtra.model.query.UserResultLoginQueryResponse
import com.github.andreyasadchy.xtra.model.query.UserVideosQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UserVideosQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersLastBroadcastQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UsersLastBroadcastQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersStreamQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UsersStreamQueryResponse
import com.github.andreyasadchy.xtra.model.query.UsersTypeQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.UsersTypeQueryResponse
import com.github.andreyasadchy.xtra.model.query.VideoQueryDeserializer
import com.github.andreyasadchy.xtra.model.query.VideoQueryResponse
import com.github.andreyasadchy.xtra.util.FetchProvider
import com.github.andreyasadchy.xtra.util.TlsSocketFactory
import com.google.gson.GsonBuilder
import com.tonyodev.fetch2.FetchConfiguration
import com.tonyodev.fetch2okhttp.OkHttpDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
class XtraModule {

    @Singleton
    @Provides
    fun providesHelixApi(client: OkHttpClient, gsonConverterFactory: GsonConverterFactory): HelixApi {
        return Retrofit.Builder()
                .baseUrl("https://api.twitch.tv/helix/")
                .client(client)
                .addConverterFactory(gsonConverterFactory)
                .build()
                .create(HelixApi::class.java)
    }

    @Singleton
    @Provides
    fun providesUsherApi(client: OkHttpClient, gsonConverterFactory: GsonConverterFactory): UsherApi {
        return Retrofit.Builder()
                .baseUrl("https://usher.ttvnw.net/")
                .client(client)
                .addConverterFactory(gsonConverterFactory)
                .build()
                .create(UsherApi::class.java)
    }

    @Singleton
    @Provides
    fun providesMiscApi(client: OkHttpClient, gsonConverterFactory: GsonConverterFactory): MiscApi {
        return Retrofit.Builder()
                .baseUrl("https://api.twitch.tv/") //placeholder url
                .client(client)
                .addConverterFactory(gsonConverterFactory)
                .build()
                .create(MiscApi::class.java)
    }

    @Singleton
    @Provides
    fun providesIdApi(client: OkHttpClient, gsonConverterFactory: GsonConverterFactory): IdApi {
        return Retrofit.Builder()
                .baseUrl("https://id.twitch.tv/oauth2/")
                .client(client)
                .addConverterFactory(gsonConverterFactory)
                .build()
                .create(IdApi::class.java)
    }

    @Singleton
    @Provides
    fun providesGraphQLApi(client: OkHttpClient, gsonConverterFactory: GsonConverterFactory): GraphQLApi {
        return Retrofit.Builder()
                .baseUrl("https://gql.twitch.tv/gql/")
                .client(client)
                .addConverterFactory(gsonConverterFactory)
                .build()
                .create(GraphQLApi::class.java)
    }

    @Singleton
    @Provides
    fun providesGsonConverterFactory(): GsonConverterFactory {
        return GsonConverterFactory.create(GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .registerTypeAdapter(RecentMessagesResponse::class.java, RecentMessagesDeserializer())
                .registerTypeAdapter(StvGlobalResponse::class.java, StvGlobalDeserializer())
                .registerTypeAdapter(StvChannelResponse::class.java, StvChannelDeserializer())
                .registerTypeAdapter(BttvGlobalResponse::class.java, BttvGlobalDeserializer())
                .registerTypeAdapter(BttvChannelResponse::class.java, BttvChannelDeserializer())
                .registerTypeAdapter(FfzGlobalResponse::class.java, FfzGlobalDeserializer())
                .registerTypeAdapter(FfzChannelResponse::class.java, FfzChannelDeserializer())

                .registerTypeAdapter(GamesResponse::class.java, GamesDeserializer())
                .registerTypeAdapter(StreamsResponse::class.java, StreamsDeserializer())
                .registerTypeAdapter(VideosResponse::class.java, VideosDeserializer())
                .registerTypeAdapter(ClipsResponse::class.java, ClipsDeserializer())
                .registerTypeAdapter(UsersResponse::class.java, UsersDeserializer())
                .registerTypeAdapter(ChannelSearchResponse::class.java, ChannelSearchDeserializer())
                .registerTypeAdapter(ChatBadgesResponse::class.java, ChatBadgesDeserializer())
                .registerTypeAdapter(CheerEmotesResponse::class.java, CheerEmotesDeserializer())
                .registerTypeAdapter(FollowResponse::class.java, FollowDeserializer())
                .registerTypeAdapter(UserEmotesResponse::class.java, UserEmotesDeserializer())
                .registerTypeAdapter(EmoteSetResponse::class.java, EmoteSetDeserializer())
                .registerTypeAdapter(ModeratorsResponse::class.java, ModeratorsDeserializer())

                .registerTypeAdapter(PlaybackAccessTokenResponse::class.java, PlaybackAccessTokenDeserializer())
                .registerTypeAdapter(ClipUrlsResponse::class.java, ClipUrlsDeserializer())
                .registerTypeAdapter(ClipDataResponse::class.java, ClipDataDeserializer())
                .registerTypeAdapter(ClipVideoResponse::class.java, ClipVideoDeserializer())
                .registerTypeAdapter(GameDataResponse::class.java, GameDataDeserializer())
                .registerTypeAdapter(StreamsDataResponse::class.java, StreamsDataDeserializer())
                .registerTypeAdapter(ViewersDataResponse::class.java, ViewersDataDeserializer())
                .registerTypeAdapter(GameStreamsDataResponse::class.java, GameStreamsDataDeserializer())
                .registerTypeAdapter(GameVideosDataResponse::class.java, GameVideosDataDeserializer())
                .registerTypeAdapter(GameClipsDataResponse::class.java, GameClipsDataDeserializer())
                .registerTypeAdapter(ChannelVideosDataResponse::class.java, ChannelVideosDataDeserializer())
                .registerTypeAdapter(ChannelClipsDataResponse::class.java, ChannelClipsDataDeserializer())
                .registerTypeAdapter(ChannelViewerListDataResponse::class.java, ChannelViewerListDataDeserializer())
                .registerTypeAdapter(EmoteCardResponse::class.java, EmoteCardDeserializer())
                .registerTypeAdapter(SearchChannelDataResponse::class.java, SearchChannelDataDeserializer())
                .registerTypeAdapter(SearchGameDataResponse::class.java, SearchGameDataDeserializer())
                .registerTypeAdapter(SearchVideosDataResponse::class.java, SearchVideosDataDeserializer())
                .registerTypeAdapter(FreeformTagDataResponse::class.java, FreeformTagDataDeserializer())
                .registerTypeAdapter(TagGameDataResponse::class.java, TagGameDataDeserializer())
                .registerTypeAdapter(ChatBadgesDataResponse::class.java, ChatBadgesDataDeserializer())
                .registerTypeAdapter(GlobalCheerEmotesDataResponse::class.java, GlobalCheerEmotesDataDeserializer())
                .registerTypeAdapter(ChannelCheerEmotesDataResponse::class.java, ChannelCheerEmotesDataDeserializer())
                .registerTypeAdapter(VideoMessagesDataResponse::class.java, VideoMessagesDataDeserializer())
                .registerTypeAdapter(VideoMessagesDownloadDataResponse::class.java, VideoMessagesDownloadDataDeserializer())
                .registerTypeAdapter(VideoGamesDataResponse::class.java, VideoGamesDataDeserializer())
                .registerTypeAdapter(FollowedStreamsDataResponse::class.java, FollowedStreamsDataDeserializer())
                .registerTypeAdapter(FollowedVideosDataResponse::class.java, FollowedVideosDataDeserializer())
                .registerTypeAdapter(FollowedChannelsDataResponse::class.java, FollowedChannelsDataDeserializer())
                .registerTypeAdapter(FollowedGamesDataResponse::class.java, FollowedGamesDataDeserializer())
                .registerTypeAdapter(FollowDataResponse::class.java, FollowDataDeserializer())
                .registerTypeAdapter(FollowingUserDataResponse::class.java, FollowingUserDataDeserializer())
                .registerTypeAdapter(FollowingGameDataResponse::class.java, FollowingGameDataDeserializer())
                .registerTypeAdapter(ChannelPointsContextDataResponse::class.java, ChannelPointsContextDataDeserializer())
                .registerTypeAdapter(UserEmotesDataResponse::class.java, UserEmotesDataDeserializer())
                .registerTypeAdapter(ModeratorsDataResponse::class.java, ModeratorsDataDeserializer())
                .registerTypeAdapter(VipsDataResponse::class.java, VipsDataDeserializer())

                .registerTypeAdapter(BadgesQueryResponse::class.java, BadgesQueryDeserializer())
                .registerTypeAdapter(GameBoxArtQueryResponse::class.java, GameBoxArtQueryDeserializer())
                .registerTypeAdapter(GameClipsQueryResponse::class.java, GameClipsQueryDeserializer())
                .registerTypeAdapter(GameStreamsQueryResponse::class.java, GameStreamsQueryDeserializer())
                .registerTypeAdapter(GameVideosQueryResponse::class.java, GameVideosQueryDeserializer())
                .registerTypeAdapter(SearchChannelsQueryResponse::class.java, SearchChannelsQueryDeserializer())
                .registerTypeAdapter(SearchGamesQueryResponse::class.java, SearchGamesQueryDeserializer())
                .registerTypeAdapter(SearchStreamsQueryResponse::class.java, SearchStreamsQueryDeserializer())
                .registerTypeAdapter(SearchVideosQueryResponse::class.java, SearchVideosQueryDeserializer())
                .registerTypeAdapter(TopGamesQueryResponse::class.java, TopGamesQueryDeserializer())
                .registerTypeAdapter(TopStreamsQueryResponse::class.java, TopStreamsQueryDeserializer())
                .registerTypeAdapter(UserBadgesQueryResponse::class.java, UserBadgesQueryDeserializer())
                .registerTypeAdapter(UserChannelPageQueryResponse::class.java, UserChannelPageQueryDeserializer())
                .registerTypeAdapter(UserCheerEmotesQueryResponse::class.java, UserCheerEmotesQueryDeserializer())
                .registerTypeAdapter(UserClipsQueryResponse::class.java, UserClipsQueryDeserializer())
                .registerTypeAdapter(UserEmotesQueryResponse::class.java, UserEmotesQueryDeserializer())
                .registerTypeAdapter(UserFollowedGamesQueryResponse::class.java, UserFollowedGamesQueryDeserializer())
                .registerTypeAdapter(UserFollowedStreamsQueryResponse::class.java, UserFollowedStreamsQueryDeserializer())
                .registerTypeAdapter(UserFollowedUsersQueryResponse::class.java, UserFollowedUsersQueryDeserializer())
                .registerTypeAdapter(UserFollowedVideosQueryResponse::class.java, UserFollowedVideosQueryDeserializer())
                .registerTypeAdapter(UserMessageClickedQueryResponse::class.java, UserMessageClickedQueryDeserializer())
                .registerTypeAdapter(UserQueryResponse::class.java, UserQueryDeserializer())
                .registerTypeAdapter(UsersLastBroadcastQueryResponse::class.java, UsersLastBroadcastQueryDeserializer())
                .registerTypeAdapter(UsersStreamQueryResponse::class.java, UsersStreamQueryDeserializer())
                .registerTypeAdapter(UsersTypeQueryResponse::class.java, UsersTypeQueryDeserializer())
                .registerTypeAdapter(UserVideosQueryResponse::class.java, UserVideosQueryDeserializer())
                .registerTypeAdapter(VideoQueryResponse::class.java, VideoQueryDeserializer())
                .registerTypeAdapter(UserResultIDQueryResponse::class.java, UserResultIDDeserializer())
                .registerTypeAdapter(UserResultLoginQueryResponse::class.java, UserResultLoginDeserializer())
                .create())
    }

    @Singleton
    @Provides
    fun providesOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
                        init(null as KeyStore?)
                        trustManagers.first { it is X509TrustManager } as X509TrustManager
                    }
                    val sslContext = SSLContext.getInstance(TlsVersion.TLS_1_2.javaName())
                    sslContext.init(null, arrayOf(trustManager), null)
                    val cipherSuites = ConnectionSpec.MODERN_TLS.cipherSuites()!!.toMutableList().apply {
                        add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA)
                    }.toTypedArray()
                    sslSocketFactory(TlsSocketFactory(sslContext.socketFactory), trustManager)
                    val cs = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .cipherSuites(*cipherSuites)
                        .build()
                    connectionSpecs(arrayListOf(cs))
                } catch (e: Exception) {
                    Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2 compatibility", e)
                }
            }
            connectTimeout(5, TimeUnit.MINUTES)
            writeTimeout(5, TimeUnit.MINUTES)
            readTimeout(5, TimeUnit.MINUTES)
        }
        return builder.build()
    }

    @Singleton
    @Provides
    fun providesFetchProvider(fetchConfigurationBuilder: FetchConfiguration.Builder): FetchProvider {
        return FetchProvider(fetchConfigurationBuilder)
    }

    @Singleton
    @Provides
    fun providesFetchConfigurationBuilder(application: Application, okHttpClient: OkHttpClient): FetchConfiguration.Builder {
        return FetchConfiguration.Builder(application)
                .enableLogging(BuildConfig.DEBUG)
                .enableRetryOnNetworkGain(true)
                .setDownloadConcurrentLimit(3)
                .setHttpDownloader(OkHttpDownloader(okHttpClient))
                .setProgressReportingInterval(1000L)
                .setAutoRetryMaxAttempts(3)
    }
}
