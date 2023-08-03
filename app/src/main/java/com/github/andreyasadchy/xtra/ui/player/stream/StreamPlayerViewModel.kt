package com.github.andreyasadchy.xtra.ui.player.stream

import android.content.Context
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
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class StreamPlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository) : PlayerViewModel(repository, localFollowsChannel) {

    val _stream = MutableLiveData<Stream?>()
    val stream: MutableLiveData<Stream?>
        get() = _stream

    var result = MutableLiveData<Triple<String, Int, Boolean>>()

    var useProxy: Int? = null
    var playingAds = false

    suspend fun checkPlaylist(url: String): Boolean {
        return try {
            val playlist = ByteArrayInputStream(playerRepository.getResponse(url = url).toByteArray()).use {
                PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
            }
            if (playlist.tracks.lastOrNull()?.trackInfo?.title?.let { it.contains("Amazon") || it.contains("Adform") || it.contains("DCM") } == true) {
                true
            } else {
                playlist.tracks.lastOrNull()?.programDateTime?.let { segmentStartTime ->
                    val segmentStartTimeMs = Util.parseXsDateTime(segmentStartTime)
                    playlist.unknownTags.find {
                        it.startsWith("#EXT-X-DATERANGE") &&
                                (Regex("ID=\"(.+?)\"").find(it)?.groupValues?.get(1)?.startsWith("stitched-ad-") == true ||
                                        Regex("CLASS=\"(.+?)\"").find(it)?.groupValues?.get(1) == "twitch-stitched-ad" ||
                                        Regex("X-TV-TWITCH-AD-.+?=\"(.+?)\"").find(it)?.groupValues?.get(1) != null) &&
                                (Regex("END-DATE=\"(.+?)\"").find(it)?.groupValues?.get(1)?.let { endDate ->
                                    segmentStartTimeMs < Util.parseXsDateTime(endDate)
                                } ?: Regex("START-DATE=\"(.+?)\"").find(it)?.groupValues?.get(1)?.let { startDate ->
                                    Regex("DURATION=(.+?)").find(it)?.groupValues?.get(1) ?: Regex("PLANNED-DURATION=(.+?)").find(it)?.groupValues?.get(1)?.let { duration ->
                                        segmentStartTimeMs < (Util.parseXsDateTime(startDate) + BigDecimal(duration).multiply(BigDecimal(1000L)).toLong())
                                    }
                                }) == true
                    } != null
                } == true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun load(gqlHeaders: Map<String, String>, channelLogin: String, proxyUrl: String?, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, proxyPlaybackAccessToken: Boolean, proxyMultivariantPlaylist: Boolean, proxyHost: String?, proxyPort: Int?, proxyUser: String?, proxyPassword: String?) {
        viewModelScope.launch {
            try {
                playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, useProxy, proxyUrl, randomDeviceId, xDeviceId, playerType, proxyPlaybackAccessToken, proxyMultivariantPlaylist, proxyHost, proxyPort, proxyUser, proxyPassword)
            } catch (e: Exception) {
                if (e.message == "failed integrity check") {
                    _integrity.postValue(true)
                }
                null
            }.let { result.postValue(it) }
        }
    }

    fun loadStream(context: Context, stream: Stream) {
        val account = Account.get(context)
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true)
        if (context.prefs().getBoolean(C.CHAT_DISABLE, false) || !context.prefs().getBoolean(C.CHAT_PUBSUB_ENABLED, true) || (context.prefs().getBoolean(C.CHAT_POINTS_COLLECT, true) && !account.id.isNullOrBlank() && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank())) {
            viewModelScope.launch {
                while (isActive) {
                    try {
                        updateStream(context, stream)
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
                    updateStream(context, stream)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                }
            }
        }
    }

    private suspend fun updateStream(context: Context, stream: Stream) {
        val account = Account.get(context)
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true)
        val s = repository.loadStream(
            channelId = stream.channelId,
            channelLogin = stream.channelLogin,
            helixClientId = context.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
            helixToken = account.helixToken,
            gqlHeaders = gqlHeaders,
            checkIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && context.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
        ).let {
            _stream.value?.apply {
                if (!it?.id.isNullOrBlank()) {
                    id = it?.id
                }
                gameId = it?.gameId
                gameName = it?.gameName
                title = it?.title
                viewerCount = it?.viewerCount
                startedAt = it?.startedAt
            }
        }
        _stream.postValue(s)
    }
}
