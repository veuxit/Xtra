package com.github.andreyasadchy.xtra.ui.player.stream

import android.content.Context
import android.util.Base64
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.Util
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class StreamPlayerViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    private val playerRepository: PlayerRepository) : PlayerViewModel(applicationContext, repository, localFollowsChannel) {

    val _stream = MutableLiveData<Stream?>()
    val stream: MutableLiveData<Stream?>
        get() = _stream

    var result = MutableLiveData<String>()

    var useProxy = false
    var playingAds = false

    suspend fun checkPlaylist(url: String): Boolean {
        return try {
            val playlist = playerRepository.getMediaPlaylist(url)
            if (playlist.segments.lastOrNull()?.title?.let { it.contains("Amazon") || it.contains("Adform") || it.contains("DCM") } == true) {
                true
            } else {
                if (playlist.programDateTime != null) {
                    val segmentStartTimeMs = Util.parseXsDateTime(playlist.programDateTime)
                    playlist.dateRanges.find {
                        (it.id.startsWith("stitched-ad-") || it.rangeClass == "twitch-stitched-ad" || it.ad) &&
                                if (it.endDate != null) {
                                    segmentStartTimeMs < Util.parseXsDateTime(it.endDate)
                                } else {
                                    val duration = it.duration ?: it.plannedDuration
                                    if (duration != null) {
                                        segmentStartTimeMs < (Util.parseXsDateTime(it.startDate) + BigDecimal(duration).multiply(BigDecimal(1000L)).toLong())
                                    } else false
                                }
                    } != null
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun load(gqlHeaders: Map<String, String>, channelLogin: String, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, proxyPlaybackAccessToken: Boolean, proxyMultivariantPlaylist: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                val url = playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, proxyPlaybackAccessToken, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
                if (proxyMultivariantPlaylist) {
                    val response = playerRepository.loadStreamPlaylistResponse(url, true, proxyHost, proxyPort, proxyUser, proxyPassword)
                    Base64.encodeToString(response.toByteArray(), Base64.DEFAULT)
                } else {
                    url
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") {
                    _integrity.postValue(true)
                }
                null
            }.let { result.postValue(it) }
        }
    }

    fun loadStream(stream: Stream) {
        val account = Account.get(applicationContext)
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
        if (applicationContext.prefs().getBoolean(C.CHAT_DISABLE, false) || !applicationContext.prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) || (applicationContext.prefs().getBoolean(C.CHAT_POINTS_COLLECT, true) && !account.id.isNullOrBlank() && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank())) {
            viewModelScope.launch {
                while (isActive) {
                    try {
                        updateStream(stream)
                        delay(300000L)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                        delay(60000L)
                    }
                }
            }
        } else if (stream.viewerCount == null) {
            viewModelScope.launch {
                try {
                    updateStream(stream)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                }
            }
        }
    }

    private suspend fun updateStream(stream: Stream) {
        val account = Account.get(applicationContext)
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
        val s = repository.loadStream(
            channelId = stream.channelId,
            channelLogin = stream.channelLogin,
            helixClientId = applicationContext.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
            helixToken = account.helixToken,
            gqlHeaders = gqlHeaders,
            checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
        ).let {
            _stream.value?.apply {
                if (!it?.id.isNullOrBlank()) {
                    id = it?.id
                }
                gameId = it?.gameId
                gameSlug = it?.gameSlug
                gameName = it?.gameName
                title = it?.title
                viewerCount = it?.viewerCount
                startedAt = it?.startedAt
            }
        }
        _stream.postValue(s)
    }
}
