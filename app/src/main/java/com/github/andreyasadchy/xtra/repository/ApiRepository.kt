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
            ).data.firstOrNull()?.boxArt
        }
    }

    suspend fun loadStream(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUsersStream(
                clientId = gqlClientId,
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
            try {
                helix.getStreams(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    ids = channelId?.let { listOf(it) },
                    logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
                ).data.firstOrNull()
            } catch (e: Exception) {
                gql.loadViewerCount(gqlClientId, channelLogin).data
            }
        }
    }

    suspend fun loadVideo(videoId: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): Video? = withContext(Dispatchers.IO) {
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

    suspend fun loadClip(clipId: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): Clip? = withContext(Dispatchers.IO) {
        try {
            val user = try {
                gql.loadClipData(gqlClientId, clipId).data
            } catch (e: Exception) {
                null
            }
            val video = gql.loadClipVideo(gqlClientId, clipId).data
            Clip(id = clipId, channelId = user?.channelId, channelLogin = user?.channelLogin, channelName = user?.channelName,
                profileImageUrl = user?.profileImageUrl, videoId = video?.videoId, duration = video?.duration, vodOffset = video?.vodOffset ?: user?.vodOffset)
        } catch (e: Exception) {
            helix.getClips(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = clipId?.let { listOf(it) }
            ).data.firstOrNull()
        }
    }

    suspend fun loadUserChannelPage(channelId: String?, channelLogin: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): Stream? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserChannelPage(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.userchannelpage).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                }).data
        } catch (e: Exception) {
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

    suspend fun loadCheckUser(channelId: String? = null, channelLogin: String? = null, helixClientId: String?, helixToken: String?, gqlClientId: String?): User? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUser(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.user).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                }).data
        } catch (e: Exception) {
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()
        }
    }

    suspend fun loadUserMessageClicked(channelId: String? = null, channelLogin: String? = null, targetId: String?, helixClientId: String?, helixToken: String?, gqlClientId: String?): User? = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserMessageClicked(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.usermessageclicked).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                    addProperty("targetId", targetId)
                }).data
        } catch (e: Exception) {
            helix.getUsers(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()
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

    suspend fun loadGlobalBadges(helixClientId: String?, helixToken: String?, gqlClientId: String?, emoteQuality: String): List<TwitchBadge> = withContext(Dispatchers.IO) {
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

    suspend fun loadChannelBadges(helixClientId: String?, helixToken: String?, gqlClientId: String?, channelId: String?, channelLogin: String?, emoteQuality: String): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserBadges(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.userbadges).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
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

    suspend fun loadCheerEmotes(helixClientId: String?, helixToken: String?, gqlClientId: String?, channelId: String?, channelLogin: String?, animateGifs: Boolean): List<CheerEmote> = withContext(Dispatchers.IO) {
        try {
            val emotes = mutableListOf<CheerEmote>()
            val context = XtraApp.INSTANCE.applicationContext
            val get = gql.loadQueryUserCheerEmotes(
                clientId = gqlClientId,
                query = context.resources.openRawResource(R.raw.usercheeremotes).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                    addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                })
            get.config.let { config ->
                config.backgrounds.let {
                    val background = (config.backgrounds.find { it.asString == "dark" } ?: config.backgrounds.last()).asString
                    config.colors.let {
                        val colors = config.colors
                        config.scales.let {
                            val scale = config.scales
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
                                                .replaceFirst("EXTENSION", type.extension)
                                            emotes.add(CheerEmote(
                                                name = tier.prefix,
                                                url1x = (scale.find { it.asString.startsWith("1") })?.let { url.replaceFirst("SCALE", it.asString) } ?: scale.last().asString,
                                                url2x = (scale.find { it.asString.startsWith("2") })?.let { url.replaceFirst("SCALE", it.asString) },
                                                url3x = (scale.find { it.asString.startsWith("3") })?.let { url.replaceFirst("SCALE", it.asString) },
                                                url4x = (scale.find { it.asString.startsWith("4") })?.let { url.replaceFirst("SCALE", it.asString) },
                                                type = if (type.animation == "animated") "gif" else null,
                                                isAnimated = type.animation == "animated",
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
                gql.loadCheerEmotes(gqlClientId, channelLogin, animateGifs)
            } catch (e: Exception) {
                val data = mutableListOf<CheerEmote>()
                helix.getCheerEmotes(
                    clientId = helixClientId,
                    token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                    userId = channelId
                ).data.forEach { emote ->
                    val urls = if (animateGifs) { emote.animated ?: emote.static } else { emote.static }
                    data.add(CheerEmote(
                        name = emote.name,
                        url1x = urls?.get("1")?.takeIf { !it.isJsonNull }?.asString,
                        url2x = urls?.get("2")?.takeIf { !it.isJsonNull }?.asString,
                        url3x = urls?.get("3")?.takeIf { !it.isJsonNull }?.asString,
                        url4x = urls?.get("4")?.takeIf { !it.isJsonNull }?.asString,
                        type = if (urls == emote.animated) "gif" else null,
                        isAnimated = urls == emote.animated,
                        minBits = emote.minBits,
                        color = emote.color
                    ))
                }
                data
            }
        }
    }

    suspend fun loadUserEmotes(gqlClientId: String?, gqlToken: String?, channelId: String?): List<TwitchEmote> = withContext(Dispatchers.IO) {
        try {
            gql.loadUserEmotes(gqlClientId, gqlToken, channelId).data
        } catch (e: Exception) {
            val context = XtraApp.INSTANCE.applicationContext
            gql.loadQueryUserEmotes(
                clientId = gqlClientId,
                token = gqlToken,
                query = context.resources.openRawResource(R.raw.useremotes).bufferedReader().use { it.readText() },
                variables = JsonObject()).data
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
                emote.format.find { it.asString == "animated" } ?: emote.format.find { it.asString == "static" }
            } else {
                emote.format.find { it.asString == "static" }
            } ?: emote.format.first()).asString
            val theme = (emote.theme.find { it.asString == "dark" } ?: emote.theme.last()).asString
            val url = emote.template
                .replaceFirst("{{id}}", emote.id)
                .replaceFirst("{{format}}", format)
                .replaceFirst("{{theme_mode}}", theme)
            data.add(TwitchEmote(
                name = emote.name,
                url1x = url.replaceFirst("{{scale}}", (emote.scale.find { it.asString.startsWith("1") } ?: emote.scale.last()).asString),
                url2x = url.replaceFirst("{{scale}}", (emote.scale.find { it.asString.startsWith("2") } ?: emote.scale.find { it.asString.startsWith("1") } ?: emote.scale.last()).asString),
                url3x = url.replaceFirst("{{scale}}", (emote.scale.find { it.asString.startsWith("3") } ?: emote.scale.find { it.asString.startsWith("2") } ?: emote.scale.find { it.asString.startsWith("1") } ?: emote.scale.last()).asString),
                url4x = url.replaceFirst("{{scale}}", (emote.scale.find { it.asString.startsWith("3") } ?: emote.scale.find { it.asString.startsWith("2") } ?: emote.scale.find { it.asString.startsWith("1") } ?: emote.scale.last()).asString),
                type = if (format == "animated") "gif" else null,
                setId = emote.setId,
                ownerId = emote.ownerId
            ))
        }
        data
    }

    suspend fun loadUserFollowing(helixClientId: String?, helixToken: String?, targetId: String?, userId: String?, gqlClientId: String?, gqlToken: String?, targetLogin: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!gqlToken.isNullOrBlank()) gql.loadFollowingUser(gqlClientId, gqlToken, targetLogin).following else throw Exception()
        } catch (e: Exception) {
            helix.getUserFollows(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                targetId = targetId,
                userId = userId
            ).data.firstOrNull()?.channelId == targetId
        }
    }

    suspend fun loadGameFollowing(gqlClientId: String?, gqlToken: String?, gameName: String?): Boolean = withContext(Dispatchers.IO) {
        gql.loadFollowingGame(gqlClientId, gqlToken, gameName).following
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

    suspend fun loadClaimPoints(gqlClientId: String?, gqlToken: String?, channelId: String?, channelLogin: String?) = withContext(Dispatchers.IO) {
        try {
            val claimId = gql.loadChannelPointsContext(gqlClientId, gqlToken, channelLogin).availableClaimId
            gql.loadClaimPoints(gqlClientId, gqlToken, channelId, claimId)
        } catch (e: Exception) {

        }
    }

    suspend fun loadJoinRaid(gqlClientId: String?, gqlToken: String?, raidId: String?) = withContext(Dispatchers.IO) {
        gql.loadJoinRaid(gqlClientId, gqlToken, raidId)
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

    suspend fun followUser(gqlClientId: String?, gqlToken: String?, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowUser(gqlClientId, gqlToken, userId).error
    }

    suspend fun unfollowUser(gqlClientId: String?, gqlToken: String?, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowUser(gqlClientId, gqlToken, userId).error
    }

    suspend fun followGame(gqlClientId: String?, gqlToken: String?, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowGame(gqlClientId, gqlToken, gameId).error
    }

    suspend fun unfollowGame(gqlClientId: String?, gqlToken: String?, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowGame(gqlClientId, gqlToken, gameId).error
    }

    suspend fun sendAnnouncement(helixClientId: String?, helixToken: String?, userId: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, message: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.sendAnnouncement(gqlClientId, gqlToken, channelId, message, color)
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

    suspend fun banUser(helixClientId: String?, helixToken: String?, userId: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?, duration: String? = null, reason: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.banUser(gqlClientId, gqlToken, channelId, targetLogin, duration, reason)
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

    suspend fun unbanUser(helixClientId: String?, helixToken: String?, userId: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.unbanUser(gqlClientId, gqlToken, channelId, targetLogin)
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

    suspend fun updateChatColor(helixClientId: String?, helixToken: String?, userId: String?, gqlClientId: String?, gqlToken: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.updateChatColor(gqlClientId, gqlToken, color)
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
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.first()?.takeIf { it.isJsonObject }?.asJsonObject?.get("color")?.takeIf { !it.isJsonNull }?.asString
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
            response.body()?.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.first()?.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { !it.isJsonNull }?.asString
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

    suspend fun createStreamMarker(helixClientId: String?, helixToken: String?, channelId: String?, gqlClientId: String?, gqlToken: String?, channelLogin: String?, description: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.createStreamMarker(gqlClientId, gqlToken, channelLogin)
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

    suspend fun getModerators(helixClientId: String?, helixToken: String?, channelId: String?, gqlClientId: String?, channelLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = gql.getModerators(gqlClientId, channelLogin)
        //val response = helix.getModerators(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, null, null)
        if (response.isSuccessful) {
            response.body()?.data?.map { it.channelLogin }.toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun addModerator(helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.addModerator(gqlClientId, gqlToken, channelId, targetLogin)
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

    suspend fun removeModerator(helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.removeModerator(gqlClientId, gqlToken, channelId, targetLogin)
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

    suspend fun startRaid(helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            val targetId = loadCheckUser(
                channelLogin = targetLogin,
                helixClientId = helixClientId,
                helixToken = helixToken,
                gqlClientId = gqlClientId
            )?.channelId
            gql.startRaid(gqlClientId, gqlToken, channelId, targetId)
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

    suspend fun cancelRaid(helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, channelId: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.cancelRaid(gqlClientId, gqlToken, channelId)
        } else {
            helix.cancelRaid(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId)
        }
        if (response.isSuccessful) {
            response.body().toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getVips(helixClientId: String?, helixToken: String?, channelId: String?, gqlClientId: String?, channelLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = gql.getVips(gqlClientId, channelLogin)
        //val response = helix.getVips(helixClientId, helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, channelId, null, null)
        if (response.isSuccessful) {
            response.body()?.data?.map { it.channelLogin }.toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun addVip(helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.addVip(gqlClientId, gqlToken, channelId, targetLogin)
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

    suspend fun removeVip(helixClientId: String?, helixToken: String?, gqlClientId: String?, gqlToken: String?, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlToken.isNullOrBlank()) {
            gql.removeVip(gqlClientId, gqlToken, channelId, targetLogin)
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

    suspend fun loadUserResult(channelId: String? = null, channelLogin: String? = null, gqlClientId: String?): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        try {
            if (!channelId.isNullOrBlank()) {
                val context = XtraApp.INSTANCE.applicationContext
                gql.loadQueryUserResultID(
                    clientId = gqlClientId,
                    query = context.resources.openRawResource(R.raw.userresultid).bufferedReader().use { it.readText() },
                    variables = JsonObject().apply {
                        addProperty("id", channelId)
                    }).data
            } else if (!channelLogin.isNullOrBlank()) {
                val context = XtraApp.INSTANCE.applicationContext
                gql.loadQueryUserResultLogin(
                    clientId = gqlClientId,
                    query = context.resources.openRawResource(R.raw.userresultlogin).bufferedReader().use { it.readText() },
                    variables = JsonObject().apply {
                        addProperty("login", channelLogin)
                    }).data
            } else null
        } catch (e: Exception) {
            null
        }
    }
}