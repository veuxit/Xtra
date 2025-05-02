package com.github.andreyasadchy.xtra.repository

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UploadDataProviders
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelixRepository @Inject constructor(
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {

    suspend fun getGames(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, names: List<String>? = null): GamesResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            ids?.forEach { put("id", it) }
            names?.forEach { put("name", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/games${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/games${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<GamesResponse>(response.body.string())
            }
        }
    }

    suspend fun getTopGames(useCronet: Boolean, headers: Map<String, String>, limit: Int?, offset: String?): GamesResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/games/top${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/games/top${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<GamesResponse>(response.body.string())
            }
        }
    }

    suspend fun getStreams(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null, gameId: String? = null, languages: List<String>? = null, limit: Int? = null, offset: String? = null): StreamsResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            ids?.forEach { put("user_id", it) }
            logins?.forEach { put("user_login", it) }
            gameId?.let { put("game_id", it) }
            languages?.forEach { put("language", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/streams${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<StreamsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/streams${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<StreamsResponse>(response.body.string())
            }
        }
    }

    suspend fun getFollowedStreams(useCronet: Boolean, headers: Map<String, String>, userId: String?, limit: Int?, offset: String?): StreamsResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("user_id", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/streams/followed${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<StreamsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/streams/followed${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<StreamsResponse>(response.body.string())
            }
        }
    }

    suspend fun getClips(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, channelId: String? = null, gameId: String? = null, startedAt: String? = null, endedAt: String? = null, limit: Int? = null, offset: String? = null): ClipsResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            ids?.forEach { put("id", it) }
            channelId?.let { put("broadcaster_id", it) }
            gameId?.let { put("game_id", it) }
            startedAt?.let { put("started_at", it) }
            endedAt?.let { put("ended_at", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/clips${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ClipsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/clips${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<ClipsResponse>(response.body.string())
            }
        }
    }

    suspend fun getVideos(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, gameId: String? = null, channelId: String? = null, period: String? = null, broadcastType: String? = null, sort: String? = null, language: String? = null, limit: Int? = null, offset: String? = null): VideosResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            ids?.forEach { put("id", it) }
            gameId?.let { put("game_id", it) }
            channelId?.let { put("user_id", it) }
            period?.let { put("period", it) }
            broadcastType?.let { put("type", it) }
            sort?.let { put("sort", it) }
            language?.let { put("language", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/videos${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<VideosResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/videos${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<VideosResponse>(response.body.string())
            }
        }
    }

    suspend fun getUsers(useCronet: Boolean, headers: Map<String, String>, ids: List<String>? = null, logins: List<String>? = null): UsersResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            ids?.forEach { put("id", it) }
            logins?.forEach { put("login", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/users${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<UsersResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/users${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<UsersResponse>(response.body.string())
            }
        }
    }

    suspend fun getSearchGames(useCronet: Boolean, headers: Map<String, String>, query: String?, limit: Int?, offset: String?): GamesResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            query?.let { put("query", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/search/categories${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<GamesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/search/categories${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<GamesResponse>(response.body.string())
            }
        }
    }

    suspend fun getSearchChannels(useCronet: Boolean, headers: Map<String, String>, query: String?, limit: Int?, offset: String?, live: Boolean? = null): ChannelSearchResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            query?.let { put("query", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
            live?.let { put("live_only", it.toString()) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/search/channels${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChannelSearchResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/search/channels${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<ChannelSearchResponse>(response.body.string())
            }
        }
    }

    suspend fun getUserFollows(useCronet: Boolean, headers: Map<String, String>, userId: String?, targetId: String? = null, limit: Int? = null, offset: String? = null): FollowsResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("user_id", it) }
            targetId?.let { put("broadcaster_id", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/channels/followed${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/channels/followed${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowsResponse>(response.body.string())
            }
        }
    }

    suspend fun getUserFollowers(useCronet: Boolean, headers: Map<String, String>, userId: String?, targetId: String? = null, limit: Int? = null, offset: String? = null): FollowsResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            targetId?.let { put("user_id", it) }
            userId?.let { put("broadcaster_id", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/channels/followers${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<FollowsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/channels/followers${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<FollowsResponse>(response.body.string())
            }
        }
    }

    suspend fun getUserEmotes(useCronet: Boolean, headers: Map<String, String>, userId: String?, channelId: String?, offset: String?): UserEmotesResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("user_id", it) }
            channelId?.let { put("broadcaster_id", it) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/emotes/user${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<UserEmotesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/emotes/user${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<UserEmotesResponse>(response.body.string())
            }
        }
    }

    suspend fun getEmotesFromSet(useCronet: Boolean, headers: Map<String, String>, setIds: List<String>): EmoteSetsResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            setIds.forEach { put("emote_set_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/emotes/set${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<EmoteSetsResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/emotes/set${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<EmoteSetsResponse>(response.body.string())
            }
        }
    }

    suspend fun getGlobalBadges(useCronet: Boolean, headers: Map<String, String>): BadgesResponse = withContext(Dispatchers.IO) {
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/badges/global", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<BadgesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/badges/global")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<BadgesResponse>(response.body.string())
            }
        }
    }

    suspend fun getChannelBadges(useCronet: Boolean, headers: Map<String, String>, userId: String?): BadgesResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("broadcaster_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/badges${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<BadgesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/badges${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<BadgesResponse>(response.body.string())
            }
        }
    }

    suspend fun getCheerEmotes(useCronet: Boolean, headers: Map<String, String>, userId: String?): CheerEmotesResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("broadcaster_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/bits/cheermotes${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<CheerEmotesResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/bits/cheermotes${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<CheerEmotesResponse>(response.body.string())
            }
        }
    }

    suspend fun getChatters(useCronet: Boolean, headers: Map<String, String>, channelId: String?, userId: String?, limit: Int? = null, offset: String? = null): ChatUsersResponse = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            userId?.let { put("moderator_id", it) }
            limit?.let { put("first", it.toString()) }
            offset?.let { put("after", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/chatters${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get().responseBody as String
            json.decodeFromString<ChatUsersResponse>(response)
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/chatters${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                json.decodeFromString<ChatUsersResponse>(response.body.string())
            }
        }
    }

    suspend fun createEventSubSubscription(useCronet: Boolean, headers: Map<String, String>, userId: String?, channelId: String?, type: String?, sessionId: String?): String? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("type", type)
            put("version", "1")
            putJsonObject("condition") {
                put("broadcaster_user_id", channelId)
                put("user_id", userId)
            }
            putJsonObject("transport") {
                put("method", "websocket")
                put("session_id", sessionId)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/eventsub/subscriptions", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/eventsub/subscriptions")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun sendMessage(useCronet: Boolean, headers: Map<String, String>, userId: String?, channelId: String?, message: String?, replyId: String?): String? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("broadcaster_id", channelId)
            put("sender_id", userId)
            put("message", message)
            replyId?.let { put("reply_parent_message_id", it) }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/messages", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/messages")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun sendAnnouncement(useCronet: Boolean, headers: Map<String, String>, channelId: String?, userId: String?, message: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            userId?.let { put("moderator_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        val body = buildJsonObject {
            put("message", message)
            color?.let { put("color", it) }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/announcements${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/announcements${query}")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun banUser(useCronet: Boolean, headers: Map<String, String>, channelId: String?, userId: String?, targetId: String?, duration: String? = null, reason: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            userId?.let { put("moderator_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        val body = buildJsonObject {
            putJsonObject("data") {
                duration?.toIntOrNull()?.let { put("duration", it) }
                put("reason", reason)
                put("user_id", targetId)
            }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/moderation/bans${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/moderation/bans${query}")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun unbanUser(useCronet: Boolean, headers: Map<String, String>, channelId: String?, userId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            userId?.let { put("moderator_id", it) }
            targetId?.let { put("user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/moderation/bans${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                setHttpMethod("DELETE")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/moderation/bans${query}")
                headers(headers.toHeaders())
                method("DELETE", null)
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun deleteMessages(useCronet: Boolean, headers: Map<String, String>, channelId: String?, userId: String?, messageId: String? = null): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            userId?.let { put("moderator_id", it) }
            messageId?.let { put("message_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/moderation/chat${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                setHttpMethod("DELETE")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/moderation/chat${query}")
                headers(headers.toHeaders())
                method("DELETE", null)
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun getChatColor(useCronet: Boolean, headers: Map<String, String>, userId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/color${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                json.decodeFromString<JsonElement>(response.responseBody as String).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("color")?.jsonPrimitive?.contentOrNull
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/color${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    json.decodeFromString<JsonElement>(response.body.string()).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("color")?.jsonPrimitive?.contentOrNull
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun updateChatColor(useCronet: Boolean, headers: Map<String, String>, userId: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("user_id", it) }
            color?.let { put("color", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/color${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                setHttpMethod("PUT")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/color${query}")
                headers(headers.toHeaders())
                method("PUT", null)
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun startCommercial(useCronet: Boolean, headers: Map<String, String>, channelId: String?, length: String?): String? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("broadcaster_id", channelId)
            put("length", length?.toIntOrNull())
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/channels/commercial", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                json.decodeFromString<JsonElement>(response.responseBody as String).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/channels/commercial")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    json.decodeFromString<JsonElement>(response.body.string()).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun updateChatSettings(useCronet: Boolean, headers: Map<String, String>, channelId: String?, userId: String?, emote: Boolean? = null, followers: Boolean? = null, followersDuration: String? = null, slow: Boolean? = null, slowDuration: Int? = null, subs: Boolean? = null, unique: Boolean? = null): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            userId?.let { put("moderator_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        val body = buildJsonObject {
            emote?.let { put("emote_mode", it) }
            followers?.let { put("follower_mode", it) }
            followersDuration?.toIntOrNull()?.let { put("follower_mode_duration", it) }
            slow?.let { put("slow_mode", it) }
            slowDuration?.let { put("slow_mode_wait_time", it) }
            subs?.let { put("subscriber_mode", it) }
            unique?.let { put("unique_chat_mode", it) }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/chat/settings${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
                setHttpMethod("PATCH")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/chat/settings${query}")
                headers(headers.toHeaders())
                method("PATCH", body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun createStreamMarker(useCronet: Boolean, headers: Map<String, String>, channelId: String?, description: String?): String? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("user_id", channelId)
            description?.let { put("description", it) }
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/streams/markers", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/streams/markers")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun addModerator(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            targetId?.let { put("user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/moderation/moderators${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/moderation/moderators${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun removeModerator(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            targetId?.let { put("user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/moderation/moderators${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                setHttpMethod("DELETE")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/moderation/moderators${query}")
                headers(headers.toHeaders())
                method("DELETE", null)
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun startRaid(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("from_broadcaster_id", it) }
            targetId?.let { put("to_broadcaster_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/raids${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/raids${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun cancelRaid(useCronet: Boolean, headers: Map<String, String>, channelId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/raids${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                setHttpMethod("DELETE")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/raids${query}")
                headers(headers.toHeaders())
                method("DELETE", null)
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun addVip(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            targetId?.let { put("user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/channels/vips${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/channels/vips${query}")
                headers(headers.toHeaders())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun removeVip(useCronet: Boolean, headers: Map<String, String>, channelId: String?, targetId: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            channelId?.let { put("broadcaster_id", it) }
            targetId?.let { put("user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/channels/vips${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                setHttpMethod("DELETE")
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/channels/vips${query}")
                headers(headers.toHeaders())
                method("DELETE", null)
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }

    suspend fun sendWhisper(useCronet: Boolean, headers: Map<String, String>, userId: String?, targetId: String?, message: String?): String? = withContext(Dispatchers.IO) {
        val query = mutableMapOf<String, String>().apply {
            userId?.let { put("from_user_id", it) }
            targetId?.let { put("to_user_id", it) }
        }.takeIf { it.isNotEmpty() }?.let {
            it.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8.name())}" }.joinToString("&", "?")
        } ?: ""
        val body = buildJsonObject {
            put("message", message)
        }.toString()
        if (useCronet && cronetEngine != null) {
            val request = UrlRequestCallbacks.forStringBody(RedirectHandlers.alwaysFollow())
            cronetEngine.newUrlRequestBuilder("https://api.twitch.tv/helix/whispers${query}", request.callback, cronetExecutor).apply {
                headers.forEach { addHeader(it.key, it.value) }
                addHeader("Content-Type", "application/json")
                setUploadDataProvider(UploadDataProviders.create(body.toByteArray()), cronetExecutor)
            }.build().start()
            val response = request.future.get()
            if (response.urlResponseInfo.httpStatusCode in 200..299) {
                null
            } else {
                response.responseBody as String
            }
        } else {
            okHttpClient.newCall(Request.Builder().apply {
                url("https://api.twitch.tv/helix/whispers${query}")
                headers(headers.toHeaders())
                post(body.toRequestBody())
            }.build()).execute().use { response ->
                if (response.isSuccessful) {
                    null
                } else {
                    response.body.string()
                }
            }
        }
    }
}