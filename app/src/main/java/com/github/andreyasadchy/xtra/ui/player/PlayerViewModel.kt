package com.github.andreyasadchy.xtra.ui.player

import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getByteArrayCronetCallback
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.chromium.net.CronetEngine
import org.chromium.net.UrlResponseInfo
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.coroutines.suspendCoroutine

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val shownNotificationsRepository: ShownNotificationsRepository,
    private val notificationUsersRepository: NotificationUsersRepository,
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
    private val playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val offlineRepository: OfflineRepository,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val streamResult = MutableStateFlow<String?>(null)
    val stream = MutableStateFlow<Stream?>(null)
    private var streamJob: Job? = null
    var useCustomProxy = false
    var playingAds = false
    var usingProxy = false
    var stopProxy = false

    val videoResult = MutableStateFlow<String?>(null)
    var playbackPosition: Long? = null
    val savedPosition = MutableStateFlow<Long?>(null)
    val isBookmarked = MutableStateFlow<Boolean?>(null)
    val gamesList = MutableStateFlow<List<Game>?>(null)
    var shouldRetry = true

    val clipUrls = MutableStateFlow<Map<String, String>?>(null)

    val savedOfflineVideoPosition = MutableStateFlow<Long?>(null)

    var qualities: Map<String, Pair<String, String?>> = emptyMap()
    var qualityIndex: Int = 0
    var previousIndex: Int = 0
    var playlistUrl: Uri? = null
    var started = false
    var restoreQuality = false
    var resume = false
    var hidden = false
    val loaded = MutableStateFlow(false)
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)

    suspend fun checkPlaylist(useCronet: Boolean, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlist = if (useCronet && cronetEngine != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                    cronetEngine.newUrlRequestBuilder(url, request.callback, cronetExecutor).build().start()
                    val response = request.future.get().responseBody as ByteArray
                    response.inputStream().use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                } else {
                    val response = suspendCoroutine<Pair<UrlResponseInfo, ByteArray>> { continuation ->
                        cronetEngine.newUrlRequestBuilder(url, getByteArrayCronetCallback(continuation), cronetExecutor).build().start()
                    }
                    response.second.inputStream().use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                }
            } else {
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    response.body.byteStream().use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                }
            }
            playlist.segments.lastOrNull()?.let { segment ->
                segment.title?.let { it.contains("Amazon") || it.contains("Adform") || it.contains("DCM") } == true ||
                        segment.programDateTime?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let { segmentStartTime ->
                            playlist.dateRanges.find { dateRange ->
                                (dateRange.id.startsWith("stitched-ad-") || dateRange.rangeClass == "twitch-stitched-ad" || dateRange.ad) &&
                                        dateRange.endDate?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let { endTime ->
                                            segmentStartTime < endTime
                                        } == true ||
                                        dateRange.startDate.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let { startTime ->
                                            (dateRange.duration ?: dateRange.plannedDuration)?.let { (it * 1000f).toLong() }?.let { duration ->
                                                segmentStartTime < (startTime + duration)
                                            } == true
                                        } == true
                            } != null
                        } == true
            } == true
        } catch (e: Exception) {
            false
        }
    }

    fun loadStreamResult(useCronet: Boolean, gqlHeaders: Map<String, String>, channelLogin: String, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, proxyPlaybackAccessToken: Boolean, proxyMultivariantPlaylist: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?, enableIntegrity: Boolean) {
        if (streamResult.value == null) {
            viewModelScope.launch {
                try {
                    val url = playerRepository.loadStreamPlaylistUrl(useCronet, gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, proxyPlaybackAccessToken, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
                    streamResult.value = if (proxyMultivariantPlaylist) {
                        withContext(Dispatchers.IO) {
                            val response = okHttpClient.newBuilder().apply {
                                if (!proxyHost.isNullOrBlank() && proxyPort != null) {
                                    proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
                                    if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                                        proxyAuthenticator { _, response ->
                                            response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(proxyUser, proxyPassword)).build()
                                        }
                                    }
                                }
                            }.build().newCall(Request.Builder().url(url).build()).execute().use { response ->
                                response.body.string()
                            }
                            Base64.encodeToString(response.toByteArray(), Base64.DEFAULT)
                        }
                    } else {
                        url
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refreshStream"
                    }
                }
            }
        }
    }

    fun loadStream(channelId: String?, channelLogin: String?, viewerCount: Int?, loop: Boolean, useCronet: Boolean, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (loop) {
            streamJob?.cancel()
            streamJob = viewModelScope.launch {
                while (isActive) {
                    try {
                        updateStream(channelId, channelLogin, useCronet, helixHeaders, gqlHeaders, enableIntegrity)
                        delay(300000L)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "stream"
                        }
                        delay(60000L)
                    }
                }
            }
        } else if (viewerCount == null) {
            viewModelScope.launch {
                try {
                    updateStream(channelId, channelLogin, useCronet, helixHeaders, gqlHeaders, enableIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "stream"
                    }
                }
            }
        }
    }

    private suspend fun updateStream(channelId: String?, channelLogin: String?, useCronet: Boolean, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        stream.value = try {
            val response = graphQLRepository.loadQueryUsersStream(
                useCronet = useCronet,
                headers = gqlHeaders,
                ids = channelId?.let { listOf(it) },
                logins = if (channelId.isNullOrBlank()) channelLogin?.let { listOf(it) } else null,
            )
            if (enableIntegrity) {
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
            if (helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) throw Exception()
            try {
                helixRepository.getStreams(
                    useCronet = useCronet,
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
                val response = graphQLRepository.loadViewerCount(useCronet, gqlHeaders, channelLogin)
                if (enableIntegrity) {
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

    fun loadVideo(useCronet: Boolean, gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean) {
        if (videoResult.value == null) {
            viewModelScope.launch {
                try {
                    videoResult.value = playerRepository.loadVideoPlaylistUrl(useCronet, gqlHeaders, videoId, playerType, supportedCodecs, enableIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refreshVideo"
                    }
                }
            }
        }
    }

    fun getVideoPosition(id: Long) {
        viewModelScope.launch {
            savedPosition.value = playerRepository.getVideoPosition(id)?.position ?: 0
        }
    }

    fun saveVideoPosition(id: Long, position: Long) {
        if (loaded.value) {
            viewModelScope.launch {
                playerRepository.saveVideoPosition(VideoPosition(id, position))
            }
        }
    }

    fun loadGamesList(videoId: String?, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (gamesList.value == null) {
            viewModelScope.launch {
                try {
                    val response = graphQLRepository.loadVideoGames(useCronet, gqlHeaders, videoId)
                    if (enableIntegrity && integrity.value == null) {
                        response.errors?.find { it.message == "failed integrity check" }?.let {
                            integrity.value = "refreshVideo"
                            return@launch
                        }
                    }
                    gamesList.value = response.data!!.video.moments.edges.map { item ->
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
                } catch (e: Exception) {

                }
            }
        }
    }

    fun checkBookmark(id: String) {
        viewModelScope.launch {
            isBookmarked.value = bookmarksRepository.getBookmarkByVideoId(id) != null
        }
    }

    fun saveBookmark(filesDir: String, useCronet: Boolean, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, videoId: String?, title: String?, uploadDate: String?, duration: String?, type: String?, animatedPreviewUrl: String?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?) {
        viewModelScope.launch {
            val item = videoId?.let { bookmarksRepository.getBookmarkByVideoId(it) }
            if (item != null) {
                bookmarksRepository.deleteBookmark(item)
            } else {
                val downloadedThumbnail = videoId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "thumbnails").mkdir()
                        val path = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                if (useCronet && cronetEngine != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                        cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                        val response = request.future.get()
                                        if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.responseBody as ByteArray)
                                            }
                                        }
                                    } else {
                                        val response = suspendCoroutine<Pair<UrlResponseInfo, ByteArray>> { continuation ->
                                            cronetEngine.newUrlRequestBuilder(it, getByteArrayCronetCallback(continuation), cronetExecutor).build().start()
                                        }
                                        if (response.first.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.second)
                                            }
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                if (useCronet && cronetEngine != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                        cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                        val response = request.future.get()
                                        if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.responseBody as ByteArray)
                                            }
                                        }
                                    } else {
                                        val response = suspendCoroutine<Pair<UrlResponseInfo, ByteArray>> { continuation ->
                                            cronetEngine.newUrlRequestBuilder(it, getByteArrayCronetCallback(continuation), cronetExecutor).build().start()
                                        }
                                        if (response.first.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.second)
                                            }
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val userTypes = channelId?.let {
                    try {
                        val response = graphQLRepository.loadQueryUsersType(useCronet, gqlHeaders, listOf(channelId))
                        response.data!!.users?.firstOrNull()?.let {
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
                        }
                    } catch (e: Exception) {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                            try {
                                helixRepository.getUsers(
                                    useCronet = useCronet,
                                    headers = helixHeaders,
                                    ids = listOf(channelId)
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
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }
                }
                bookmarksRepository.saveBookmark(
                    Bookmark(
                        videoId = videoId,
                        userId = channelId,
                        userLogin = channelLogin,
                        userName = channelName,
                        userType = userTypes?.type,
                        userBroadcasterType = userTypes?.broadcasterType,
                        userLogo = downloadedLogo,
                        gameId = gameId,
                        gameSlug = gameSlug,
                        gameName = gameName,
                        title = title,
                        createdAt = uploadDate,
                        thumbnail = downloadedThumbnail,
                        type = type,
                        duration = duration,
                        animatedPreviewURL = animatedPreviewUrl
                    )
                )
            }
        }
    }

    fun loadClip(useCronet: Boolean, gqlHeaders: Map<String, String>, id: String?, enableIntegrity: Boolean) {
        if (clipUrls.value == null) {
            viewModelScope.launch {
                try {
                    clipUrls.value = playerRepository.loadClipUrls(useCronet, gqlHeaders, id, enableIntegrity) ?: emptyMap()
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refreshClip"
                    } else {
                        clipUrls.value = emptyMap()
                    }
                }
            }
        }
    }

    fun getOfflineVideoPosition(id: Int) {
        viewModelScope.launch {
            savedOfflineVideoPosition.value = offlineRepository.getVideoById(id)?.lastWatchPosition ?: 0
        }
    }

    fun saveOfflineVideoPosition(id: Int, position: Long) {
        if (loaded.value) {
            viewModelScope.launch {
                offlineRepository.updateVideoPosition(id, position)
            }
        }
    }

    fun isFollowingChannel(userId: String?, channelId: String?, channelLogin: String?, setting: Int, useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!channelId.isNullOrBlank()) {
                        if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                            val response = try {
                                if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || channelLogin == null) throw Exception()
                                val follower = graphQLRepository.loadFollowingUser(useCronet, gqlHeaders, channelLogin).data?.user?.self?.follower
                                Pair(follower != null, follower?.disableNotifications == false)
                            } catch (e: Exception) {
                                val following = helixRepository.getUserFollows(
                                    useCronet = useCronet,
                                    headers = helixHeaders,
                                    userId = userId,
                                    targetId = channelId,
                                ).data.firstOrNull()?.channelId == channelId
                                Pair(following, null)
                            }
                            _isFollowing.value = response.first
                        } else {
                            _isFollowing.value = localFollowsChannel.getFollowByUserId(channelId) != null
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(userId: String?, channelId: String?, channelLogin: String?, channelName: String?, setting: Int, notificationsEnabled: Boolean, startedAt: String?, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = graphQLRepository.loadFollowUser(useCronet, gqlHeaders, channelId).also { response ->
                            if (enableIntegrity && integrity.value == null) {
                                response.errors?.find { it.message == "failed integrity check" }?.let {
                                    integrity.value = "follow"
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(true, errorMessage)
                        } else {
                            _isFollowing.value = true
                            follow.value = Pair(true, null)
                            if (notificationsEnabled) {
                                startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                    shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                    } else {
                        localFollowsChannel.saveFollow(LocalFollowChannel(channelId, channelLogin, channelName))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                        notificationUsersRepository.saveUser(NotificationUser(channelId))
                        if (notificationsEnabled) {
                            startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowChannel(userId: String?, channelId: String?, setting: Int, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = graphQLRepository.loadUnfollowUser(useCronet, gqlHeaders, channelId).also { response ->
                            if (enableIntegrity && integrity.value == null) {
                                response.errors?.find { it.message == "failed integrity check" }?.let {
                                    integrity.value = "unfollow"
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(false, errorMessage)
                        } else {
                            _isFollowing.value = false
                            follow.value = Pair(false, null)
                        }
                    } else {
                        localFollowsChannel.getFollowByUserId(channelId)?.let { localFollowsChannel.deleteFollow(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                        notificationUsersRepository.deleteUser(NotificationUser(channelId))
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}