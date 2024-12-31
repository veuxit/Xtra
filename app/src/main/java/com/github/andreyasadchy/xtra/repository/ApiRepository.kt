package com.github.andreyasadchy.xtra.repository

import android.util.Base64
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.gql.video.VideoMessagesResponse
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.type.BadgeImageSize
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiRepository @Inject constructor(
    private val helix: HelixApi,
    private val gql: GraphQLRepository,
    private val misc: MiscApi,
) {

    suspend fun loadGameBoxArt(gameId: String, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            gql.loadQueryGameBoxArt(
                headers = gqlHeaders,
                id = gameId
            ).data!!.game?.boxArtURL
        } catch (e: Exception) {
            helix.getGames(
                headers = helixHeaders,
                ids = listOf(gameId)
            ).data.firstOrNull()?.boxArtUrl
        }
    }

    suspend fun loadStream(channelId: String?, channelLogin: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): Stream? = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryUsersStream(
                headers = gqlHeaders,
                id = if (!channelId.isNullOrBlank()) listOf(channelId) else null,
                login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) listOf(channelLogin) else null,
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.users?.firstOrNull()?.let {
                Stream(
                    id = it.stream?.id,
                    channelId = channelId,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    gameId = it.stream?.game?.id,
                    gameSlug = it.stream?.game?.slug,
                    gameName = it.stream?.game?.displayName,
                    type = it.stream?.type,
                    title = it.stream?.broadcaster?.broadcastSettings?.title,
                    viewerCount = it.stream?.viewersCount,
                    startedAt = it.stream?.createdAt?.toString(),
                    thumbnailUrl = it.stream?.previewImageURL,
                    profileImageUrl = it.profileImageURL,
                    tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name }
                )
            }
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            try {
                helix.getStreams(
                    headers = helixHeaders,
                    ids = channelId?.let { listOf(it) },
                    logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
                ).data.firstOrNull()?.let {
                    Stream(
                        id = it.id,
                        channelId = it.channelId,
                        channelLogin = it.channelLogin,
                        channelName = it.channelName,
                        gameId = it.gameId,
                        gameName = it.gameName,
                        type = it.type,
                        title = it.title,
                        viewerCount = it.viewerCount,
                        startedAt = it.startedAt,
                        thumbnailUrl = it.thumbnailUrl,
                        tags = it.tags
                    )
                }
            } catch (e: Exception) {
                val response = gql.loadViewerCount(gqlHeaders, channelLogin)
                if (checkIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                }
                response.data!!.user.stream?.let {
                    Stream(
                        id = it.id,
                        viewerCount = it.viewersCount
                    )
                }
            }
        }
    }

    suspend fun loadVideo(videoId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean = false): Video? = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryVideo(
                headers = gqlHeaders,
                id = videoId
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.let { item ->
                item.video?.let {
                    Video(
                        id = videoId,
                        channelId = it.owner?.id,
                        channelLogin = it.owner?.login,
                        channelName = it.owner?.displayName,
                        type = it.broadcastType?.toString(),
                        title = it.title,
                        uploadDate = it.createdAt?.toString(),
                        duration = it.lengthSeconds?.toString(),
                        thumbnailUrl = it.previewThumbnailURL,
                        profileImageUrl = it.owner?.profileImageURL,
                        animatedPreviewURL = it.animatedPreviewURL,
                    )
                }
            }
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            helix.getVideos(
                headers = helixHeaders,
                ids = videoId?.let { listOf(it) }
            ).data.firstOrNull()?.let {
                Video(
                    id = it.id,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.uploadDate,
                    duration = it.duration,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
        }
    }

    suspend fun loadVideos(ids: List<String>, helixHeaders: Map<String, String>): List<Video> = withContext(Dispatchers.IO) {
        helix.getVideos(
            headers = helixHeaders,
            ids = ids
        ).data.map {
            Video(
                id = it.id,
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                title = it.title,
                viewCount = it.viewCount,
                uploadDate = it.uploadDate,
                duration = it.duration,
                thumbnailUrl = it.thumbnailUrl,
            )
        }
    }

    suspend fun loadClip(clipId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): Clip? = withContext(Dispatchers.IO) {
        try {
            val user = try {
                gql.loadClipData(gqlHeaders, clipId).data?.clip
            } catch (e: Exception) {
                null
            }
            val response = gql.loadClipVideo(gqlHeaders, clipId)
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            val clip = response.data?.clip
            Clip(
                id = clipId,
                channelId = user?.broadcaster?.id,
                channelLogin = user?.broadcaster?.login,
                channelName = user?.broadcaster?.displayName,
                profileImageUrl = user?.broadcaster?.profileImageURL,
                videoId = clip?.video?.id,
                duration = clip?.durationSeconds,
                vodOffset = clip?.videoOffsetSeconds ?: user?.videoOffsetSeconds
            )
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            helix.getClips(
                headers = helixHeaders,
                ids = clipId?.let { listOf(it) }
            ).data.firstOrNull()?.let {
                Clip(
                    id = it.id,
                    channelId = it.channelId,
                    channelName = it.channelName,
                    videoId = it.videoId,
                    vodOffset = it.vodOffset,
                    gameId = it.gameId,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt,
                    duration = it.duration,
                    thumbnailUrl = it.thumbnailUrl,
                )
            }
        }
    }

    suspend fun loadUserChannelPage(channelId: String?, channelLogin: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): Stream? = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryUserChannelPage(
                headers = gqlHeaders,
                id = if (!channelId.isNullOrBlank()) channelId else null,
                login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null,
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.user?.let {
                Stream(
                    id = it.stream?.id,
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    gameId = it.stream?.game?.id,
                    gameSlug = it.stream?.game?.slug,
                    gameName = it.stream?.game?.displayName,
                    type = it.stream?.type,
                    title = it.stream?.title,
                    viewerCount = it.stream?.viewersCount,
                    startedAt = it.stream?.createdAt?.toString(),
                    thumbnailUrl = it.stream?.previewImageURL,
                    profileImageUrl = it.profileImageURL,
                    user = User(
                        channelId = it.id,
                        channelLogin = it.login,
                        channelName = it.displayName,
                        type = when {
                            it.roles?.isStaff == true -> "staff"
                            else -> null
                        },
                        broadcasterType = when {
                            it.roles?.isPartner == true -> "partner"
                            it.roles?.isAffiliate == true -> "affiliate"
                            else -> null
                        },
                        profileImageUrl = it.profileImageURL,
                        createdAt = it.createdAt?.toString(),
                        followersCount = it.followers?.totalCount,
                        bannerImageURL = it.bannerImageURL,
                        lastBroadcast = it.lastBroadcast?.startedAt?.toString()
                    )
                )
            }
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            helix.getStreams(
                headers = helixHeaders,
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()?.let {
                Stream(
                    id = it.id,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    gameId = it.gameId,
                    gameName = it.gameName,
                    type = it.type,
                    title = it.title,
                    viewerCount = it.viewerCount,
                    startedAt = it.startedAt,
                    thumbnailUrl = it.thumbnailUrl,
                    tags = it.tags
                )
            }
        }
    }

    suspend fun loadUser(channelId: String?, channelLogin: String?, helixHeaders: Map<String, String>): User? = withContext(Dispatchers.IO) {
        helix.getUsers(
            headers = helixHeaders,
            ids = channelId?.let { listOf(it) },
            logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
        ).data.firstOrNull()?.let {
            User(
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                type = it.type,
                broadcasterType = it.broadcasterType,
                profileImageUrl = it.profileImageUrl,
                createdAt = it.createdAt,
            )
        }
    }

    suspend fun loadCheckUser(channelId: String? = null, channelLogin: String? = null, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): User? = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryUser(
                headers = gqlHeaders,
                id = if (!channelId.isNullOrBlank()) channelId else null,
                login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null,
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.user?.let {
                User(
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    profileImageUrl = it.profileImageURL
                )
            }
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            helix.getUsers(
                headers = helixHeaders,
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()?.let {
                User(
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    type = it.type,
                    broadcasterType = it.broadcasterType,
                    profileImageUrl = it.profileImageUrl,
                    createdAt = it.createdAt,
                )
            }
        }
    }

    suspend fun loadUserMessageClicked(channelId: String? = null, channelLogin: String? = null, targetId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): User? = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryUserMessageClicked(
                headers = gqlHeaders,
                id = if (!channelId.isNullOrBlank()) channelId else null,
                login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null,
                targetId = targetId
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.user?.let {
                User(
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    profileImageUrl = it.profileImageURL,
                    bannerImageURL = it.bannerImageURL,
                    createdAt = it.createdAt?.toString(),
                    followedAt = it.follow?.followedAt?.toString()
                )
            }
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            helix.getUsers(
                headers = helixHeaders,
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null
            ).data.firstOrNull()?.let {
                User(
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    type = it.type,
                    broadcasterType = it.broadcasterType,
                    profileImageUrl = it.profileImageUrl,
                    createdAt = it.createdAt,
                )
            }
        }
    }

    suspend fun loadUserTypes(ids: List<String>, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>): List<User>? = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryUsersType(
                headers = gqlHeaders,
                ids = ids
            )
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            response.data!!.users?.mapNotNull {
                if (it != null) {
                    User(
                        channelId = it.id,
                        broadcasterType = when {
                            it.roles?.isPartner == true -> "partner"
                            it.roles?.isAffiliate == true -> "affiliate"
                            else -> null
                        },
                        type = when {
                            it.roles?.isStaff == true -> "staff"
                            else -> null
                        }
                    )
                } else null
            }
        } catch (e: Exception) {
            helix.getUsers(
                headers = helixHeaders,
                ids = ids
            ).data.map {
                User(
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    type = it.type,
                    broadcasterType = it.broadcasterType,
                    profileImageUrl = it.profileImageUrl,
                    createdAt = it.createdAt,
                )
            }
        }
    }

    suspend fun loadGlobalBadges(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, emoteQuality: String, checkIntegrity: Boolean): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryBadges(
                headers = gqlHeaders,
                quality = when (emoteQuality) {
                    "4" -> BadgeImageSize.QUADRUPLE
                    "3" -> BadgeImageSize.QUADRUPLE
                    "2" -> BadgeImageSize.DOUBLE
                    else -> BadgeImageSize.NORMAL
                }.toString()
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.badges?.mapNotNull {
                it?.setID?.let { setId ->
                    it.version?.let { version ->
                        it.imageURL?.let { url ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = url,
                                url2x = url,
                                url3x = url,
                                url4x = url,
                                title = it.title
                            )
                        }
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            try {
                val response = gql.loadChatBadges(gqlHeaders, "")
                if (checkIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                }
                response.data!!.badges?.mapNotNull {
                    it.setID?.let { setId ->
                        it.version?.let { version ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = it.image1x,
                                url2x = it.image2x,
                                url3x = it.image4x,
                                url4x = it.image4x,
                                title = it.title,
                            )
                        }
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                if (e.message == "failed integrity check") throw e
                helix.getGlobalBadges(
                    headers = helixHeaders
                ).data.mapNotNull { set ->
                    set.setId?.let { setId ->
                        set.versions?.mapNotNull {
                            it.id?.let { version ->
                                TwitchBadge(
                                    setId = setId,
                                    version = version,
                                    url1x = it.url1x,
                                    url2x = it.url2x,
                                    url3x = it.url4x,
                                    url4x = it.url4x
                                )
                            }
                        }
                    }
                }.flatten()
            }
        }
    }

    suspend fun loadChannelBadges(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, emoteQuality: String, checkIntegrity: Boolean): List<TwitchBadge> = withContext(Dispatchers.IO) {
        try {
            val response = gql.loadQueryUserBadges(
                headers = gqlHeaders,
                id = if (!channelId.isNullOrBlank()) channelId else null,
                login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null,
                quality = when (emoteQuality) {
                    "4" -> BadgeImageSize.QUADRUPLE
                    "3" -> BadgeImageSize.QUADRUPLE
                    "2" -> BadgeImageSize.DOUBLE
                    else -> BadgeImageSize.NORMAL
                }.toString()
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.user?.broadcastBadges?.mapNotNull {
                it?.setID?.let { setId ->
                    it.version?.let { version ->
                        it.imageURL?.let { url ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = url,
                                url2x = url,
                                url3x = url,
                                url4x = url,
                                title = it.title
                            )
                        }
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            try {
                val response = gql.loadChatBadges(gqlHeaders, channelLogin)
                if (checkIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                }
                response.data!!.badges?.mapNotNull {
                    it.setID?.let { setId ->
                        it.version?.let { version ->
                            TwitchBadge(
                                setId = setId,
                                version = version,
                                url1x = it.image1x,
                                url2x = it.image2x,
                                url3x = it.image4x,
                                url4x = it.image4x,
                                title = it.title,
                            )
                        }
                    }
                } ?: emptyList()
            } catch (e: Exception) {
                if (e.message == "failed integrity check") throw e
                helix.getChannelBadges(
                    headers = helixHeaders,
                    userId = channelId
                ).data.mapNotNull { set ->
                    set.setId?.let { setId ->
                        set.versions?.mapNotNull {
                            it.id?.let { version ->
                                TwitchBadge(
                                    setId = setId,
                                    version = version,
                                    url1x = it.url1x,
                                    url2x = it.url2x,
                                    url3x = it.url4x,
                                    url4x = it.url4x
                                )
                            }
                        }
                    }
                }.flatten()
            }
        }
    }

    suspend fun loadCheerEmotes(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, channelLogin: String?, animateGifs: Boolean, checkIntegrity: Boolean): List<CheerEmote> = withContext(Dispatchers.IO) {
        try {
            val emotes = mutableListOf<CheerEmote>()
            val response = gql.loadQueryUserCheerEmotes(
                headers = gqlHeaders,
                id = if (!channelId.isNullOrBlank()) channelId else null,
                login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null
            )
            if (checkIntegrity) {
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
            response.data!!.cheerConfig?.displayConfig?.let { config ->
                val background = config.backgrounds?.find { it == "dark" } ?: config.backgrounds?.lastOrNull() ?: ""
                val format = if (animateGifs) {
                    config.types?.find { it.animation == "animated" } ?: config.types?.find { it.animation == "static" }
                } else {
                    config.types?.find { it.animation == "static" }
                } ?: config.types?.lastOrNull()
                val scale1x = config.scales?.find { it.startsWith("1") } ?: config.scales?.lastOrNull() ?: ""
                val scale2x = config.scales?.find { it.startsWith("2") } ?: scale1x
                val scale3x = config.scales?.find { it.startsWith("3") } ?: scale2x
                val scale4x = config.scales?.find { it.startsWith("4") } ?: scale3x
                response.data!!.cheerConfig?.groups?.mapNotNull { group ->
                    group.nodes?.mapNotNull { emote ->
                        emote.tiers?.mapNotNull { tier ->
                            config.colors?.find { it.bits == tier?.bits }?.let { item ->
                                val url = group.templateURL!!
                                    .replaceFirst("PREFIX", emote.prefix!!.lowercase())
                                    .replaceFirst("TIER", item.bits!!.toString())
                                    .replaceFirst("BACKGROUND", background)
                                    .replaceFirst("ANIMATION", format?.animation ?: "")
                                    .replaceFirst("EXTENSION", format?.extension ?: "")
                                CheerEmote(
                                    name = emote.prefix,
                                    url1x = url.replaceFirst("SCALE", scale1x),
                                    url2x = url.replaceFirst("SCALE", scale2x),
                                    url3x = url.replaceFirst("SCALE", scale3x),
                                    url4x = url.replaceFirst("SCALE", scale4x),
                                    format = if (format?.animation == "animated") "gif" else null,
                                    isAnimated = format?.animation == "animated",
                                    minBits = item.bits,
                                    color = item.color
                                )
                            }
                        }
                    }?.flatten()
                }?.flatten()?.let { emotes.addAll(it) }
                response.data!!.user?.cheer?.cheerGroups?.mapNotNull { group ->
                    group.nodes?.mapNotNull { emote ->
                        emote.tiers?.mapNotNull { tier ->
                            config.colors?.find { it.bits == tier?.bits }?.let { item ->
                                val url = group.templateURL!!
                                    .replaceFirst("PREFIX", emote.prefix!!.lowercase())
                                    .replaceFirst("TIER", item.bits!!.toString())
                                    .replaceFirst("BACKGROUND", background)
                                    .replaceFirst("ANIMATION", format?.animation ?: "")
                                    .replaceFirst("EXTENSION", format?.extension ?: "")
                                CheerEmote(
                                    name = emote.prefix,
                                    url1x = url.replaceFirst("SCALE", scale1x),
                                    url2x = url.replaceFirst("SCALE", scale2x),
                                    url3x = url.replaceFirst("SCALE", scale3x),
                                    url4x = url.replaceFirst("SCALE", scale4x),
                                    format = if (format?.animation == "animated") "gif" else null,
                                    isAnimated = format?.animation == "animated",
                                    minBits = item.bits,
                                    color = item.color
                                )
                            }
                        }
                    }?.flatten()
                }?.flatten()?.let { emotes.addAll(it) }
            }
            emotes
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            try {
                val emotes = mutableListOf<CheerEmote>()
                val response = gql.loadGlobalCheerEmotes(gqlHeaders)
                if (checkIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                }
                response.data!!.cheerConfig.displayConfig.let { config ->
                    val background = config.backgrounds?.find { it == "dark" } ?: config.backgrounds?.lastOrNull() ?: ""
                    val format = if (animateGifs) {
                        config.types?.find { it.animation == "animated" } ?: config.types?.find { it.animation == "static" }
                    } else {
                        config.types?.find { it.animation == "static" }
                    } ?: config.types?.lastOrNull()
                    val scale1x = config.scales?.find { it.startsWith("1") } ?: config.scales?.lastOrNull() ?: ""
                    val scale2x = config.scales?.find { it.startsWith("2") } ?: scale1x
                    val scale3x = config.scales?.find { it.startsWith("3") } ?: scale2x
                    val scale4x = config.scales?.find { it.startsWith("4") } ?: scale3x
                    response.data.cheerConfig.groups.map { group ->
                        group.nodes.map { emote ->
                            emote.tiers.mapNotNull { tier ->
                                config.colors.find { it.bits == tier.bits }?.let { item ->
                                    val url = group.templateURL
                                        .replaceFirst("PREFIX", emote.prefix.lowercase())
                                        .replaceFirst("TIER", item.bits.toString())
                                        .replaceFirst("BACKGROUND", background)
                                        .replaceFirst("ANIMATION", format?.animation ?: "")
                                        .replaceFirst("EXTENSION", format?.extension ?: "")
                                    CheerEmote(
                                        name = emote.prefix,
                                        url1x = url.replaceFirst("SCALE", scale1x),
                                        url2x = url.replaceFirst("SCALE", scale2x),
                                        url3x = url.replaceFirst("SCALE", scale3x),
                                        url4x = url.replaceFirst("SCALE", scale4x),
                                        format = if (format?.animation == "animated") "gif" else null,
                                        isAnimated = format?.animation == "animated",
                                        minBits = item.bits,
                                        color = item.color,
                                    )
                                }
                            }
                        }.flatten()
                    }.flatten().let { emotes.addAll(it) }
                    gql.loadChannelCheerEmotes(gqlHeaders, channelLogin).data?.channel?.cheer?.cheerGroups?.map { group ->
                        group.nodes.map { emote ->
                            emote.tiers.mapNotNull { tier ->
                                config.colors.find { it.bits == tier.bits }?.let { item ->
                                    val url = group.templateURL
                                        .replaceFirst("PREFIX", emote.prefix.lowercase())
                                        .replaceFirst("TIER", item.bits.toString())
                                        .replaceFirst("BACKGROUND", background)
                                        .replaceFirst("ANIMATION", format?.animation ?: "")
                                        .replaceFirst("EXTENSION", format?.extension ?: "")
                                    CheerEmote(
                                        name = emote.prefix,
                                        url1x = url.replaceFirst("SCALE", scale1x),
                                        url2x = url.replaceFirst("SCALE", scale2x),
                                        url3x = url.replaceFirst("SCALE", scale3x),
                                        url4x = url.replaceFirst("SCALE", scale4x),
                                        format = if (format?.animation == "animated") "gif" else null,
                                        isAnimated = format?.animation == "animated",
                                        minBits = item.bits,
                                        color = item.color,
                                    )
                                }
                            }
                        }.flatten()
                    }?.flatten()?.let { emotes.addAll(it) }
                }
                emotes
            } catch (e: Exception) {
                if (e.message == "failed integrity check") throw e
                helix.getCheerEmotes(
                    headers = helixHeaders,
                    userId = channelId
                ).data.map { set ->
                    set.tiers.mapNotNull { tier ->
                        tier.images.let { it.dark ?: it.light }?.let { formats ->
                            if (animateGifs) {
                                formats.animated ?: formats.static
                            } else {
                                formats.static
                            }?.let { urls ->
                                CheerEmote(
                                    name = set.prefix,
                                    url1x = urls.url1x,
                                    url2x = urls.url2x,
                                    url3x = urls.url3x,
                                    url4x = urls.url4x,
                                    format = if (urls == formats.animated) "gif" else null,
                                    isAnimated = urls == formats.animated,
                                    minBits = tier.minBits,
                                    color = tier.color
                                )
                            }
                        }
                    }
                }.flatten()
            }
        }
    }

    suspend fun loadUserEmotes(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, userId: String?, animateGifs: Boolean, checkIntegrity: Boolean): List<TwitchEmote> = withContext(Dispatchers.IO) {
        try {
            if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
            val emotes = mutableListOf<TwitchEmote>()
            var offset: String? = null
            do {
                val response = gql.loadUserEmotes(gqlHeaders, channelId, offset)
                if (checkIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                }
                val sets = response.data!!.channel.self.availableEmoteSetsPaginated
                val items = sets.edges
                items.map { item ->
                    item.node.let { set ->
                        set.emotes.mapNotNull { emote ->
                            emote.token?.let { token ->
                                TwitchEmote(
                                    id = emote.id,
                                    name = if (emote.type == "SMILIES") {
                                        token.replace("\\", "").replace("?", "")
                                            .replace("&lt;", "<").replace("&gt;", ">")
                                            .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                            .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                                    } else token,
                                    setId = emote.setID,
                                    ownerId = set.owner?.id
                                )
                            }
                        }
                    }
                }.flatten().let { emotes.addAll(it) }
                offset = items.lastOrNull()?.cursor
            } while (!items.lastOrNull()?.cursor.isNullOrBlank() && sets.pageInfo?.hasNextPage == true)
            emotes
        } catch (e: Exception) {
            if (e.message == "failed integrity check") throw e
            try {
                if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
                val response = gql.loadQueryUserEmotes(
                    headers = gqlHeaders,
                )
                if (checkIntegrity) {
                    response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                }
                response.data!!.user?.emoteSets?.mapNotNull { set ->
                    set.emotes?.mapNotNull { emote ->
                        if (emote?.token != null && (!emote.type?.toString().equals("follower", true) || (emote.owner?.id == null || emote.owner.id == channelId))) {
                            TwitchEmote(
                                id = emote.id,
                                name = if (emote.type == "SMILIES") {
                                    emote.token.replace("\\", "").replace("?", "")
                                        .replace("&lt;", "<").replace("&gt;", ">")
                                        .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                        .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                                } else emote.token,
                                setId = emote.setID,
                                ownerId = emote.owner?.id
                            )
                        } else null
                    }
                }?.flatten() ?: emptyList()
            } catch (e: Exception) {
                if (e.message == "failed integrity check") throw e
                val emotes = mutableListOf<TwitchEmote>()
                var offset: String? = null
                do {
                    val response = helix.getUserEmotes(
                        headers = helixHeaders,
                        userId = userId,
                        channelId = channelId,
                        offset = offset
                    )
                    response.data.mapNotNull { emote ->
                        emote.name?.let { name ->
                            emote.id?.let { id ->
                                val format = if (animateGifs) {
                                    emote.format?.find { it == "animated" } ?: emote.format?.find { it == "static" }
                                } else {
                                    emote.format?.find { it == "static" }
                                } ?: emote.format?.firstOrNull() ?: ""
                                val theme = emote.theme?.find { it == "dark" } ?: emote.theme?.lastOrNull() ?: ""
                                val scale1x = emote.scale?.find { it.startsWith("1") } ?: emote.scale?.lastOrNull() ?: ""
                                val scale2x = emote.scale?.find { it.startsWith("2") } ?: scale1x
                                val scale3x = emote.scale?.find { it.startsWith("3") } ?: scale2x
                                val url = response.template
                                    .replaceFirst("{{id}}", id)
                                    .replaceFirst("{{format}}", format)
                                    .replaceFirst("{{theme_mode}}", theme)
                                TwitchEmote(
                                    name = if (emote.type == "smilies") {
                                        name.replace("\\", "").replace("?", "")
                                            .replace("&lt;", "<").replace("&gt;", ">")
                                            .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                            .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                                    } else name,
                                    url1x = url.replaceFirst("{{scale}}", scale1x),
                                    url2x = url.replaceFirst("{{scale}}", scale2x),
                                    url3x = url.replaceFirst("{{scale}}", scale3x),
                                    url4x = url.replaceFirst("{{scale}}", scale3x),
                                    format = if (format == "animated") "gif" else null,
                                    setId = emote.setId,
                                    ownerId = emote.ownerId
                                )
                            }
                        }
                    }.let { emotes.addAll(it) }
                    offset = response.pagination?.cursor
                } while (!response.pagination?.cursor.isNullOrBlank())
                emotes
            }
        }
    }

    suspend fun loadEmotesFromSet(helixHeaders: Map<String, String>, setIds: List<String>, animateGifs: Boolean): List<TwitchEmote> = withContext(Dispatchers.IO) {
        val response = helix.getEmotesFromSet(
            headers = helixHeaders,
            setIds = setIds
        )
        response.data.mapNotNull { emote ->
            emote.name?.let { name ->
                emote.id?.let { id ->
                    val format = if (animateGifs) {
                        emote.format?.find { it == "animated" } ?: emote.format?.find { it == "static" }
                    } else {
                        emote.format?.find { it == "static" }
                    } ?: emote.format?.firstOrNull() ?: ""
                    val theme = emote.theme?.find { it == "dark" } ?: emote.theme?.lastOrNull() ?: ""
                    val scale1x = emote.scale?.find { it.startsWith("1") } ?: emote.scale?.lastOrNull() ?: ""
                    val scale2x = emote.scale?.find { it.startsWith("2") } ?: scale1x
                    val scale3x = emote.scale?.find { it.startsWith("3") } ?: scale2x
                    val url = response.template
                        .replaceFirst("{{id}}", id)
                        .replaceFirst("{{format}}", format)
                        .replaceFirst("{{theme_mode}}", theme)
                    TwitchEmote(
                        name = if (emote.type == "smilies") {
                            name.replace("\\", "").replace("?", "")
                                .replace("&lt;", "<").replace("&gt;", ">")
                                .replace(Regex("\\((.)\\|.\\)")) { it.groups[1]?.value ?: "" }
                                .replace(Regex("\\[(.).*?]")) { it.groups[1]?.value ?: "" }
                        } else name,
                        url1x = url.replaceFirst("{{scale}}", scale1x),
                        url2x = url.replaceFirst("{{scale}}", scale2x),
                        url3x = url.replaceFirst("{{scale}}", scale3x),
                        url4x = url.replaceFirst("{{scale}}", scale3x),
                        format = if (format == "animated") "gif" else null,
                        setId = emote.setId,
                        ownerId = emote.ownerId
                    )
                }
            }
        }
    }

    suspend fun loadUserFollowing(helixHeaders: Map<String, String>, targetId: String?, userId: String?, gqlHeaders: Map<String, String>, targetLogin: String?): Pair<Boolean, Boolean?> = withContext(Dispatchers.IO) {
        try {
            if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || targetLogin == null) throw Exception()
            val follower = gql.loadFollowingUser(gqlHeaders, targetLogin).data?.user?.self?.follower
            Pair(follower != null, follower?.disableNotifications == false)
        } catch (e: Exception) {
            val following = helix.getUserFollows(
                headers = helixHeaders,
                targetId = targetId,
                userId = userId
            ).data.firstOrNull()?.channelId == targetId
            Pair(following, null)
        }
    }

    suspend fun loadGameFollowing(gqlHeaders: Map<String, String>, gameName: String?): Boolean = withContext(Dispatchers.IO) {
        gql.loadFollowingGame(gqlHeaders, gameName).data?.game?.self?.follow != null
    }

    suspend fun loadVideoMessages(gqlHeaders: Map<String, String>, videoId: String, offset: Int? = null, cursor: String? = null): VideoMessagesResponse = withContext(Dispatchers.IO) {
        gql.loadVideoMessages(gqlHeaders, videoId, offset, cursor)
    }

    suspend fun loadVideoGames(gqlHeaders: Map<String, String>, videoId: String?): List<Game> = withContext(Dispatchers.IO) {
        val response = gql.loadVideoGames(gqlHeaders, videoId)
        response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        response.data!!.video.moments.edges.map { item ->
            item.node.let {
                Game(
                    gameId = it.details?.game?.id,
                    gameName = it.details?.game?.displayName,
                    boxArtUrl = it.details?.game?.boxArtURL,
                    vodPosition = it.positionMilliseconds,
                    vodDuration = it.durationMilliseconds,
                )
            }
        }
    }

    suspend fun loadJoinRaid(gqlHeaders: Map<String, String>, raidId: String?) = withContext(Dispatchers.IO) {
        gql.loadJoinRaid(gqlHeaders, raidId).also { response ->
            response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
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
                        val json = buildJsonObject {
                            put("event", "minute-watched")
                            putJsonObject("properties") {
                                put("channel_id", channelId)
                                put("broadcast_id", streamId)
                                put("player", "site")
                                put("user_id", userId?.toLong())
                            }
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
        gql.loadFollowUser(gqlHeaders, userId).errors?.firstOrNull()?.message
    }

    suspend fun unfollowUser(gqlHeaders: Map<String, String>, userId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowUser(gqlHeaders, userId).errors?.firstOrNull()?.message
    }

    suspend fun toggleNotifications(gqlHeaders: Map<String, String>, userId: String?, disableNotifications: Boolean): String? = withContext(Dispatchers.IO) {
        gql.loadToggleNotificationsUser(gqlHeaders, userId, disableNotifications).errors?.firstOrNull()?.message
    }

    suspend fun followGame(gqlHeaders: Map<String, String>, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadFollowGame(gqlHeaders, gameId).errors?.firstOrNull()?.message
    }

    suspend fun unfollowGame(gqlHeaders: Map<String, String>, gameId: String?): String? = withContext(Dispatchers.IO) {
        gql.loadUnfollowGame(gqlHeaders, gameId).errors?.firstOrNull()?.message
    }

    suspend fun createChatEventSubSubscription(helixHeaders: Map<String, String>, userId: String?, channelId: String?, type: String?, sessionId: String?): String? = withContext(Dispatchers.IO) {
        val json = buildJsonObject {
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
        }
        val response = helix.createEventSubSubscription(helixHeaders, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun sendMessage(helixHeaders: Map<String, String>, userId: String?, channelId: String?, message: String?, replyId: String?): String? = withContext(Dispatchers.IO) {
        val json = buildJsonObject {
            put("broadcaster_id", channelId)
            put("sender_id", userId)
            put("message", message)
            replyId?.let { put("reply_parent_message_id", it) }
        }
        val response = helix.sendMessage(helixHeaders, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun sendAnnouncement(helixHeaders: Map<String, String>, userId: String?, gqlHeaders: Map<String, String>, channelId: String?, message: String?, color: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.sendAnnouncement(gqlHeaders, channelId, message, color).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val json = buildJsonObject {
                put("message", message)
                color?.let { put("color", it) }
            }
            helix.sendAnnouncement(helixHeaders, channelId, userId, json)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun banUser(helixHeaders: Map<String, String>, userId: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?, duration: String? = null, reason: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.banUser(gqlHeaders, channelId, targetLogin, duration, reason).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            val json = buildJsonObject {
                putJsonObject("data") {
                    duration?.toIntOrNull()?.let { put("duration", it) }
                    put("reason", reason)
                    put("user_id", targetId)
                }
            }
            helix.banUser(helixHeaders, channelId, userId, json)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun unbanUser(helixHeaders: Map<String, String>, userId: String?, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.unbanUser(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.unbanUser(helixHeaders, channelId, userId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun deleteMessages(helixHeaders: Map<String, String>, channelId: String?, userId: String?, messageId: String? = null): String? = withContext(Dispatchers.IO) {
        val response = helix.deleteMessages(helixHeaders, channelId, userId, messageId)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun updateChatColor(helixHeaders: Map<String, String>, userId: String?, gqlHeaders: Map<String, String>, color: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.updateChatColor(gqlHeaders, color).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            helix.updateChatColor(helixHeaders, userId, color)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getChatColor(helixHeaders: Map<String, String>, userId: String?): String? = withContext(Dispatchers.IO) {
        val response = helix.getChatColor(helixHeaders, userId)
        if (response.isSuccessful) {
            response.body()?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject?.get("color")?.jsonPrimitive?.contentOrNull
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun startCommercial(helixHeaders: Map<String, String>, channelId: String?, length: String?): String? = withContext(Dispatchers.IO) {
        val json = buildJsonObject {
            put("broadcaster_id", channelId)
            put("length", length?.toIntOrNull())
        }
        val response = helix.startCommercial(helixHeaders, json)
        if (response.isSuccessful) {
            response.body()?.jsonObject?.get("data")?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun updateChatSettings(helixHeaders: Map<String, String>, channelId: String?, userId: String?, emote: Boolean? = null, followers: Boolean? = null, followersDuration: String? = null, slow: Boolean? = null, slowDuration: Int? = null, subs: Boolean? = null, unique: Boolean? = null): String? = withContext(Dispatchers.IO) {
        val json = buildJsonObject {
            emote?.let { put("emote_mode", it) }
            followers?.let { put("follower_mode", it) }
            followersDuration?.toIntOrNull()?.let { put("follower_mode_duration", it) }
            slow?.let { put("slow_mode", it) }
            slowDuration?.let { put("slow_mode_wait_time", it) }
            subs?.let { put("subscriber_mode", it) }
            unique?.let { put("unique_chat_mode", it) }
        }
        val response = helix.updateChatSettings(helixHeaders, channelId, userId, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun createStreamMarker(helixHeaders: Map<String, String>, channelId: String?, gqlHeaders: Map<String, String>, channelLogin: String?, description: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.createStreamMarker(gqlHeaders, channelLogin).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val json = buildJsonObject {
                put("user_id", channelId)
                description?.let { put("description", it) }
            }
            helix.createStreamMarker(helixHeaders, json)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getModerators(gqlHeaders: Map<String, String>, channelLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = gql.getModerators(gqlHeaders, channelLogin)
        response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        if (response.isSuccessful) {
            response.body()?.data?.user?.mods?.edges?.map { it.node.login }.toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun addModerator(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.addModerator(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.addModerator(helixHeaders, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun removeModerator(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.removeModerator(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.removeModerator(helixHeaders, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun startRaid(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?, checkIntegrity: Boolean): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            val targetId = loadCheckUser(
                channelLogin = targetLogin,
                helixHeaders = helixHeaders,
                gqlHeaders = gqlHeaders,
                checkIntegrity = checkIntegrity
            )?.channelId
            gql.startRaid(gqlHeaders, channelId, targetId).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.startRaid(helixHeaders, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun cancelRaid(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.cancelRaid(gqlHeaders, channelId).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            helix.cancelRaid(helixHeaders, channelId)
        }
        if (response.isSuccessful) {
            response.body().toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun getVips(gqlHeaders: Map<String, String>, channelLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = gql.getVips(gqlHeaders, channelLogin)
        response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        if (response.isSuccessful) {
            response.body()?.data?.user?.vips?.edges?.map { it.node.login }.toString()
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun addVip(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.addVip(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.addVip(helixHeaders, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun removeVip(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, channelId: String?, targetLogin: String?): String? = withContext(Dispatchers.IO) {
        val response = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            gql.removeVip(gqlHeaders, channelId, targetLogin).also { response ->
                response.body()?.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        } else {
            val targetId = helix.getUsers(
                headers = helixHeaders,
                logins = targetLogin?.let { listOf(it) }
            ).data.firstOrNull()?.channelId
            helix.removeVip(helixHeaders, channelId, targetId)
        }
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun sendWhisper(helixHeaders: Map<String, String>, userId: String?, targetLogin: String?, message: String?): String? = withContext(Dispatchers.IO) {
        val targetId = helix.getUsers(
            headers = helixHeaders,
            logins = targetLogin?.let { listOf(it) }
        ).data.firstOrNull()?.channelId
        val json = buildJsonObject {
            put("message", message)
        }
        val response = helix.sendWhisper(helixHeaders, userId, targetId, json)
        if (response.isSuccessful) {
            null
        } else {
            response.errorBody()?.string()
        }
    }

    suspend fun loadUserResult(channelId: String? = null, channelLogin: String? = null, gqlHeaders: Map<String, String>): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        if (!channelId.isNullOrBlank()) {
            val response = gql.loadQueryUserResultID(
                headers = gqlHeaders,
                id = channelId
            )
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            response.data!!.userResultByID?.let {
                when (it.typeName) {
                    "User" -> Pair(null, null)
                    "UserDoesNotExist" -> Pair(it.typeName, it.reason)
                    "UserError" -> Pair(it.typeName, null)
                    else -> null
                }
            }
        } else if (!channelLogin.isNullOrBlank()) {
            val response = gql.loadQueryUserResultLogin(
                headers = gqlHeaders,
                login = channelLogin
            )
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            response.data!!.userResultByLogin?.let {
                when (it.typeName) {
                    "User" -> Pair(null, null)
                    "UserDoesNotExist" -> Pair(it.typeName, it.reason)
                    "UserError" -> Pair(it.typeName, null)
                    else -> null
                }
            }
        } else null
    }
}