package com.github.andreyasadchy.xtra.ui.player.stream

import android.util.Base64
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class StreamPlayerViewModel @Inject constructor(
    private val repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    shownNotificationsRepository: ShownNotificationsRepository,
    notificationUsersRepository: NotificationUsersRepository,
    okHttpClient: OkHttpClient,
    private val playerRepository: PlayerRepository) : PlayerViewModel(repository, localFollowsChannel, shownNotificationsRepository, notificationUsersRepository, okHttpClient) {

    val result = MutableStateFlow<String?>(null)

    val stream = MutableStateFlow<Stream?>(null)

    var useProxy = false
    var playingAds = false

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

    fun load(gqlHeaders: Map<String, String>, channelLogin: String, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, proxyPlaybackAccessToken: Boolean, proxyMultivariantPlaylist: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?, enableIntegrity: Boolean) {
        if (result.value == null) {
            viewModelScope.launch {
                try {
                    val url = playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, proxyPlaybackAccessToken, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
                    result.value = if (proxyMultivariantPlaylist) {
                        val response = playerRepository.loadStreamPlaylistResponse(url, true, proxyHost, proxyPort, proxyUser, proxyPassword)
                        Base64.encodeToString(response.toByteArray(), Base64.DEFAULT)
                    } else {
                        url
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
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
}
