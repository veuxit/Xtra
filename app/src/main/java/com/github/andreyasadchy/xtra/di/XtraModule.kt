package com.github.andreyasadchy.xtra.di

import android.app.Application
import android.os.Build
import android.util.Log
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.api.*
import com.github.andreyasadchy.xtra.model.chat.*
import com.github.andreyasadchy.xtra.model.gql.channel.*
import com.github.andreyasadchy.xtra.model.gql.chat.*
import com.github.andreyasadchy.xtra.model.gql.clip.*
import com.github.andreyasadchy.xtra.model.gql.followed.*
import com.github.andreyasadchy.xtra.model.gql.game.*
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenDeserializer
import com.github.andreyasadchy.xtra.model.gql.playlist.PlaybackAccessTokenResponse
import com.github.andreyasadchy.xtra.model.gql.search.*
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsDataResponse
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataDeserializer
import com.github.andreyasadchy.xtra.model.gql.stream.ViewersDataResponse
import com.github.andreyasadchy.xtra.model.gql.tag.*
import com.github.andreyasadchy.xtra.model.gql.video.*
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchDeserializer
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearchResponse
import com.github.andreyasadchy.xtra.model.helix.chat.*
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
import com.github.andreyasadchy.xtra.model.query.*
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
    fun providesTTVLolApi(client: OkHttpClient, gsonConverterFactory: GsonConverterFactory): TTVLolApi {
        return Retrofit.Builder()
                .baseUrl("https://api.ttv.lol/")
                .client(client.newBuilder().addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                            .addHeader("X-Donate-To", "https://ttv.lol/donate")
                            .build()
                    chain.proceed(request)
                }.build())
                .addConverterFactory(gsonConverterFactory)
                .build()
                .create(TTVLolApi::class.java)
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
