package com.github.andreyasadchy.xtra.repository

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.api.UsherApi
import com.github.andreyasadchy.xtra.db.RecentEmotesDao
import com.github.andreyasadchy.xtra.db.VideoPositionsDao
import com.github.andreyasadchy.xtra.model.PlaybackAccessToken
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.chat.BttvChannelResponse
import com.github.andreyasadchy.xtra.model.chat.BttvGlobalResponse
import com.github.andreyasadchy.xtra.model.chat.FfzChannelResponse
import com.github.andreyasadchy.xtra.model.chat.FfzGlobalResponse
import com.github.andreyasadchy.xtra.model.chat.RecentEmote
import com.github.andreyasadchy.xtra.model.chat.RecentMessagesResponse
import com.github.andreyasadchy.xtra.model.chat.StvChannelResponse
import com.github.andreyasadchy.xtra.model.chat.StvGlobalResponse
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.m3u8.MediaPlaylist
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.use
import org.json.JSONObject
import retrofit2.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PlayerRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val usher: UsherApi,
    private val misc: MiscApi,
    private val graphQL: GraphQLRepository,
    private val recentEmotes: RecentEmotesDao,
    private val videoPositions: VideoPositionsDao) {

    suspend fun getMediaPlaylist(url: String): MediaPlaylist = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            response.body()!!.byteStream().use {
                PlaylistUtils.parseMediaPlaylist(it)
            }
        }
    }

    suspend fun loadStreamPlaylistUrl(gqlHeaders: Map<String, String>, channelLogin: String, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, proxyPlaybackAccessToken: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?, enableIntegrity: Boolean): String = withContext(Dispatchers.IO) {
        val accessToken = loadStreamPlaybackAccessToken(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, proxyPlaybackAccessToken, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
        buildUrl(
            "https://usher.ttvnw.net/api/channel/hls/$channelLogin.m3u8?",
            "allow_source", "true",
            "allow_audio_only", "true",
            "fast_bread", "true", //low latency
            "p", Random.nextInt(9999999).toString(),
            "platform", if (supportedCodecs?.contains("av1", true) == true) "web" else null,
            "sig", accessToken?.signature,
            "supported_codecs", supportedCodecs,
            "token", accessToken?.token
        ).toString()
    }

    suspend fun loadStreamPlaylistResponse(url: String, proxyMultivariantPlaylist: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?): ResponseBody = withContext(Dispatchers.IO) {
        okHttpClient.newBuilder().apply {
            if (proxyMultivariantPlaylist && !proxyHost.isNullOrBlank() && proxyPort != null) {
                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
                if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                    proxyAuthenticator { _, response ->
                        response.request().newBuilder().header("Proxy-Authorization", Credentials.basic(proxyUser, proxyPassword)).build()
                    }
                }
            }
        }.build().newCall(Request.Builder().url(url).build()).execute().use { response ->
            response.body()!!
        }
    }

    private suspend fun loadStreamPlaybackAccessToken(gqlHeaders: Map<String, String>, channelLogin: String, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, proxyPlaybackAccessToken: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?, enableIntegrity: Boolean): PlaybackAccessToken? = withContext(Dispatchers.IO) {
        val accessTokenHeaders = getPlaybackAccessTokenHeaders(gqlHeaders, randomDeviceId, xDeviceId, enableIntegrity)
        if (proxyPlaybackAccessToken && !proxyHost.isNullOrBlank() && proxyPort != null) {
            okHttpClient.newBuilder().apply {
                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
                if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                    proxyAuthenticator { _, response ->
                        response.request().newBuilder().header(
                            "Proxy-Authorization", Credentials.basic(proxyUser, proxyPassword)
                        ).build()
                    }
                }
            }.build().newCall(Request.Builder().apply {
                url("https://gql.twitch.tv/gql/")
                post(RequestBody.create(MediaType.get("application/x-www-form-urlencoded; charset=utf-8"), graphQL.getPlaybackAccessTokenRequestBody(channelLogin, "", playerType).toString()))
                accessTokenHeaders.filterKeys { it == C.HEADER_CLIENT_ID || it == "X-Device-Id" }.forEach {
                    addHeader(it.key, it.value)
                }
            }.build()).execute().use { response ->
                val text = response.body()!!.string()
                if (text.isNotBlank()) {
                    val message = JSONObject(text).optJSONObject("data")?.optJSONObject("streamPlaybackAccessToken")
                    PlaybackAccessToken(
                        token = message?.optString("value"),
                        signature = message?.optString("signature"),
                    )
                } else null
            }
        } else {
            graphQL.loadPlaybackAccessToken(
                headers = accessTokenHeaders,
                login = channelLogin,
                playerType = playerType
            ).streamToken
        }
    }

    suspend fun loadVideoPlaylistUrl(gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean): Uri = withContext(Dispatchers.IO) {
        val accessToken = loadVideoPlaybackAccessToken(gqlHeaders, videoId, playerType, enableIntegrity)
        buildUrl(
            "https://usher.ttvnw.net/vod/$videoId.m3u8?",
            "allow_source", "true",
            "allow_audio_only", "true",
            "p", Random.nextInt(9999999).toString(),
            "platform", if (supportedCodecs?.contains("av1", true) == true) "web" else null,
            "sig", accessToken?.signature,
            "supported_codecs", supportedCodecs,
            "token", accessToken?.token,
        )
    }

    suspend fun loadVideoPlaylist(gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, enableIntegrity: Boolean): Response<ResponseBody> = withContext(Dispatchers.IO) {
        val accessToken = loadVideoPlaybackAccessToken(gqlHeaders, videoId, playerType, enableIntegrity)
        val playlistQueryOptions = HashMap<String, String>().apply {
            put("allow_source", "true")
            put("allow_audio_only", "true")
            put("p", Random.nextInt(9999999).toString())
            accessToken?.signature?.let { put("sig", it) }
            accessToken?.token?.let { put("token", it) }
        }
        usher.getVideoPlaylist(videoId, playlistQueryOptions)
    }

    private suspend fun loadVideoPlaybackAccessToken(gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, enableIntegrity: Boolean): PlaybackAccessToken? {
        val accessTokenHeaders = getPlaybackAccessTokenHeaders(gqlHeaders = gqlHeaders, randomDeviceId = true, enableIntegrity = enableIntegrity)
        return graphQL.loadPlaybackAccessToken(
            headers = accessTokenHeaders,
            vodId = videoId,
            playerType = playerType
        ).videoToken
    }

    private fun getPlaybackAccessTokenHeaders(gqlHeaders: Map<String, String>, randomDeviceId: Boolean?, xDeviceId: String? = null, enableIntegrity: Boolean): Map<String, String> {
        return if (enableIntegrity) {
            gqlHeaders
        } else {
            gqlHeaders.toMutableMap().apply {
                if (randomDeviceId != false) {
                    val randomId = UUID.randomUUID().toString().replace("-", "").substring(0, 32) //X-Device-Id or Device-ID removes "commercial break in progress" (length 16 or 32)
                    put("X-Device-Id", randomId)
                } else {
                    xDeviceId?.let { put("X-Device-Id", it) }
                }
            }
        }
    }

    private fun buildUrl(url: String, vararg queryParams: String?): Uri {
        val stringBuilder = StringBuilder(url)
        stringBuilder.append(queryParams[0])
            .append("=")
            .append(URLEncoder.encode(queryParams[1], Charsets.UTF_8.name()))
        for (i in 2 until queryParams.size step 2) {
            val value = queryParams[i + 1]
            if (!value.isNullOrBlank()) {
                stringBuilder.append("&")
                    .append(queryParams[i])
                    .append("=")
                    .append(URLEncoder.encode(value, Charsets.UTF_8.name()))
            }
        }
        return stringBuilder.toString().toUri()
    }

    suspend fun loadRecentMessages(channelLogin: String, limit: String): Response<RecentMessagesResponse> = withContext(Dispatchers.IO) {
        misc.getRecentMessages(channelLogin, limit)
    }

    suspend fun loadGlobalStvEmotes(): Response<StvGlobalResponse> = withContext(Dispatchers.IO) {
        misc.getGlobalStvEmotes()
    }

    suspend fun loadStvEmotes(channelId: String): Response<StvChannelResponse> = withContext(Dispatchers.IO) {
        misc.getStvEmotes(channelId)
    }

    suspend fun loadGlobalBttvEmotes(): Response<BttvGlobalResponse> = withContext(Dispatchers.IO) {
        misc.getGlobalBttvEmotes()
    }

    suspend fun loadBttvEmotes(channelId: String): Response<BttvChannelResponse> = withContext(Dispatchers.IO) {
        misc.getBttvEmotes(channelId)
    }

    suspend fun loadGlobalFfzEmotes(): Response<FfzGlobalResponse> = withContext(Dispatchers.IO) {
        misc.getGlobalFfzEmotes()
    }

    suspend fun loadFfzEmotes(channelId: String): Response<FfzChannelResponse> = withContext(Dispatchers.IO) {
        misc.getFfzEmotes(channelId)
    }

    fun loadRecentEmotes() = recentEmotes.getAll()

    suspend fun insertRecentEmotes(emotes: Collection<RecentEmote>) = withContext(Dispatchers.IO) {
        val listSize = emotes.size
        val list = if (listSize <= RecentEmote.MAX_SIZE) {
            emotes
        } else {
            emotes.toList().subList(listSize - RecentEmote.MAX_SIZE, listSize)
        }
        recentEmotes.ensureMaxSizeAndInsert(list)
    }

    fun loadVideoPositions(): LiveData<Map<Long, Long>> = videoPositions.getAll().map { list ->
        list.associate { it.id to it.position }
    }

    fun saveVideoPosition(position: VideoPosition) {
        GlobalScope.launch {
            videoPositions.insert(position)
        }
    }

    suspend fun deleteVideoPositions() = withContext(Dispatchers.IO) {
        videoPositions.deleteAll()
    }
}