package com.github.andreyasadchy.xtra.repository

import android.util.Base64
import androidx.core.util.Pair
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesDataResponse
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.type.BadgeImageSize
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
    private val misc: MiscApi) {

    suspend fun loadGameBoxArt(gameId: String, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryGameBoxArt(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.gameboxart).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", gameId)
                }).boxArtURL
        } catch (e: Exception) {
            helix.getGames(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = listOf(gameId)
            ).data.firstOrNull()?.boxArt
        }
    }

    suspend fun loadStream(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUsersStream(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.usersstream).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    val idArray = JsonArray()
                    if (!channelId.isNullOrBlank()) idArray.add(channelId)
                    add("id", idArray)
                    val loginArray = JsonArray()
                    if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) loginArray.add(channelLogin)
                    add("login", loginArray)
                }).data.firstOrNull()
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            try {
                helix.getStreams(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    ids = channelId?.let { listOf(it) },
                    logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
                ).data.firstOrNull()
            } catch (e: Exception) {
                gql.loadViewerCount(gqlHeaders, channelLogin).data
            }
        }
    }

    suspend fun loadVideo(videoId: String?, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, checkIntegrity: Boolean = false): Video? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryVideo(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.video).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", videoId)
                }).data
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            helix.getVideos(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = videoId?.let { listOf(it) }
            ).data.firstOrNull()
        }
    }

    suspend fun loadVideos(ids: List<String>, helixClientId: String?, helixToken: String?): List<Video>? = withContext(Dispatchers.IO) {
        helix.getVideos(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            ids = ids
        ).data
    }

    suspend fun loadClip(clipId: String?, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): Clip? = withContext(Dispatchers.IO) {
        try {
            val user = try {
                gql.loadClipData(gqlHeaders, clipId).data
            } catch (e: Exception) {
                null
            }
            val video = gql.loadClipVideo(gqlHeaders, clipId).data
            Clip(id = clipId, channelId = user?.channelId, channelLogin = user?.channelLogin, channelName = user?.channelName,
                profileImageUrl = user?.profileImageUrl, videoId = video?.videoId, duration = video?.duration, vodOffset = video?.vodOffset ?: user?.vodOffset)
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            helix.getClips(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = clipId?.let { listOf(it) }
            ).data.firstOrNull()
        }
    }

    suspend fun loadUserChannelPage(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserChannelPage(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.userchannelpage).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                }).data
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            helix.getStreams(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()
        }
    }

    suspend fun loadUser(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?): User? = withContext(Dispatchers.IO) {
        helix.getUsers(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            ids = channelId?.let { listOf(it) },
            logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
        ).data.firstOrNull()
    }

    suspend fun loadCheckUser(channelId: String? = null, channelLogin: String? = null, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): User? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUser(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.user).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                }).data
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()
        }
    }

    suspend fun loadUserMessageClicked(channelId: String? = null, channelLogin: String? = null, targetId: String?, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): User? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserMessageClicked(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.usermessageclicked).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                    addProperty("targetId", targetId)
                }).data
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()
        }
    }

    suspend fun loadUserTypes(ids: List<String>, helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>): List<User>? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUsersType(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.userstype).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    val idArray = JsonArray()
                    ids.forEach {
                        idArray.add(it)
                    }
                    add("ids", idArray)
                }).data
        } catch (e: Exception) {
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = ids
            ).data
        }
    }

    suspend fun loadGlobalBadges(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, emoteQuality: String, checkIntegrity: Boolean): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryBadges(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.badges).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("quality", (when (emoteQuality) {"4" -> BadgeImageSize.QUADRUPLE "3" -> BadgeImageSize.QUADRUPLE "2" -> BadgeImageSize.DOUBLE else -> BadgeImageSize.NORMAL}).toString())
                }).data
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            try {
                gql.loadChatBadges(gqlHeaders, "").global
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") throw e
                helix.getGlobalBadges(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }
                ).data
            }
        }
    }

    suspend fun loadChannelBadges(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, checkIntegrity: Boolean): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserBadges(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.userbadges).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                    addProperty("quality", (when (emoteQuality) {"4" -> BadgeImageSize.QUADRUPLE "3" -> BadgeImageSize.QUADRUPLE "2" -> BadgeImageSize.DOUBLE else -> BadgeImageSize.NORMAL}).toString())
                }).data
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            try {
                gql.loadChatBadges(gqlHeaders, channelLogin).channel
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") throw e
                helix.getChannelBadges(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    userId = channelId
                ).data
            }
        }
    }

    suspend fun loadCheerEmotes(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, animateGifs: Boolean, checkIntegrity: Boolean): List<CheerEmote> = withContext(Dispatchers.IO) {
        try {
            val emotes = mutableListOf<CheerEmote>()
            val context = XtraApp.INSTANCE.applicationContext
            val get = gql.loadQueryUserCheerEmotes(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.usercheeremotes).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                })
            val background = (get.config.backgrounds.find { it == "dark" } ?: get.config.backgrounds.lastOrNull())
            val format = if (animateGifs) {
                get.config.types.entries.find { it.key == "animated" } ?: get.config.types.entries.find { it.key == "static" }
            } else {
                get.config.types.entries.find { it.key == "static" }
            } ?: get.config.types.entries.lastOrNull()
            get.tiers.forEach { tier ->
                get.config.colors.entries.find { it.key == tier.tierBits }?.let { colorMap ->
                    val url = tier.template.apply {
                        replaceFirst("PREFIX", tier.prefix)
                        replaceFirst("TIER", colorMap.key.toString())
                        background?.let { replaceFirst("BACKGROUND", it) }
                        format?.let {
                            replaceFirst("ANIMATION", it.key)
                            replaceFirst("EXTENSION", it.value)
                        }
                    }
                    emotes.add(CheerEmote(
                        name = tier.prefix,
                        url1x = url.apply { (get.config.scales.find { it.startsWith("1") } ?: get.config.scales.lastOrNull())?.let { replaceFirst("SCALE", it) } },
                        url2x = get.config.scales.find { it.startsWith("2") }?.let { url.replaceFirst("SCALE", it) },
                        url3x = get.config.scales.find { it.startsWith("3") }?.let { url.replaceFirst("SCALE", it) },
                        url4x = get.config.scales.find { it.startsWith("4") }?.let { url.replaceFirst("SCALE", it) },
                        format = if (format?.key == "animated") "gif" else null,
                        isAnimated = format?.key == "animated",
                        minBits = colorMap.key,
                        color = colorMap.value
                    ))
                }
            }
            emotes
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            try {
                gql.loadCheerEmotes(gqlHeaders, channelLogin, animateGifs)
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") throw e
                val data = mutableListOf<CheerEmote>()
                helix.getCheerEmotes(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    userId = channelId
                ).data.filter { it.theme == "dark" && if (animateGifs) it.format == "animated" else it.format != "animated" }.forEach { emote ->
                    data.add(CheerEmote(
                        name = emote.name,
                        url1x = emote.urls["1"],
                        url2x = emote.urls["2"],
                        url3x = emote.urls["3"],
                        url4x = emote.urls["4"],
                        format = if (emote.format == "animated") "gif" else null,
                        isAnimated = emote.format == "animated",
                        minBits = emote.minBits,
                        color = emote.color
                    ))
                }
                data
            }
        }
    }

    suspend fun loadUserEmotes(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, userId: String?, animateGifs: Boolean, checkIntegrity: Boolean): List<TwitchEmote> = withContext(Dispatchers.IO) {
        try {
            if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
            val emotes = mutableListOf<TwitchEmote>()
            var offset: String? = null
            do {
                val get = gql.loadUserEmotes(gqlHeaders, channelId, offset)
                offset = get.cursor
                get.data.let { emotes.addAll(it) }
            } while (!get.cursor.isNullOrBlank() && get.hasNextPage == true)
            emotes
        } catch (e: Exception) {
            if (checkIntegrity && e.message == "failed integrity check") throw e
            try {
                if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                val context = XtraApp.INSTANCE.applicationContext
                gql.loadQueryUserEmotes(
                    headers = gqlHeaders,
                    query = context.resources.openRawResource(R.raw.useremotes).bufferedReader().use { it.readText() },
                    variables = JsonObject()).data
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") throw e
                val emotes = mutableListOf<TwitchEmote>()
                var offset: String? = null
                do {
                    val get = helix.getUserEmotes(
                        clientId = helixClientId,
                        token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                        userId = userId,
                        channelId = channelId,
                        offset = offset
                    )
                    offset = get.cursor
                    get.data.forEach { emote ->
                        val format = (if (animateGifs) {
                            emote.formats.find { it == "animated" } ?: emote.formats.find { it == "static" }
                        } else {
                            emote.formats.find { it == "static" }
                        } ?: emote.formats.first())
                        val theme = (emote.themes.find { it == "dark" } ?: emote.themes.last())
                        val url = emote.template
                            .replaceFirst("{{id}}", emote.id)
                            .replaceFirst("{{format}}", format)
                            .replaceFirst("{{theme_mode}}", theme)
                        emotes.add(TwitchEmote(
                            name = emote.name,
                            url1x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                            url2x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("2") } ?: emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                            url3x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("3") } ?: emote.scales.find { it.startsWith("2") } ?: emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                            url4x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("3") } ?: emote.scales.find { it.startsWith("2") } ?: emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                            format = if (format == "animated") "gif" else null,
                            setId = emote.setId,
                            ownerId = emote.ownerId
                        ))
                    }
                } while (!get.cursor.isNullOrBlank())
                emotes
            }
        }
    }

    suspend fun loadEmotesFromSet(helixClientId: String?, helixToken: String?, setIds: List<String>, animateGifs: Boolean): List<TwitchEmote> = withContext(Dispatchers.IO) {
        val data = mutableListOf<TwitchEmote>()
        helix.getEmotesFromSet(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            setIds = setIds
        ).data.forEach { emote ->
            val format = (if (animateGifs) {
                emote.formats.find { it == "animated" } ?: emote.formats.find { it == "static" }
            } else {
                emote.formats.find { it == "static" }
            } ?: emote.formats.first())
            val theme = (emote.themes.find { it == "dark" } ?: emote.themes.last())
            val url = emote.template
                .replaceFirst("{{id}}", emote.id)
                .replaceFirst("{{format}}", format)
                .replaceFirst("{{theme_mode}}", theme)
            data.add(TwitchEmote(
                name = emote.name,
                url1x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                url2x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("2") } ?: emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                url3x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("3") } ?: emote.scales.find { it.startsWith("2") } ?: emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                url4x = url.replaceFirst("{{scale}}", (emote.scales.find { it.startsWith("3") } ?: emote.scales.find { it.startsWith("2") } ?: emote.scales.find { it.startsWith("1") } ?: emote.scales.last())),
                format = if (format == "animated") "gif" else null,
                setId = emote.setId,
                ownerId = emote.ownerId
            ))
        }
        data
    }

    suspend fun loadUserFollowing(helixClientId: String?, helixToken: String?, targetId: String?, userId: String?, gqlHeaders: Map<String, String>, targetLogin: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gql.loadFollowingUser(gqlHeaders, targetLogin).following else throw Exception()
        } catch (e: Exception) {
            helix.getUserFollows(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                targetId = targetId,
                userId = userId
            ).data.firstOrNull()?.channelId == targetId
        }
    }

    suspend fun loadGameFollowing(gqlHeaders: Map<String, String>, gameName: String?): Boolean = withContext(Dispatchers.IO) {
        gql.loadFollowingGame(gqlHeaders, gameName).following
    }

    suspend fun loadVideoMessages(gqlHeaders: Map<String, String>, videoId: String, offset: Int? = null, cursor: String? = null): VideoMessagesDataResponse = withContext(Dispatchers.IO) {
        gql.loadVideoMessages(gqlHeaders, videoId, offset, cursor)
    }

    suspend fun loadVideoGames(gqlHeaders: Map<String, String>, videoId: String?): List<Game> = withContext(Dispatchers.IO) {
        gql.loadVideoGames(gqlHeaders, videoId).data
    }

    suspend fun loadChannelViewerList(gqlHeaders: Map<String, String>, channelLogin: String?): ChannelViewerList = withContext(Dispatchers.IO) {
        gql.loadChannelViewerList(gqlHeaders, channelLogin).data
    }

    suspend fun loadClaimPoints(gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?) = withContext(Dispatchers.IO) {
        val claimId = gql.loadChannelPointsContext(gqlHeaders, channelLogin).availableClaimId
        gql.loadClaimPoints(gqlHeaders, channelId, claimId).also { response ->
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
            }
        }
    }

    suspend fun loadJoinRaid(gqlHeaders: Map<String, String>, raidId: String?) = withContext(Dispatchers.IO) {
        gql.loadJoinRaid(gqlHeaders, raidId).also { response ->
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
            }
        }
    }

    suspend fun loadMinuteWatched(userId: String?, streamId: String?, channelId: String?, channelLogin: String?) = withContext(Dispatchers.IO) {
        try {
            val pageResponse = channelLogin?.let { misc.getChannelPage(it).string() }
            if (!pageResponse.isNullOrBlank()) {
                val settingsRegex = Regex("(https://assets.twitch.tv/config/settings.*?.js|https://static.twitchcdn.net/config/settings.*?js)")
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
                                addProperty("user_id", userId?.toLong())
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

    suspend fun followUser(gqlHeaders: Map<String, String>, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowUser(gqlHeaders, userId).error
    }

    suspend fun unfollowUser(gqlHeaders: Map<String, String>, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowUser(gqlHeaders, userId).error
    }

    suspend fun followGame(gqlHeaders: Map<String, String>, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowGame(gqlHeaders, gameId).error
    }

    suspend fun unfollowGame(gqlHeaders: Map<String, String>, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowGame(gqlHeaders, gameId).error
    }

    suspend fun createChatEventSubSubscription(helixClientId: String?, helixToken: String?, userId: String?, channelId: String?, type: String?, sessionId: String?): String? = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            addProperty("type", type)
            addProperty("version", "1")
            add("condition", JsonObject().apply {
                addProperty("broadcaster_user_id", channelId)
                addProperty("user_id", userId)
            })
            add("transport", JsonObject().apply {
                addProperty("method", "websocket")
                addProperty("session_id", sessionId)
            })
        }
        val response = helix.createEventSubSubscription(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun sendMessage(helixClientId: String?, helixToken: String?, userId: String?, channelId: String?, message: String?): String? = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            addProperty("broadcaster_id", channelId)
            addProperty("sender_id", userId)
            addProperty("message", message)
        }
        val response = helix.sendMessage(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun sendAnnouncement(helixClientId: String?, helixToken: String?, userId: String?, gqlHeaders: Map<String, String>, channelId: String?, message: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.sendAnnouncement(gqlHeaders, channelId, message, color).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val json = JsonObject().apply {
                addProperty("message", message)
                color?.let { addProperty("color", it) }
            }
            helix.sendAnnouncement(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, userId, json)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun banUser(helixClientId: String?, helixToken: String?, userId: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?, duration: String? = null, reason: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.banUser(gqlHeaders, channelId, targetLogin, duration, reason).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            val json = JsonObject().apply {
                add("data", JsonObject().apply {
                    duration?.toIntOrNull()?.let { addProperty("duration", it) }
                    addProperty("reason", reason)
                    addProperty("user_id", targetId)
                })
            }
            helix.banUser(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, userId, json)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun unbanUser(helixClientId: String?, helixToken: String?, userId: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.unbanUser(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.unbanUser(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, userId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun deleteMessages(helixClientId: String?, helixToken: String?, channelId: String?, userId: String?, messageId: String? = null): String? = withContext(Dispatchers.IO) {
        val response = helix.deleteMessages(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, userId, messageId)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun updateChatColor(helixClientId: String?, helixToken: String?, userId: String?, gqlHeaders: Map<String, String>, color: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.updateChatColor(gqlHeaders, color).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            helix.updateChatColor(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, userId, color)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getChatColor(helixClientId: String?, helixToken: String?, userId: String?): String? = withContext(Dispatchers.IO) {
        val response = helix.getChatColor(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, userId)
        if (response.isSuccessful) {
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.first()?.takeIf { it.isJsonObject }?.asJsonObject?.get("color")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun startCommercial(helixClientId: String?, helixToken: String?, channelId: String?, length: String?): String? = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            addProperty("broadcaster_id", channelId)
            addProperty("length", length?.toIntOrNull())
        }
        val response = helix.startCommercial(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, json)
        if (response.isSuccessful) {
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.first()?.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun updateChatSettings(helixClientId: String?, helixToken: String?, channelId: String?, userId: String?, emote: Boolean? = null, followers: Boolean? = null, followersDuration: String? = null, slow: Boolean? = null, slowDuration: Int? = null, subs: Boolean? = null, unique: Boolean? = null): String? = withContext(Dispatchers.IO) {
        val json = JsonObject().apply {
            emote?.let { addProperty("emote_mode", it) }
            followers?.let { addProperty("follower_mode", it) }
            followersDuration?.toIntOrNull()?.let { addProperty("follower_mode_duration", it) }
            slow?.let { addProperty("slow_mode", it) }
            slowDuration?.let { addProperty("slow_mode_wait_time", it) }
            subs?.let { addProperty("subscriber_mode", it) }
            unique?.let { addProperty("unique_chat_mode", it) }
        }
        val response = helix.updateChatSettings(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, userId, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun createStreamMarker(helixClientId: String?, helixToken: String?, channelId: String?, gqlHeaders: Map<String, String>, channelLogin: String?, description: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.createStreamMarker(gqlHeaders, channelLogin).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val json = JsonObject().apply {
                addProperty("user_id", channelId)
                description?.let { addProperty("description", it) }
            }
            helix.createStreamMarker(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, json)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getModerators(gqlHeaders: Map<String, String>, channelLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = gql.getModerators(gqlHeaders, channelLogin)
        if (response.isSuccessful) {
            response.body()?.data?.map { it.channelLogin }.toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun addModerator(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.addModerator(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.addModerator(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun removeModerator(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.removeModerator(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.removeModerator(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun startRaid(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?, checkIntegrity: Boolean): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            val targetId = loadCheckUser(
                channelLogin = targetLogin,
                helixClientId = helixClientId,
                helixToken = helixToken,
                gqlHeaders = gqlHeaders,
                checkIntegrity = checkIntegrity
            )?.channelId
            gql.startRaid(gqlHeaders, channelId, targetId).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.startRaid(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun cancelRaid(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.cancelRaid(gqlHeaders, channelId).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            helix.cancelRaid(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId)
        }
        if (response.isSuccessful) {
            response.body().toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getVips(gqlHeaders: Map<String, String>, channelLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = gql.getVips(gqlHeaders, channelLogin)
        if (response.isSuccessful) {
            response.body()?.data?.map { it.channelLogin }.toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun addVip(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.addVip(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.addVip(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun removeVip(helixClientId: String?, helixToken: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.removeVip(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
                    item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
                }
            }
        } else {
            val targetId = helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.removeVip(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun sendWhisper(helixClientId: String?, helixToken: String?, userId: String?, targetLogin: String?, message: String?): String? = withContext(Dispatchers.IO) {
        val targetId = helix.getUsers(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            logins = targetLogin?.let { listOf(it) }
        ).data.firstOrNull()?.channelId
        val json = JsonObject().apply {
            addProperty("message", message)
        }
        val response = helix.sendWhisper(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, userId, targetId, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun loadUserResult(channelId: String? = null, channelLogin: String? = null, gqlHeaders: Map<String, String>): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        if (!channelId.isNullOrBlank()) {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserResultID(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.userresultid).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", channelId)
                }).data
        } else if (!channelLogin.isNullOrBlank()) {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserResultLogin(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.userresultlogin).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("login", channelLogin)
                }).data
        } else null
    }
}