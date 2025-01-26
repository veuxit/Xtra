package com.github.andreyasadchy.xtra.ui.player

import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val shownNotificationsRepository: ShownNotificationsRepository,
    private val notificationUsersRepository: NotificationUsersRepository,
    private val okHttpClient: OkHttpClient,
    private val playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val offlineRepository: OfflineRepository,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val streamResult = MutableStateFlow<String?>(null)
    val stream = MutableStateFlow<Stream?>(null)
    var useProxy = false
    var playingAds = false

    val videoResult = MutableStateFlow<Uri?>(null)
    var playbackPosition: Long? = null
    val savedPosition = MutableSharedFlow<VideoPosition?>()
    val isBookmarked = MutableStateFlow<Boolean?>(null)
    val gamesList = MutableStateFlow<List<Game>?>(null)
    var shouldRetry = true

    val clipUrls = MutableStateFlow<Map<String, String>?>(null)

    val offlineVideo = MutableSharedFlow<OfflineVideo?>()

    var started = false
    var background = false
    var pipMode = false
    var playerMode = PlaybackService.PLAYER_MODE_NORMAL
    val loaded = MutableStateFlow(false)
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)

    suspend fun checkPlaylist(url: String): Boolean {
        return try {
            val playlist = playerRepository.getMediaPlaylist(url)
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

    fun loadStreamResult(gqlHeaders: Map<String, String>, channelLogin: String, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, proxyPlaybackAccessToken: Boolean, proxyMultivariantPlaylist: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?, enableIntegrity: Boolean) {
        if (streamResult.value == null) {
            viewModelScope.launch {
                try {
                    val url = playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, proxyPlaybackAccessToken, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
                    streamResult.value = if (proxyMultivariantPlaylist) {
                        val response = playerRepository.loadStreamPlaylistResponse(url, true, proxyHost, proxyPort, proxyUser, proxyPassword)
                        Base64.encodeToString(response.toByteArray(), Base64.DEFAULT)
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

    fun loadStream(stream: Stream, loop: Boolean, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (loop) {
            viewModelScope.launch {
                while (isActive) {
                    try {
                        updateStream(stream, helixHeaders, gqlHeaders, checkIntegrity)
                        delay(300000L)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check" && integrity.value == null) {
                            integrity.value = "stream"
                        }
                        delay(60000L)
                    }
                }
            }
        } else if (stream.viewerCount == null) {
            viewModelScope.launch {
                try {
                    updateStream(stream, helixHeaders, gqlHeaders, checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "stream"
                    }
                }
            }
        }
    }

    private suspend fun updateStream(stream: Stream, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        repository.loadStream(stream.channelId, stream.channelLogin, helixHeaders, gqlHeaders, checkIntegrity).let {
            this.stream.value = Stream(
                id = if (!it?.id.isNullOrBlank()) {
                    it.id
                } else stream.id,
                channelId = stream.channelId,
                channelLogin = stream.channelLogin,
                channelName = stream.channelName,
                gameId = it?.gameId,
                gameSlug = it?.gameSlug,
                gameName = it?.gameName,
                type = stream.type,
                title = it?.title,
                viewerCount = it?.viewerCount,
                startedAt = it?.startedAt,
                thumbnailUrl = stream.thumbnailUrl,
                profileImageUrl = stream.profileImageUrl,
                tags = stream.tags,
                user = stream.user,
            )
        }
    }

    fun loadVideo(gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean) {
        if (videoResult.value == null) {
            viewModelScope.launch {
                try {
                    videoResult.value = playerRepository.loadVideoPlaylistUrl(gqlHeaders, videoId, playerType, supportedCodecs, enableIntegrity)
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
            savedPosition.emit(playerRepository.getVideoPosition(id))
        }
    }

    fun saveVideoPosition(id: Long, position: Long) {
        if (loaded.value) {
            viewModelScope.launch {
                playerRepository.saveVideoPosition(VideoPosition(id, position))
            }
        }
    }

    fun loadGamesList(gqlHeaders: Map<String, String>, videoId: String?) {
        if (gamesList.value == null) {
            viewModelScope.launch {
                try {
                    gamesList.value = repository.loadVideoGames(gqlHeaders, videoId)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refreshVideo"
                    }
                }
            }
        }
    }

    fun checkBookmark(id: String) {
        viewModelScope.launch {
            isBookmarked.value = bookmarksRepository.getBookmarkByVideoId(id) != null
        }
    }

    fun saveBookmark(filesDir: String, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, video: Video) {
        viewModelScope.launch {
            val item = video.id?.let { bookmarksRepository.getBookmarkByVideoId(it) }
            if (item != null) {
                bookmarksRepository.deleteBookmark(item)
            } else {
                val downloadedThumbnail = video.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    video.thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "thumbnails").mkdir()
                        val path = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val downloadedLogo = video.channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    video.channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val userTypes = try {
                    video.channelId?.let { repository.loadUserTypes(listOf(it), helixHeaders, gqlHeaders) }?.firstOrNull()
                } catch (e: Exception) {
                    null
                }
                bookmarksRepository.saveBookmark(
                    Bookmark(
                        videoId = video.id,
                        userId = video.channelId,
                        userLogin = video.channelLogin,
                        userName = video.channelName,
                        userType = userTypes?.type,
                        userBroadcasterType = userTypes?.broadcasterType,
                        userLogo = downloadedLogo,
                        gameId = video.gameId,
                        gameSlug = video.gameSlug,
                        gameName = video.gameName,
                        title = video.title,
                        createdAt = video.uploadDate,
                        thumbnail = downloadedThumbnail,
                        type = video.type,
                        duration = video.duration,
                        animatedPreviewURL = video.animatedPreviewURL
                    )
                )
            }
        }
    }

    fun loadClip(gqlHeaders: Map<String, String>, id: String?) {
        if (clipUrls.value == null) {
            viewModelScope.launch {
                try {
                    clipUrls.value = playerRepository.loadClipUrls(gqlHeaders, id) ?: emptyMap()
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

    fun getOfflineVideo(id: Int) {
        viewModelScope.launch {
            offlineVideo.emit(offlineRepository.getVideoById(id))
        }
    }

    fun saveOfflineVideoPosition(id: Int, position: Long) {
        if (loaded.value) {
            viewModelScope.launch {
                offlineRepository.updateVideoPosition(id, position)
            }
        }
    }

    fun isFollowingChannel(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?, channelLogin: String?) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!channelId.isNullOrBlank()) {
                        if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                            val response = repository.loadUserFollowing(helixHeaders, channelId, userId, gqlHeaders, channelLogin)
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

    fun saveFollowChannel(filesDir: String, gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, notificationsEnabled: Boolean, startedAt: String?) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = repository.followUser(gqlHeaders, channelId)
                        if (!errorMessage.isNullOrBlank()) {
                            if (errorMessage == "failed integrity check" && integrity.value == null) {
                                integrity.value = "follow"
                            } else {
                                follow.value = Pair(true, errorMessage)
                            }
                        } else {
                            _isFollowing.value = true
                            follow.value = Pair(true, errorMessage)
                            if (notificationsEnabled) {
                                startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                    shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                    } else {
                        val downloadedLogo = channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "profile_pics").mkdir()
                            val path = filesDir + File.separator + "profile_pics" + File.separator + channelId
                            viewModelScope.launch(Dispatchers.IO) {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
                                    }
                                }
                            }
                            path
                        }
                        localFollowsChannel.saveFollow(LocalFollowChannel(channelId, channelLogin, channelName, downloadedLogo))
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

    fun deleteFollowChannel(gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = repository.unfollowUser(gqlHeaders, channelId)
                        if (!errorMessage.isNullOrBlank()) {
                            if (errorMessage == "failed integrity check" && integrity.value == null) {
                                integrity.value = "unfollow"
                            } else {
                                follow.value = Pair(false, errorMessage)
                            }
                        } else {
                            _isFollowing.value = false
                            follow.value = Pair(false, errorMessage)
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