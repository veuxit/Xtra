package com.github.andreyasadchy.xtra.repository

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.json.buildJsonString
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.json.writeObject
import com.apollographql.apollo.api.parseResponse
import com.github.andreyasadchy.xtra.BadgesQuery
import com.github.andreyasadchy.xtra.GameBoxArtQuery
import com.github.andreyasadchy.xtra.GameClipsQuery
import com.github.andreyasadchy.xtra.GameStreamsQuery
import com.github.andreyasadchy.xtra.GameVideosQuery
import com.github.andreyasadchy.xtra.SearchChannelsQuery
import com.github.andreyasadchy.xtra.SearchGamesQuery
import com.github.andreyasadchy.xtra.SearchStreamsQuery
import com.github.andreyasadchy.xtra.SearchVideosQuery
import com.github.andreyasadchy.xtra.TopGamesQuery
import com.github.andreyasadchy.xtra.TopStreamsQuery
import com.github.andreyasadchy.xtra.UserBadgesQuery
import com.github.andreyasadchy.xtra.UserChannelPageQuery
import com.github.andreyasadchy.xtra.UserCheerEmotesQuery
import com.github.andreyasadchy.xtra.UserClipsQuery
import com.github.andreyasadchy.xtra.UserEmotesQuery
import com.github.andreyasadchy.xtra.UserFollowedGamesQuery
import com.github.andreyasadchy.xtra.UserFollowedStreamsQuery
import com.github.andreyasadchy.xtra.UserFollowedUsersQuery
import com.github.andreyasadchy.xtra.UserFollowedVideosQuery
import com.github.andreyasadchy.xtra.UserMessageClickedQuery
import com.github.andreyasadchy.xtra.UserQuery
import com.github.andreyasadchy.xtra.UserResultIDQuery
import com.github.andreyasadchy.xtra.UserResultLoginQuery
import com.github.andreyasadchy.xtra.UserVideosQuery
import com.github.andreyasadchy.xtra.UsersLastBroadcastQuery
import com.github.andreyasadchy.xtra.UsersStreamQuery
import com.github.andreyasadchy.xtra.UsersTypeQuery
import com.github.andreyasadchy.xtra.VideoQuery
import com.github.andreyasadchy.xtra.model.gql.ErrorResponse
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
import com.github.andreyasadchy.xtra.type.BadgeImageSize
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.ClipsPeriod
import com.github.andreyasadchy.xtra.type.Language
import com.github.andreyasadchy.xtra.type.StreamSort
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.util.body
import com.github.andreyasadchy.xtra.util.toHeaders
import com.github.andreyasadchy.xtra.util.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UploadDataProviders
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GraphQLRepository @Inject constructor(
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    suspend fun loadQueryBadges(useCronet: Boolean, headers: Map<String, String>, quality: BadgeImageSize): ApolloResponse<BadgesQuery.Data> = withContext(Dispatchers.IO) {
        val query = BadgesQuery(Optional.Present(quality))
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryGameBoxArt(useCronet: Boolean, headers: Map<String, String>, id: String? = null, name: String? = null): ApolloResponse<GameBoxArtQuery.Data> = withContext(Dispatchers.IO) {
        val query = GameBoxArtQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            name = if (!name.isNullOrBlank()) Optional.Present(name) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryGameClips(useCronet: Boolean, headers: Map<String, String>, id: String?, slug: String?, name: String?, languages: List<Language>?, sort: ClipsPeriod?, first: Int?, after: String?): ApolloResponse<GameClipsQuery.Data> = withContext(Dispatchers.IO) {
        val query = GameClipsQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            slug = if (!slug.isNullOrBlank()) Optional.Present(slug) else Optional.Absent,
            name = if (!name.isNullOrBlank()) Optional.Present(name) else Optional.Absent,
            languages = Optional.Present(languages),
            sort = Optional.Present(sort),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryGameStreams(useCronet: Boolean, headers: Map<String, String>, id: String?, slug: String?, name: String?, sort: StreamSort?, tags: List<String>?, first: Int?, after: String?): ApolloResponse<GameStreamsQuery.Data> = withContext(Dispatchers.IO) {
        val query = GameStreamsQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            slug = if (!slug.isNullOrBlank()) Optional.Present(slug) else Optional.Absent,
            name = if (!name.isNullOrBlank()) Optional.Present(name) else Optional.Absent,
            sort = Optional.Present(sort),
            tags = Optional.Present(tags),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryGameVideos(useCronet: Boolean, headers: Map<String, String>, id: String?, slug: String?, name: String?, languages: List<String>?, sort: VideoSort?, type: List<BroadcastType>?, first: Int?, after: String?): ApolloResponse<GameVideosQuery.Data> = withContext(Dispatchers.IO) {
        val query = GameVideosQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            slug = if (!slug.isNullOrBlank()) Optional.Present(slug) else Optional.Absent,
            name = if (!name.isNullOrBlank()) Optional.Present(name) else Optional.Absent,
            languages = Optional.Present(languages),
            sort = Optional.Present(sort),
            type = Optional.Present(type),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQuerySearchChannels(useCronet: Boolean, headers: Map<String, String>, query: String, first: Int?, after: String?): ApolloResponse<SearchChannelsQuery.Data> = withContext(Dispatchers.IO) {
        val query = SearchChannelsQuery(
            query = query,
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQuerySearchGames(useCronet: Boolean, headers: Map<String, String>, query: String, first: Int?, after: String?): ApolloResponse<SearchGamesQuery.Data> = withContext(Dispatchers.IO) {
        val query = SearchGamesQuery(
            query = query,
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQuerySearchStreams(useCronet: Boolean, headers: Map<String, String>, query: String, first: Int?, after: String?): ApolloResponse<SearchStreamsQuery.Data> = withContext(Dispatchers.IO) {
        val query = SearchStreamsQuery(
            query = query,
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQuerySearchVideos(useCronet: Boolean, headers: Map<String, String>, query: String, first: Int?, after: String?): ApolloResponse<SearchVideosQuery.Data> = withContext(Dispatchers.IO) {
        val query = SearchVideosQuery(
            query = query,
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryTopGames(useCronet: Boolean, headers: Map<String, String>, tags: List<String>?, first: Int?, after: String?): ApolloResponse<TopGamesQuery.Data> = withContext(Dispatchers.IO) {
        val query = TopGamesQuery(
            tags = Optional.Present(tags),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryTopStreams(useCronet: Boolean, headers: Map<String, String>, tags: List<String>?, first: Int?, after: String?): ApolloResponse<TopStreamsQuery.Data> = withContext(Dispatchers.IO) {
        val query = TopStreamsQuery(
            tags = Optional.Present(tags),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUser(useCronet: Boolean, headers: Map<String, String>, id: String? = null, login: String? = null): ApolloResponse<UserQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserBadges(useCronet: Boolean, headers: Map<String, String>, id: String? = null, login: String? = null, quality: BadgeImageSize?): ApolloResponse<UserBadgesQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserBadgesQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
            quality = Optional.Present(quality),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserChannelPage(useCronet: Boolean, headers: Map<String, String>, id: String? = null, login: String? = null): ApolloResponse<UserChannelPageQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserChannelPageQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserCheerEmotes(useCronet: Boolean, headers: Map<String, String>, id: String? = null, login: String? = null): ApolloResponse<UserCheerEmotesQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserCheerEmotesQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserClips(useCronet: Boolean, headers: Map<String, String>, id: String?, login: String?, sort: ClipsPeriod?, first: Int?, after: String?): ApolloResponse<UserClipsQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserClipsQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
            sort = Optional.Present(sort),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserEmotes(useCronet: Boolean, headers: Map<String, String>): ApolloResponse<UserEmotesQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserEmotesQuery()
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserFollowedGames(useCronet: Boolean, headers: Map<String, String>, first: Int?): ApolloResponse<UserFollowedGamesQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserFollowedGamesQuery(
            first = Optional.Present(first),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserFollowedStreams(useCronet: Boolean, headers: Map<String, String>, first: Int?, after: String?): ApolloResponse<UserFollowedStreamsQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserFollowedStreamsQuery(
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserFollowedUsers(useCronet: Boolean, headers: Map<String, String>, first: Int?, after: String?): ApolloResponse<UserFollowedUsersQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserFollowedUsersQuery(
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserFollowedVideos(useCronet: Boolean, headers: Map<String, String>, sort: VideoSort?, type: List<BroadcastType>?, first: Int?, after: String?): ApolloResponse<UserFollowedVideosQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserFollowedVideosQuery(
            sort = Optional.Present(sort),
            type = Optional.Present(type),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserMessageClicked(useCronet: Boolean, headers: Map<String, String>, id: String? = null, login: String? = null, targetId: String?): ApolloResponse<UserMessageClickedQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserMessageClickedQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
            targetId = Optional.Present(targetId),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserResultID(useCronet: Boolean, headers: Map<String, String>, id: String): ApolloResponse<UserResultIDQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserResultIDQuery(id)
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserResultLogin(useCronet: Boolean, headers: Map<String, String>, login: String): ApolloResponse<UserResultLoginQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserResultLoginQuery(login)
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUserVideos(useCronet: Boolean, headers: Map<String, String>, id: String?, login: String?, sort: VideoSort?, types: List<BroadcastType>?, first: Int?, after: String?): ApolloResponse<UserVideosQuery.Data> = withContext(Dispatchers.IO) {
        val query = UserVideosQuery(
            id = if (!id.isNullOrBlank()) Optional.Present(id) else Optional.Absent,
            login = if (!login.isNullOrBlank()) Optional.Present(login) else Optional.Absent,
            sort = Optional.Present(sort),
            types = Optional.Present(types),
            first = Optional.Present(first),
            after = Optional.Present(after),
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUsersLastBroadcast(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null): ApolloResponse<UsersLastBroadcastQuery.Data> = withContext(Dispatchers.IO) {
        val query = UsersLastBroadcastQuery(
            ids = if (!ids.isNullOrEmpty()) Optional.Present(ids) else Optional.Absent,
            logins = if (ids.isNullOrEmpty() && !logins.isNullOrEmpty()) Optional.Present(logins) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUsersStream(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null): ApolloResponse<UsersStreamQuery.Data> = withContext(Dispatchers.IO) {
        val query = UsersStreamQuery(
            ids = if (!ids.isNullOrEmpty()) Optional.Present(ids) else Optional.Absent,
            logins = if (!logins.isNullOrEmpty()) Optional.Present(logins) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryUsersType(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null): ApolloResponse<UsersTypeQuery.Data> = withContext(Dispatchers.IO) {
        val query = UsersTypeQuery(
            ids = if (!ids.isNullOrEmpty()) Optional.Present(ids) else Optional.Absent,
            logins = if (!logins.isNullOrEmpty()) Optional.Present(logins) else Optional.Absent,
        )
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    suspend fun loadQueryVideo(useCronet: Boolean, headers: Map<String, String>, id: String?): ApolloResponse<VideoQuery.Data> = withContext(Dispatchers.IO) {
        val query = VideoQuery(Optional.Present(id))
        val body = buildJsonString {
            query.apply {
                writeObject {
                    name("variables")
                    writeObject {
                        serializeVariables(this, CustomScalarAdapters.Empty, false)
                    }
                    name("query")
                    value(document().replaceFirst(name(), "null"))
                }
            }
        }
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as ByteArray
            response.inputStream().source().buffer().jsonReader().use {
                query.parseResponse(it)
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                response.body.byteStream().source().buffer().jsonReader().use {
                    query.parseResponse(it)
                }
            }
        }
    }

    fun getPlaybackAccessTokenRequestBody(login: String?, vodId: String?, playerType: String?): String {
        return buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9")
                    put("version", 1)
                }
            }
            put("operationName", "PlaybackAccessToken")
            putJsonObject("variables") {
                put("isLive", !login.isNullOrBlank())
                put("login", login ?: "")
                put("isVod", !vodId.isNullOrBlank())
                put("vodID", vodId ?: "")
                put("platform", "web")
                put("playerType", playerType)
            }
        }.toString()
    }

    suspend fun loadPlaybackAccessToken(useCronet: Boolean, headers: Map<String, String>, login: String? = null, vodId: String? = null, playerType: String?): PlaybackAccessTokenResponse = withContext(Dispatchers.IO) {
        val body = getPlaybackAccessTokenRequestBody(login, vodId, playerType)
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<PlaybackAccessTokenResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<PlaybackAccessTokenResponse>(response.body.string())
            }
        }
    }

    suspend fun loadClipUrls(useCronet: Boolean, headers: Map<String, String>, slug: String?): ClipUrlsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6fd3af2b22989506269b9ac02dd87eb4a6688392d67d94e41a6886f1e9f5c00f")
                    put("version", 1)
                }
            }
            put("operationName", "VideoAccessToken_Clip")
            putJsonObject("variables") {
                put("slug", slug)
                put("platform", "web")
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ClipUrlsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ClipUrlsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadClipData(useCronet: Boolean, headers: Map<String, String>, slug: String?): ClipDataResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "a33067cdf92191dccfb53aa86f878a2c271e6a3587a6731dc8275e5751dd133f")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelClipCore")
            putJsonObject("variables") {
                put("clipSlug", slug)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ClipDataResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ClipDataResponse>(response.body.string())
            }
        }
    }

    suspend fun loadClipVideo(useCronet: Boolean, headers: Map<String, String>, slug: String?): ClipVideoResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "9aa558e066a22227c5ef2c0a8fded3aaa57d35181ad15f63df25bff516253a90")
                    put("version", 1)
                }
            }
            put("operationName", "ChatClip")
            putJsonObject("variables") {
                put("clipSlug", slug)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ClipVideoResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ClipVideoResponse>(response.body.string())
            }
        }
    }

    suspend fun loadTopGames(useCronet: Boolean, headers: Map<String, String>, tags: List<String>?, limit: Int?, cursor: String?): GamesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2f67f71ba89f3c0ed26a141ec00da1defecb2303595f5cda4298169549783d9e")
                    put("version", 1)
                }
            }
            put("operationName", "BrowsePage_AllDirectories")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("limit", limit)
                putJsonObject("options") {
                    put("sort", "VIEWER_COUNT")
                    putJsonArray("tags") {
                        tags?.forEach {
                            add(it)
                        }
                    }
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<GamesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadTopStreams(useCronet: Boolean, headers: Map<String, String>, tags: List<String>?, limit: Int?, cursor: String?): StreamsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "75a4899f0a765cc08576125512f710e157b147897c06f96325de72d4c5a64890")
                    put("version", 1)
                }
            }
            put("operationName", "BrowsePage_Popular")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("includeIsDJ", true)
                put("limit", limit)
                put("platformType", "all")
                put("sortTypeIsRecency", false)
                putJsonObject("options") {
                    putJsonArray("freeformTags") {
                        tags?.forEach {
                            add(it)
                        }
                    }
                    put("sort", "VIEWER_COUNT")
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<StreamsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<StreamsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadGameStreams(useCronet: Boolean, headers: Map<String, String>, gameSlug: String?, sort: String?, tags: List<String>?, limit: Int?, cursor: String?): GameStreamsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c7c9d5aad09155c4161d2382092dc44610367f3536aac39019ec2582ae5065f9")
                    put("version", 1)
                }
            }
            put("operationName", "DirectoryPage_Game")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("includeIsDJ", true)
                put("limit", limit)
                put("slug", gameSlug)
                put("sortTypeIsRecency", false)
                putJsonObject("options") {
                    putJsonArray("freeformTags") {
                        tags?.forEach {
                            add(it)
                        }
                    }
                    put("sort", sort)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GameStreamsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<GameStreamsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadGameVideos(useCronet: Boolean, headers: Map<String, String>, gameSlug: String?, type: String?, sort: String?, limit: Int?, cursor: String?): GameVideosResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b1b02043611ce6f315eb37cb5ecfd0dab38ffeeab1958dfbe538787cc14d5fc3")
                    put("version", 1)
                }
            }
            put("operationName", "DirectoryVideos_Game")
            putJsonObject("variables") {
                if (type != null) {
                    put("broadcastTypes", type)
                }
                put("followedCursor", cursor)
                put("slug", gameSlug)
                put("videoLimit", limit)
                put("videoSort", sort)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GameVideosResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<GameVideosResponse>(response.body.string())
            }
        }
    }

    suspend fun loadGameClips(useCronet: Boolean, headers: Map<String, String>, gameSlug: String?, sort: String?, limit: Int?, cursor: String?): GameClipsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b814240ae1e920af4573e9a9f0b04951528cb5ee60a7c47a484edae15068f26b")
                    put("version", 1)
                }
            }
            put("operationName", "ClipsCards__Game")
            putJsonObject("variables") {
                putJsonObject("criteria") {
                    put("filter", sort)
                }
                put("cursor", cursor)
                put("categorySlug", gameSlug)
                put("limit", limit)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GameClipsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<GameClipsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadChannelVideos(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?, type: String?, sort: String?, limit: Int?, cursor: String?): ChannelVideosResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "08eed732ca804e536f9262c6ce87e0e15f07d6d3c047e8e5d7a461afd5a66a00")
                    put("version", 1)
                }
            }
            put("operationName", "FilterableVideoTower_Videos")
            putJsonObject("variables") {
                put("broadcastType", type)
                put("cursor", cursor)
                put("channelOwnerLogin", channelLogin)
                put("limit", limit)
                put("videoSort", sort)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChannelVideosResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ChannelVideosResponse>(response.body.string())
            }
        }
    }

    suspend fun loadChannelClips(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?, sort: String?, limit: Int?, cursor: String?): ChannelClipsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "fa3122f0b8fbd980f247a0f885c8097c154debc595dbcb815265669ea410c2eb")
                    put("version", 1)
                }
            }
            put("operationName", "ClipsCards__User")
            putJsonObject("variables") {
                putJsonObject("criteria") {
                    put("filter", sort)
                }
                put("cursor", cursor)
                put("login", channelLogin)
                put("limit", limit)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChannelClipsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ChannelClipsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadSearchChannels(useCronet: Boolean, headers: Map<String, String>, query: String?, cursor: String?): SearchChannelsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f6c2575aee4418e8a616e03364d8bcdbf0b10a5c87b59f523569dacc963e8da5")
                    put("version", 1)
                }
            }
            put("operationName", "SearchResultsPage_SearchResults")
            putJsonObject("variables") {
                putJsonObject("options") {
                    putJsonArray("targets") {
                        add(buildJsonObject {
                            put("cursor", cursor)
                            put("index", "CHANNEL")
                        })
                    }
                }
                put("includeIsDJ", true)
                put("query", query)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<SearchChannelsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<SearchChannelsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadSearchGames(useCronet: Boolean, headers: Map<String, String>, query: String?, cursor: String?): SearchGamesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f6c2575aee4418e8a616e03364d8bcdbf0b10a5c87b59f523569dacc963e8da5")
                    put("version", 1)
                }
            }
            put("operationName", "SearchResultsPage_SearchResults")
            putJsonObject("variables") {
                putJsonObject("options") {
                    putJsonArray("targets") {
                        add(buildJsonObject {
                            put("cursor", cursor)
                            put("index", "GAME")
                        })
                    }
                }
                put("includeIsDJ", true)
                put("query", query)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<SearchGamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<SearchGamesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadSearchVideos(useCronet: Boolean, headers: Map<String, String>, query: String?, cursor: String?): SearchVideosResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f6c2575aee4418e8a616e03364d8bcdbf0b10a5c87b59f523569dacc963e8da5")
                    put("version", 1)
                }
            }
            put("operationName", "SearchResultsPage_SearchResults")
            putJsonObject("variables") {
                putJsonObject("options") {
                    putJsonArray("targets") {
                        add(buildJsonObject {
                            put("cursor", cursor)
                            put("index", "VOD")
                        })
                    }
                }
                put("includeIsDJ", true)
                put("query", query)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<SearchVideosResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<SearchVideosResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFreeformTags(useCronet: Boolean, headers: Map<String, String>, query: String?, limit: Int?): SearchStreamTagsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "8bc91a618bb5f0c5f9bc19195028c9f4a6a1b8651cf5bd8e4f2408124cdf465a")
                    put("version", 1)
                }
            }
            put("operationName", "SearchFreeformTags")
            putJsonObject("variables") {
                put("first", limit)
                put("userQuery", query ?: "")
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<SearchStreamTagsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<SearchStreamTagsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadGameTags(useCronet: Boolean, headers: Map<String, String>, query: String?, limit: Int?): SearchGameTagsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b4cb189d8d17aadf29c61e9d7c7e7dcfc932e93b77b3209af5661bffb484195f")
                    put("version", 1)
                }
            }
            put("operationName", "SearchCategoryTags")
            putJsonObject("variables") {
                put("limit", limit)
                put("userQuery", query ?: "")
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<SearchGameTagsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<SearchGameTagsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadChatBadges(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): BadgesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "dd0997370fb7ca288bc52a96a9a7e3222c75c4a9a9b03df17d779666f07f7529")
                    put("version", 1)
                }
            }
            put("operationName", "ChatList_Badges")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<BadgesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<BadgesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadGlobalCheerEmotes(useCronet: Boolean, headers: Map<String, String>): GlobalCheerEmotesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6a265b86f3be1c8d11bdcf32c183e106028c6171e985cc2584d15f7840f5fee6")
                    put("version", 1)
                }
            }
            put("operationName", "BitsConfigContext_Global")
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GlobalCheerEmotesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<GlobalCheerEmotesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadChannelCheerEmotes(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): ChannelCheerEmotesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "d897953c76165a0d2a12b57c9c013a77b3cf02b5c153645e1e1631f763bf1eb5")
                    put("version", 1)
                }
            }
            put("operationName", "BitsConfigContext_Channel")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChannelCheerEmotesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ChannelCheerEmotesResponse>(response.body.string())
            }
        }
    }

    private fun getVideoMessagesRequestBody(videoId: String?, offset: Int?, cursor: String?): String {
        return buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b70a3591ff0f4e0313d126c6a1502d79a1c02baebb288227c582044aa76adf6a")
                    put("version", 1)
                }
            }
            put("operationName", "VideoCommentsByOffsetOrCursor")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("contentOffsetSeconds", offset)
                put("videoID", videoId)
            }
        }.toString()
    }

    suspend fun loadVideoMessages(useCronet: Boolean, headers: Map<String, String>, videoId: String?, offset: Int? = null, cursor: String? = null): VideoMessagesResponse = withContext(Dispatchers.IO) {
        val body = getVideoMessagesRequestBody(videoId, offset, cursor)
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<VideoMessagesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<VideoMessagesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadVideoMessagesDownload(useCronet: Boolean, headers: Map<String, String>, videoId: String?, offset: Int? = null, cursor: String? = null): JsonElement = withContext(Dispatchers.IO) {
        val body = getVideoMessagesRequestBody(videoId, offset, cursor)
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<JsonElement>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<JsonElement>(response.body.string())
            }
        }
    }

    suspend fun loadVideoGames(useCronet: Boolean, headers: Map<String, String>, videoId: String?): VideoGamesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "71835d5ef425e154bf282453a926d99b328cdc5e32f36d3a209d0f4778b41203")
                    put("version", 1)
                }
            }
            put("operationName", "VideoPlayer_ChapterSelectButtonVideo")
            putJsonObject("variables") {
                put("videoID", videoId)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<VideoGamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<VideoGamesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadChannelViewerList(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): ChannelViewerListResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2e71a3399875770c1e5d81a9774d9803129c44cf8f6bad64973aa0d239a88caf")
                    put("version", 1)
                }
            }
            put("operationName", "CommunityTab")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChannelViewerListResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ChannelViewerListResponse>(response.body.string())
            }
        }
    }

    suspend fun loadViewerCount(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): ViewerCountResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "00b11c9c428f79ae228f30080a06ffd8226a1f068d6f52fbc057cbde66e994c2")
                    put("version", 1)
                }
            }
            put("operationName", "UseViewCount")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ViewerCountResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ViewerCountResponse>(response.body.string())
            }
        }
    }

    suspend fun loadEmoteCard(useCronet: Boolean, headers: Map<String, String>, emoteId: String?): EmoteCardResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "af523cd8807a390830351eb5362633a2c718d6b5cd05494a894af86770c817d6")
                    put("version", 1)
                }
            }
            put("operationName", "EmoteCard")
            putJsonObject("variables") {
                put("emoteID", emoteId)
                put("octaneEnabled", true)
                put("artistEnabled", true)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<EmoteCardResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<EmoteCardResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowedStreams(useCronet: Boolean, headers: Map<String, String>, limit: Int?, cursor: String?): FollowedStreamsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "ecadcf350272dde399a63385cf888903d7fcd4c8fc6809a8469fe3753579d1c6")
                    put("version", 1)
                }
            }
            put("operationName", "FollowingLive_CurrentUser")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("includeIsDJ", true)
                put("limit", limit)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowedStreamsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowedStreamsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowedVideos(useCronet: Boolean, headers: Map<String, String>, limit: Int?, cursor: String?): FollowedVideosResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "11d0ddb94121afab8fa8b641e01f038db35892f95b4e4b9e5380eaa33d5e4a8c")
                    put("version", 1)
                }
            }
            put("operationName", "FollowedVideos_CurrentUser")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("limit", limit)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowedVideosResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowedVideosResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowedChannels(useCronet: Boolean, headers: Map<String, String>, limit: Int?, cursor: String?): FollowedChannelsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "eecf815273d3d949e5cf0085cc5084cd8a1b5b7b6f7990cf43cb0beadf546907")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelFollows")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("limit", limit)
                put("order", "DESC")
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowedChannelsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowedChannelsResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowedGames(useCronet: Boolean, headers: Map<String, String>, limit: Int?): FollowedGamesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f3c5d45175d623ed3d5ff4ca4c7de379ea6a1a4852236087dc1b81b7dbfd3114")
                    put("version", 1)
                }
            }
            put("operationName", "FollowingGames_CurrentUser")
            putJsonObject("variables") {
                put("limit", limit)
                put("type", "ALL")
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowedGamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowedGamesResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowUser(useCronet: Boolean, headers: Map<String, String>, userId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "800e7346bdf7e5278a3c1d3f21b2b56e2639928f86815677a7126b093b2fdd08")
                    put("version", 1)
                }
            }
            put("operationName", "FollowButton_FollowUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("disableNotifications", false)
                    put("targetID", userId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadUnfollowUser(useCronet: Boolean, headers: Map<String, String>, userId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f7dae976ebf41c755ae2d758546bfd176b4eeb856656098bb40e0a672ca0d880")
                    put("version", 1)
                }
            }
            put("operationName", "FollowButton_UnfollowUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("targetID", userId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadToggleNotificationsUser(useCronet: Boolean, headers: Map<String, String>, userId: String?, disableNotifications: Boolean): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2319a2486246f63b13ffc0d1c317c89df177150185352791a81eb7bced0128a1")
                    put("version", 1)
                }
            }
            put("operationName", "LiveNotificationsToggle_ToggleNotifications")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("disableNotifications", disableNotifications)
                    put("targetID", userId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowGame(useCronet: Boolean, headers: Map<String, String>, gameId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "b846b65ba4bc9a3561dbe2d069d95deed9b9e031bcfda2482d1bedd84a1c2eb3")
                    put("version", 1)
                }
            }
            put("operationName", "FollowGameButton_FollowGame")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("gameID", gameId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadUnfollowGame(useCronet: Boolean, headers: Map<String, String>, gameId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "811e02e396ebba0664f21ff002f2eff3c6f57e8af9aedb4f4dfa77cefd0db43d")
                    put("version", 1)
                }
            }
            put("operationName", "FollowGameButton_UnfollowGame")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("gameID", gameId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowingUser(useCronet: Boolean, headers: Map<String, String>, userLogin: String?): FollowingUserResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "834a75e1c06cffada00f0900664a5033e392f6fb655fae8d2e25b21b340545a9")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelSupportButtons")
            putJsonObject("variables") {
                put("channelLogin", userLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowingUserResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowingUserResponse>(response.body.string())
            }
        }
    }

    suspend fun loadFollowingGame(useCronet: Boolean, headers: Map<String, String>, gameName: String?): FollowingGameResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "cfeda60899b6b867b2d7f30c8556778c4a9cc8268bd1aadd9f88134a0f642a02")
                    put("version", 1)
                }
            }
            put("operationName", "FollowGameButton_Game")
            putJsonObject("variables") {
                put("name", gameName)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowingGameResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowingGameResponse>(response.body.string())
            }
        }
    }

    suspend fun loadChannelPointsContext(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): ChannelPointContextResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "374314de591e69925fce3ddc2bcf085796f56ebb8cad67a0daa3165c03adc345")
                    put("version", 1)
                }
            }
            put("operationName", "ChannelPointsContext")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChannelPointContextResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ChannelPointContextResponse>(response.body.string())
            }
        }
    }

    suspend fun loadClaimPoints(useCronet: Boolean, headers: Map<String, String>, channelId: String?, claimId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "46aaeebe02c99afdf4fc97c7c0cba964124bf6b0af229395f1f6d1feed05b3d0")
                    put("version", 1)
                }
            }
            put("operationName", "ClaimCommunityPoints")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("claimID", claimId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadJoinRaid(useCronet: Boolean, headers: Map<String, String>, raidId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c6a332a86d1087fbbb1a8623aa01bd1313d2386e7c63be60fdb2d1901f01a4ae")
                    put("version", 1)
                }
            }
            put("operationName", "JoinRaid")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("raidID", raidId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun loadUserEmotes(useCronet: Boolean, headers: Map<String, String>, channelId: String?, cursor: String?): UserEmotesResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6c45e0ecaa823cc7db3ecdd1502af2223c775bdcfb0f18a3a0ce9a0b7db8ef6c")
                    put("version", 1)
                }
            }
            put("operationName", "AvailableEmotesForChannelPaginated")
            putJsonObject("variables") {
                put("cursor", cursor)
                put("channelID", channelId)
                put("pageLimit", 350)
                put("withOwner", true)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<UserEmotesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<UserEmotesResponse>(response.body.string())
            }
        }
    }

    suspend fun sendMessage(useCronet: Boolean, headers: Map<String, String>, channelId: String?, message: String?, replyId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "0435464292cf380ed4b3d905e4edcb73078362e82c06367a5b2181c76c822fa2")
                    put("version", 1)
                }
            }
            put("operationName", "sendChatMessage")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("message", message)
                    put("replyParentMessageID", replyId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun sendAnnouncement(useCronet: Boolean, headers: Map<String, String>, channelId: String?, message: String?, color: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f9e37b572ceaca1475d8d50805ae64d6eb388faf758556b2719f44d64e5ba791")
                    put("version", 1)
                }
            }
            put("operationName", "SendAnnouncementMessage")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("message", message)
                    put("color", color)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun banUser(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetLogin: String?, duration: String? = null, reason: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "d7be2d2e1e22813c1c2f3d9d5bf7e425d815aeb09e14001a5f2c140b93f6fb67")
                    put("version", 1)
                }
            }
            put("operationName", "Chat_BanUserFromChatRoom")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("bannedUserLogin", targetLogin)
                    put("expiresIn", duration)
                    put("reason", reason)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun unbanUser(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetLogin: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "bee22da7ae03569eb9ae41ef857fd1bb75507d4984d764a81fe8775accac71bd")
                    put("version", 1)
                }
            }
            put("operationName", "Chat_UnbanUserFromChatRoom")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("bannedUserLogin", targetLogin)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun updateChatColor(useCronet: Boolean, headers: Map<String, String>, color: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "0371259a74a3db4ff4bf4473d998d8ae8e4f135b20403323691d434f2790e081")
                    put("version", 1)
                }
            }
            put("operationName", "Chat_UpdateChatColor")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("color", color)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun updateChatSettings(useCronet: Boolean, headers: Map<String, String>, channelId: String?, emote: Boolean? = null): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "6d8b11f4e29f87be5e2397dd54b2df669e9a5aacd831252d88b7b7a6616dc170")
                    put("version", 1)
                }
            }
            put("operationName", "UpdateChatSettings")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    emote?.let { put("isEmoteOnlyModeEnabled", it) }
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun setFollowersOnlyMode(useCronet: Boolean, headers: Map<String, String>, channelId: String?, duration: Int?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "0ee2e448691c84b4be72bcd1ae6c51fcf512414fe372e502fe67d3c7eaf8da31")
                    put("version", 1)
                }
            }
            put("operationName", "SetFollowersOnlyModeSetting")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("followersOnlyDurationMinutes", duration)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun setSlowMode(useCronet: Boolean, headers: Map<String, String>, channelId: String?, duration: Int?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "8cf824c7fbe97e1fbde96125bffabd5b315b7f370c2ddae317f058a7b9b1c57d")
                    put("version", 1)
                }
            }
            put("operationName", "SetSlowModeSetting")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("slowModeDurationSeconds", duration)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun createStreamMarker(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c65f8b33e3bcccf2b16057e8f445311d213ecf8729f842ccdc71908231fa9a78")
                    put("version", 1)
                }
            }
            put("operationName", "VideoMarkersChatCommand")
            putJsonObject("variables") {
                put("channelLogin", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun getModerators(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): ModeratorsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "cb912a7e0789e0f8a4c85c25041a08324475831024d03d624172b59498caf085")
                    put("version", 1)
                }
            }
            put("operationName", "Mods")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ModeratorsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ModeratorsResponse>(response.body.string())
            }
        }
    }

    suspend fun addModerator(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetLogin: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "46da4ec4229593fe4b1bce911c75625c299638e228262ff621f80d5067695a8a")
                    put("version", 1)
                }
            }
            put("operationName", "ModUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("targetLogin", targetLogin)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun removeModerator(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetLogin: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "1ed42ccb3bc3a6e79f51e954a2df233827f94491fbbb9bd05b22b1aaaf219b8b")
                    put("version", 1)
                }
            }
            put("operationName", "UnmodUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("targetLogin", targetLogin)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun startRaid(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "f4fc7ac482599d81dfb6aa37100923c8c9edeea9ca2be854102a6339197f840a")
                    put("version", 1)
                }
            }
            put("operationName", "chatCreateRaid")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("sourceID", channelId)
                    put("targetID", targetId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun cancelRaid(useCronet: Boolean, headers: Map<String, String>, channelId: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "c388b89e7616a11a8a07b75e3d7bbe7278d37c3c46f43d7c8d4d0262edc00cd9")
                    put("version", 1)
                }
            }
            put("operationName", "chatCancelRaid")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("sourceID", channelId)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun getVips(useCronet: Boolean, headers: Map<String, String>, channelLogin: String?): VipsResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "612a574d07afe5db2f9e878e290225224a0b955e65b5d1235dcd4b68ff668218")
                    put("version", 1)
                }
            }
            put("operationName", "VIPs")
            putJsonObject("variables") {
                put("login", channelLogin)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<VipsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<VipsResponse>(response.body.string())
            }
        }
    }

    suspend fun addVip(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetLogin: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "e8c397f1ed8b1fdbaa201eedac92dd189ecfb2d828985ec159d4ae77f9920170")
                    put("version", 1)
                }
            }
            put("operationName", "VIPUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("granteeLogin", targetLogin)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }

    suspend fun removeVip(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetLogin: String?): ErrorResponse = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("sha256Hash", "2ce4fcdf6667d013aa1f820010e699d1d4abdda55e26539ecf4efba8aff2d661")
                    put("version", 1)
                }
            }
            put("operationName", "UnVIPUser")
            putJsonObject("variables") {
                putJsonObject("input") {
                    put("channelID", channelId)
                    put("revokeeLogin", targetLogin)
                }
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://gql.twitch.tv/gql/", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ErrorResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                headers(headers.toHeaders())
                header("Content-Type", "application/json")
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                json.decodeFromString<ErrorResponse>(response.body.string())
            }
        }
    }
}