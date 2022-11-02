package com.github.andreyasadchy.xtra.repository

import android.util.Base64
import androidx.core.util.Pair
import androidx.paging.PagedList
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.gql.chat.ChannelPointsContextDataResponse
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDataResponse
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearch
import com.github.andreyasadchy.xtra.model.helix.channel.ChannelViewerList
import com.github.andreyasadchy.xtra.model.helix.clip.Clip
import com.github.andreyasadchy.xtra.model.helix.follows.Follow
import com.github.andreyasadchy.xtra.model.helix.follows.Order
import com.github.andreyasadchy.xtra.model.helix.game.Game
import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.github.andreyasadchy.xtra.model.helix.tag.Tag
import com.github.andreyasadchy.xtra.model.helix.user.User
import com.github.andreyasadchy.xtra.model.helix.video.BroadcastType
import com.github.andreyasadchy.xtra.model.helix.video.Period
import com.github.andreyasadchy.xtra.model.helix.video.Sort
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.datasource.*
import com.github.andreyasadchy.xtra.type.*
import com.github.andreyasadchy.xtra.ui.view.chat.animateGifs
import com.github.andreyasadchy.xtra.ui.view.chat.emoteQuality
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiRepository @Inject constructor(
    private val helix: HelixApi,
    private val gql: GraphQLRepository,
    private val misc: MiscApi,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val localFollowsGame: LocalFollowGameRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository) {

    fun loadTopGames(helixClientId: String?, helixToken: String?, gqlClientId: String?, tags: List<String>?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Game> {
        val factory = GamesDataSource.Factory(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, tags, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
                .setPageSize(30)
                .setInitialLoadSizeHint(30)
                .setPrefetchDistance(10)
                .setEnablePlaceholders(false)
                .build()
        return Listing.create(factory, config)
    }

    fun loadTopStreams(helixClientId: String?, helixToken: String?, gqlClientId: String?, tags: List<String>?, apiPref: ArrayList<Pair<Long?, String?>?>, thumbnailsEnabled: Boolean, coroutineScope: CoroutineScope): Listing<Stream> {
        val factory = StreamsDataSource.Factory(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, tags, gql, apiPref, coroutineScope)
        val builder = PagedList.Config.Builder().setEnablePlaceholders(false)
        if (thumbnailsEnabled) {
            builder.setPageSize(30)
                .setInitialLoadSizeHint(30)
                .setPrefetchDistance(3)
        } else {
            builder.setPageSize(30)
                .setInitialLoadSizeHint(30)
                .setPrefetchDistance(10)
        }
        val config = builder.build()
        return Listing.create(factory, config)
    }

    fun loadGameStreams(gameId: String?, gameName: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlQuerySort: StreamSort?, gqlSort: com.github.andreyasadchy.xtra.model.helix.stream.Sort?, tags: List<String>?, apiPref: ArrayList<Pair<Long?, String?>?>, thumbnailsEnabled: Boolean, coroutineScope: CoroutineScope): Listing<Stream> {
        val factory = GameStreamsDataSource.Factory(gameId, gameName, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, gqlQuerySort, gqlSort, tags, gql, apiPref, coroutineScope)
        val builder = PagedList.Config.Builder().setEnablePlaceholders(false)
        if (thumbnailsEnabled) {
            builder.setPageSize(30)
                .setInitialLoadSizeHint(30)
                .setPrefetchDistance(3)
        } else {
            builder.setPageSize(30)
                .setInitialLoadSizeHint(30)
                .setPrefetchDistance(10)
        }
        val config = builder.build()
        return Listing.create(factory, config)
    }

    fun loadGameVideos(gameId: String?, gameName: String?, helixClientId: String?, helixToken: String?, helixPeriod: Period, helixBroadcastTypes: BroadcastType, helixLanguage: String?, helixSort: Sort, gqlClientId: String?, gqlQueryLanguages: List<String>?, gqlQueryType: com.github.andreyasadchy.xtra.type.BroadcastType?, gqlQuerySort: VideoSort?, gqlType: String?, gqlSort: String?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Video> {
        val factory = GameVideosDataSource.Factory(gameId, gameName, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helixPeriod, helixBroadcastTypes, helixLanguage, helixSort, helix, gqlClientId, gqlQueryLanguages, gqlQueryType, gqlQuerySort, gqlType, gqlSort, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(30)
            .setInitialLoadSizeHint(30)
            .setPrefetchDistance(3)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadGameClips(gameId: String?, gameName: String?, helixClientId: String?, helixToken: String?, started_at: String?, ended_at: String?, gqlClientId: String?, gqlQueryLanguages: List<Language>?, gqlQueryPeriod: ClipsPeriod?, gqlPeriod: String?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Clip> {
        val factory = GameClipsDataSource.Factory(gameId, gameName, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, started_at, ended_at, helix, gqlClientId, gqlQueryLanguages, gqlQueryPeriod, gqlPeriod, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(20)
            .setInitialLoadSizeHint(20)
            .setPrefetchDistance(3)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadChannelVideos(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, helixPeriod: Period, helixBroadcastTypes: BroadcastType, helixSort: Sort, gqlClientId: String?, gqlQueryType: com.github.andreyasadchy.xtra.type.BroadcastType?, gqlQuerySort: VideoSort?, gqlType: String?, gqlSort: String?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Video> {
        val factory = ChannelVideosDataSource.Factory(channelId, channelLogin, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helixPeriod, helixBroadcastTypes, helixSort, helix, gqlClientId, gqlQueryType, gqlQuerySort, gqlType, gqlSort, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(30)
            .setInitialLoadSizeHint(30)
            .setPrefetchDistance(3)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadChannelClips(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, started_at: String?, ended_at: String?, gqlClientId: String?, gqlQueryPeriod: ClipsPeriod?, gqlPeriod: String?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Clip> {
        val factory = ChannelClipsDataSource.Factory(channelId, channelLogin, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, started_at, ended_at, helix, gqlClientId, gqlQueryPeriod, gqlPeriod, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(20)
            .setInitialLoadSizeHint(20)
            .setPrefetchDistance(3)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadSearchChannels(query: String, helixClientId: String?, helixToken: String?, gqlClientId: String?, apiPref: ArrayList<Pair<Long?, String?>?>?, coroutineScope: CoroutineScope): Listing<ChannelSearch> {
        val factory = SearchChannelsDataSource.Factory(query, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(15)
            .setInitialLoadSizeHint(15)
            .setPrefetchDistance(5)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadSearchGames(query: String, helixClientId: String?, helixToken: String?, gqlClientId: String?, apiPref: ArrayList<Pair<Long?, String?>?>?, coroutineScope: CoroutineScope): Listing<Game> {
        val factory = SearchGamesDataSource.Factory(query, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(15)
            .setInitialLoadSizeHint(15)
            .setPrefetchDistance(5)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadSearchVideos(query: String, gqlClientId: String?, apiPref: ArrayList<Pair<Long?, String?>?>?, coroutineScope: CoroutineScope): Listing<Video> {
        val factory = SearchVideosDataSource.Factory(query, gqlClientId, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(10)
            .setInitialLoadSizeHint(15)
            .setPrefetchDistance(3)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadSearchStreams(query: String, helixClientId: String?, helixToken: String?, gqlClientId: String?, apiPref: ArrayList<Pair<Long?, String?>?>?, thumbnailsEnabled: Boolean?, coroutineScope: CoroutineScope): Listing<Stream> {
        val factory = SearchStreamsDataSource.Factory(query, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, gql, apiPref, coroutineScope)
        val builder = PagedList.Config.Builder().setEnablePlaceholders(false)
        if (thumbnailsEnabled == true) {
            builder.setPageSize(10)
                .setInitialLoadSizeHint(15)
                .setPrefetchDistance(3)
        } else {
            builder.setPageSize(30)
                .setInitialLoadSizeHint(30)
                .setPrefetchDistance(10)
        }
        val config = builder.build()
        return Listing.create(factory, config)
    }

    fun loadFollowedStreams(userId: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, apiPref: ArrayList<Pair<Long?, String?>?>, thumbnailsEnabled: Boolean, coroutineScope: CoroutineScope): Listing<Stream> {
        val factory = FollowedStreamsDataSource.Factory(localFollowsChannel, userId, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, gql, apiPref, coroutineScope)
        val builder = PagedList.Config.Builder().setEnablePlaceholders(false)
        if (thumbnailsEnabled) {
            builder.setPageSize(50)
                .setInitialLoadSizeHint(50)
                .setPrefetchDistance(3)
        } else {
            builder.setPageSize(50)
                .setInitialLoadSizeHint(50)
                .setPrefetchDistance(10)
        }
        val config = builder.build()
        return Listing.create(factory, config)
    }

    fun loadFollowedVideos(userId: String?, gqlClientId: String?, gqlToken: String?, gqlQueryType: com.github.andreyasadchy.xtra.type.BroadcastType?, gqlQuerySort: VideoSort?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Video> {
        val factory = FollowedVideosDataSource.Factory(userId, gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, gqlQueryType, gqlQuerySort, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(50)
            .setInitialLoadSizeHint(50)
            .setPrefetchDistance(3)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadFollowedChannels(userId: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, apiPref: ArrayList<Pair<Long?, String?>?>, sort: com.github.andreyasadchy.xtra.model.helix.follows.Sort, order: Order, coroutineScope: CoroutineScope): Listing<Follow> {
        val factory = FollowedChannelsDataSource.Factory(localFollowsChannel, offlineRepository, bookmarksRepository, userId, helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, helix, gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, gql, apiPref, sort, order, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(40)
            .setInitialLoadSizeHint(40)
            .setPrefetchDistance(10)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    fun loadFollowedGames(userId: String?, gqlClientId: String?, gqlToken: String?, apiPref: ArrayList<Pair<Long?, String?>?>, coroutineScope: CoroutineScope): Listing<Game> {
        val factory = FollowedGamesDataSource.Factory(localFollowsGame, userId, gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, gql, apiPref, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(100)
            .setInitialLoadSizeHint(100)
            .setPrefetchDistance(10)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }

    suspend fun loadGameBoxArt(gameId: String, helixClientId: String?, helixToken: String?, gqlClientId: String?): String? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryGameBoxArt(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.gameboxart).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", gameId)
                }).boxArtURL
        } catch (e: Exception) {
            helix.getGames(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = listOf(gameId)
            ).data?.firstOrNull()?.boxArt
        }
    }

    suspend fun loadStream(channelId: String, channelLogin: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUsersStream(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.usersstream).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    val idArray = JsonArray()
                    idArray.add(channelId)
                    add("id", idArray)
                }).data.firstOrNull()
        } catch (e: Exception) {
            try {
                helix.getStreams(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    ids = listOf(channelId)
                ).data?.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun loadVideo(videoId: String, helixClientId: String?, helixToken: String?, gqlClientId: String?): Video? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryVideo(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.video).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", videoId)
                }).data
        } catch (e: Exception) {
            helix.getVideos(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = listOf(videoId)
            ).data?.firstOrNull()
        }
    }

    suspend fun loadVideos(ids: List<String>, helixClientId: String?, helixToken: String?): List<Video>? = withContext(Dispatchers.IO) {
        helix.getVideos(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            ids = ids
        ).data
    }

    suspend fun loadClip(clipId: String, helixClientId: String?, helixToken: String?, gqlClientId: String?): Clip? = withContext(Dispatchers.IO) {
        try {
            var user: Clip? = null
            try {
                user = gql.loadClipData(gqlClientId, clipId).data
            } catch (e: Exception) {}
            val video = gql.loadClipVideo(gqlClientId, clipId).data
            Clip(id = clipId, broadcaster_id = user?.broadcaster_id, broadcaster_login = user?.broadcaster_login, broadcaster_name = user?.broadcaster_name,
                profileImageURL = user?.profileImageURL, video_id = video?.video_id, duration = video?.duration, videoOffsetSeconds = video?.videoOffsetSeconds ?: user?.videoOffsetSeconds)
        } catch (e: Exception) {
            helix.getClips(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = listOf(clipId)
            ).data?.firstOrNull()
        }
    }

    suspend fun loadUserChannelPage(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserChannelPage(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.userchannelpage).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                    addProperty("login", channelLogin)
                }).data
        } catch (e: Exception) {
            helix.getStreams(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(channelId) },
                logins = channelLogin?.let { listOf(channelLogin) }
            ).data?.firstOrNull()
        }
    }

    suspend fun loadUser(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?): User? = withContext(Dispatchers.IO) {
        helix.getUsers(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            ids = channelId?.let { listOf(channelId) },
            logins = channelLogin?.let { listOf(channelLogin) }
        ).data?.firstOrNull()
    }

    suspend fun loadCheckUser(channelId: String? = null, channelLogin: String? = null, helixClientId: String?, helixToken: String?, gqlClientId: String?): User? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUser(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.user).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                    addProperty("login", channelLogin)
                }).data
        } catch (e: Exception) {
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(channelId) },
                logins = channelLogin?.let { listOf(channelLogin) }
            ).data?.firstOrNull()
        }
    }

    suspend fun loadUserMessageClicked(channelId: String? = null, channelLogin: String? = null, targetId: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): User? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserMessageClicked(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.usermessageclicked).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                    addProperty("login", channelLogin)
                    addProperty("targetId", targetId)
                }).data
        } catch (e: Exception) {
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(channelId) },
                logins = channelLogin?.let { listOf(channelLogin) }
            ).data?.firstOrNull()
        }
    }

    suspend fun loadUserTypes(ids: List<String>, helixClientId: String?, helixToken: String?, gqlClientId: String?): List<User>? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUsersType(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.userstype).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    val idArray = JsonArray()
                    ids.forEach {
                        idArray.add(it)
                    }
                    add("id", idArray)
                }).data
        } catch (e: Exception) {
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = ids
            ).data
        }
    }

    suspend fun loadGlobalBadges(helixClientId: String?, helixToken: String?, gqlClientId: String?): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryBadges(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.badges).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("quality", (when (emoteQuality) {"4" -> BadgeImageSize.QUADRUPLE "3" -> BadgeImageSize.QUADRUPLE "2" -> BadgeImageSize.DOUBLE else -> BadgeImageSize.NORMAL}).toString())
                }).data
        } catch (e: Exception) {
            try {
                gql.loadChatBadges(gqlClientId, "").global
            } catch (e: Exception) {
                helix.getGlobalBadges(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }
                ).data
            }
        }
    }

    suspend fun loadChannelBadges(helixClientId: String?, helixToken: String?, gqlClientId: String?, channelId: String?, channelLogin: String?): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserBadges(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.userbadges).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                    addProperty("quality", (when (emoteQuality) {"4" -> BadgeImageSize.QUADRUPLE "3" -> BadgeImageSize.QUADRUPLE "2" -> BadgeImageSize.DOUBLE else -> BadgeImageSize.NORMAL}).toString())
                }).data
        } catch (e: Exception) {
            try {
                gql.loadChatBadges(gqlClientId, channelLogin).channel
            } catch (e: Exception) {
                helix.getChannelBadges(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    userId = channelId
                ).data
            }
        }
    }

    suspend fun loadCheerEmotes(helixClientId: String?, helixToken: String?, gqlClientId: String?, channelId: String?, channelLogin: String?): List<CheerEmote> = withContext(Dispatchers.IO) {
        try {
            val emotes = mutableListOf<CheerEmote>()
            val context = XtraApp.INSTANCE.applicationContext
            val get = gql.loadQueryUserCheerEmotes(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.usercheeremotes).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                })
            get.config.let { config ->
                config.backgrounds.let {
                    val background = (config.backgrounds.find { it.asString == "dark" } ?: config.backgrounds.last()).asString
                    config.colors.let {
                        val colors = config.colors
                        config.scales.let {
                            val scale = (config.scales.find { it.asString == emoteQuality } ?: config.scales.last()).asString
                            config.types.let {
                                val type = if (animateGifs) {
                                    config.types.find { it.animation == "animated" } ?: config.types.find { it.animation == "static" }
                                } else {
                                    config.types.find { it.animation == "static" }
                                } ?: config.types.first()
                                if (type.animation != null && type.extension != null) {
                                    get.tiers.forEach { tier ->
                                        val item = colors.find { it.bits == tier.tierBits }
                                        if (item?.bits != null) {
                                            val url = tier.template
                                                .replaceFirst("PREFIX", tier.prefix.lowercase())
                                                .replaceFirst("BACKGROUND", background)
                                                .replaceFirst("ANIMATION", type.animation)
                                                .replaceFirst("TIER", item.bits.toString())
                                                .replaceFirst("SCALE", scale)
                                                .replaceFirst("EXTENSION", type.extension)
                                            emotes.add(CheerEmote(
                                                name = tier.prefix,
                                                url = url,
                                                type = if (type.animation == "animated") "image/gif" else "image/png",
                                                minBits = item.bits,
                                                color = item.color
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            emotes
        } catch (e: Exception) {
            try {
                gql.loadCheerEmotes(gqlClientId, channelLogin)
            } catch (e: Exception) {
                helix.getCheerEmotes(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    userId = channelId
                ).emotes
            }
        }
    }

    suspend fun loadUserEmotes(gqlClientId: String?, gqlToken: String?, userId: String, channelId: String?): List<TwitchEmote>? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserEmotes(
                clientId = gqlClientId,
                token = gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) },
                query = context.resources.openRawResource(R.raw.useremotes).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                }).data
        } catch (e: Exception) {
            try {
                gql.loadUserEmotes(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, channelId).data
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun loadEmotesFromSet(helixClientId: String?, helixToken: String?, setIds: List<String>): List<TwitchEmote>? = withContext(Dispatchers.IO) {
        helix.getEmotesFromSet(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            setIds = setIds
        ).data
    }

    suspend fun loadUserFollowing(helixClientId: String?, helixToken: String?, targetId: String?, userId: String?, gqlClientId: String?, gqlToken: String?, targetLogin: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!gqlToken.isNullOrBlank()) gql.loadFollowingUser(gqlClientId, gqlToken.let { TwitchApiHelper.addTokenPrefixGQL(it) }, targetLogin).following else throw Exception()
        } catch (e: Exception) {
            helix.getUserFollows(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                targetId = targetId,
                userId = userId
            ).total == 1
        }
    }

    suspend fun loadGameFollowing(gqlClientId: String?, gqlToken: String?, gameName: String?): Boolean = withContext(Dispatchers.IO) {
        gql.loadFollowingGame(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, gameName).following
    }

    suspend fun loadVideoMessages(gqlClientId: String?, videoId: String, offset: Int? = null, cursor: String? = null): VideoMessagesDataResponse = withContext(Dispatchers.IO) {
        gql.loadVideoMessages(gqlClientId, videoId, offset, cursor)
    }

    suspend fun loadVideoGames(clientId: String?, videoId: String?): List<Game> = withContext(Dispatchers.IO) {
        gql.loadVideoGames(clientId, videoId).data
    }

    suspend fun loadChannelViewerList(clientId: String?, channelLogin: String?): ChannelViewerList = withContext(Dispatchers.IO) {
        gql.loadChannelViewerList(clientId, channelLogin).data
    }

    suspend fun loadHosting(gqlClientId: String?, channelId: String?, channelLogin: String?): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserHosting(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.userhosting).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                    addProperty("login", channelLogin)
                }).data
        } catch (e: Exception) {
            if (!channelLogin.isNullOrBlank()) {
                gql.loadChannelHosting(gqlClientId, channelLogin).data
            } else {
                null
            }
        }
    }

    suspend fun loadChannelPointsContext(gqlClientId: String?, gqlToken: String?, channelLogin: String?): ChannelPointsContextDataResponse = withContext(Dispatchers.IO){
        gql.loadChannelPointsContext(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, channelLogin)
    }

    suspend fun loadClaimPoints(gqlClientId: String?, gqlToken: String?, channelId: String?, claimID: String?) = withContext(Dispatchers.IO) {
        gql.loadClaimPoints(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, null, null, channelId, claimID)
    }

    suspend fun loadJoinRaid(gqlClientId: String?, gqlToken: String?, raidId: String?) = withContext(Dispatchers.IO) {
        gql.loadJoinRaid(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, null, null, raidId)
    }

    suspend fun loadMinuteWatched(userId: String?, streamId: String?, channelId: String?, channelLogin: String?) = withContext(Dispatchers.IO) {
        try {
            val pageResponse = channelLogin?.let { misc.getChannelPage(it).string() }
            if (!pageResponse.isNullOrBlank()) {
                val settingsRegex = Regex("(https://static.twitchcdn.net/config/settings.*?js)")
                val settingsUrl = settingsRegex.find(pageResponse)?.value
                val settingsResponse = settingsUrl?.let { misc.getUrl(it).string() }
                if (!settingsResponse.isNullOrBlank()) {
                    val spadeRegex = Regex("\"spade_url\":\"(.*?)\"")
                    val spadeUrl = spadeRegex.find(settingsResponse)?.groups?.get(1)?.value
                    if (!spadeUrl.isNullOrBlank()) {
                        val json = JsonObject().apply {
                            addProperty("event", "minute-watched")
                            add("properties", JsonObject().apply {
                                addProperty("channel_id", channelId)
                                addProperty("broadcast_id", streamId)
                                addProperty("player", "site")
                                addProperty("user_id", userId?.toInt())
                            })
                        }
                        val spadeRequest = Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
                        val request = RequestBody.create(MediaType.get("application/x-www-form-urlencoded; charset=utf-8"), spadeRequest)
                        misc.postUrl(spadeUrl, request)
                    }
                }
            }
        } catch (e: Exception) {

        }
    }

    private suspend fun loadClientIntegrityToken(gqlClientId: String?, gqlToken: String?, deviceId: String?): String? = withContext(Dispatchers.IO) {
        val response = misc.getClientIntegrityToken(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, deviceId).string()
        val tokenRegex = Regex("\"token\":\"(.*?)\"")
        tokenRegex.find(response)?.groups?.get(1)?.value
    }

    suspend fun followUser(gqlClientId: String?, gqlToken: String?, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowUser(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, null, null, userId).error
    }

    suspend fun unfollowUser(gqlClientId: String?, gqlToken: String?, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowUser(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, null, null, userId).error
    }

    suspend fun followGame(gqlClientId: String?, gqlToken: String?, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowGame(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, null, null, gameId).error
    }

    suspend fun unfollowGame(gqlClientId: String?, gqlToken: String?, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowGame(gqlClientId, gqlToken?.let { TwitchApiHelper.addTokenPrefixGQL(it) }, null, null, gameId).error
    }

    fun loadTagsGQL(clientId: String?, getGameTags: Boolean, gameId: String?, gameName: String?, query: String?, coroutineScope: CoroutineScope): Listing<Tag> {
        val factory = TagsDataSourceGQL.Factory(clientId, getGameTags, gameId, gameName, query, gql, coroutineScope)
        val config = PagedList.Config.Builder()
            .setPageSize(10000)
            .setInitialLoadSizeHint(10000)
            .setPrefetchDistance(5)
            .setEnablePlaceholders(false)
            .build()
        return Listing.create(factory, config)
    }
}