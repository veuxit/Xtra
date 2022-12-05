package com.github.andreyasadchy.xtra.ui.player.video

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.User
import com.github.andreyasadchy.xtra.model.VideoDownloadInfo
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.helix.game.Game
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.player.lowlatency.DefaultHlsPlaylistParserFactory
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.player.AudioPlayerService
import com.github.andreyasadchy.xtra.ui.player.HlsPlayerViewModel
import com.github.andreyasadchy.xtra.ui.player.PlayerMode
import com.github.andreyasadchy.xtra.util.*
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    context: Application,
    private val playerRepository: PlayerRepository,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    private val bookmarksRepository: BookmarksRepository) : HlsPlayerViewModel(context, repository, localFollowsChannel) {

    private lateinit var video: Video
    val videoInfo: VideoDownloadInfo?
        get() {
            val playlist = (player.currentManifest as? HlsManifest)?.mediaPlaylist ?: return null
            val segments = playlist.segments
            val size = segments.size
            val relativeTimes = ArrayList<Long>(size)
            val durations = ArrayList<Long>(size)
            for (i in 0 until size) {
                val segment = segments[i]
                relativeTimes.add(segment.relativeStartTimeUs / 1000L)
                durations.add(segment.durationUs / 1000L)
            }
            return VideoDownloadInfo(video, helper.urls, relativeTimes, durations, playlist.durationUs / 1000L, playlist.targetDurationUs / 1000L, player.currentPosition)
        }

    override val userId: String?
        get() { return video.user_id }
    override val userLogin: String?
        get() { return video.user_login }
    override val userName: String?
        get() { return video.user_name }
    override val channelLogo: String?
        get() { return video.channelLogo }

    val bookmarkItem = MutableLiveData<Bookmark>()
    val gamesList = MutableLiveData<List<Game>>()
    private var isLoading = false
    private var startOffset: Long = 0
    private var shouldRetry = true

    private val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
        .setPlaylistParserFactory(DefaultHlsPlaylistParserFactory())

    init {
        val speed = context.prefs().getFloat(C.PLAYER_SPEED, 1f)
        setSpeed(speed)
    }

    fun loadGamesList(clientId: String?, videoId: String?) {
        if (gamesList.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    val get = repository.loadVideoGames(clientId, videoId)
                    if (get.isNotEmpty()) {
                        gamesList.postValue(get)
                    }
                } catch (e: Exception) {
                    _errors.postValue(e)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    fun seek(position: Long) {
        player.seekTo(position)
    }

    fun setVideo(video: Video, offset: Double) {
        if (!this::video.isInitialized) {
            this.video = video
            startOffset = offset.toLong()
            playVideo((getApplication<Application>().prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2) <= 1)
        }
    }

    private fun playVideo(skipAccessToken: Boolean) {
        viewModelScope.launch {
            try {
                val url = if (skipAccessToken && !video.animatedPreviewURL.isNullOrBlank()) {
                    TwitchApiHelper.getVideoUrlFromPreview(video.animatedPreviewURL!!, video.type).toUri()
                } else {
                    val context = getApplication<Application>()
                    playerRepository.loadVideoPlaylistUrl(
                        gqlClientId = context.prefs().getString(C.GQL_CLIENT_ID, "kimne78kx3ncx6brgo4mv6wki5h1ko"),
                        gqlToken = if (context.prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_VIDEO, true)) User.get(context).gqlToken else null,
                        videoId = video.id,
                        playerType = context.prefs().getString(C.TOKEN_PLAYERTYPE_VIDEO, "channel_home_live")
                    )
                }
                mediaSource = hlsMediaSourceFactory.createMediaSource(MediaItem.fromUri(url))
                play()
                if (startOffset > 0) {
                    player.seekTo(startOffset)
                }
            } catch (e: Exception) {

            }
        }
    }

    override fun changeQuality(index: Int) {
        previousQuality = qualityIndex
        super.changeQuality(index)
        when {
            index < qualities.lastIndex -> {
                val audioOnly = playerMode.value == PlayerMode.AUDIO_ONLY
                if (audioOnly) {
                    playbackPosition = currentPlayer.value!!.currentPosition
                }
                setVideoQuality(index)
                if (audioOnly) {
                    player.seekTo(playbackPosition)
                }
            }
            index == qualities.lastIndex -> startAudioOnly()
        }
    }

    fun startAudioOnly(showNotification: Boolean = false) {
        (player.currentManifest as? HlsManifest)?.let {
            helper.urls.values.lastOrNull()?.let {
                startBackgroundAudio(it, video.user_name, video.title, video.channelLogo, true, AudioPlayerService.TYPE_VIDEO, video.id?.toLongOrNull(), showNotification)
                _playerMode.value = PlayerMode.AUDIO_ONLY
            }
        }
    }

    override fun onResume() {
        isResumed = true
        userLeaveHint = false
        if (playerMode.value == PlayerMode.NORMAL) {
            super.onResume()
        } else if (playerMode.value == PlayerMode.AUDIO_ONLY) {
            hideAudioNotification()
            if (qualityIndex != qualities.lastIndex) {
                changeQuality(qualityIndex)
            }
        }
        if (playerMode.value != PlayerMode.AUDIO_ONLY) {
            player.seekTo(playbackPosition)
        }
    }

    override fun onPause() {
        isResumed = false
        if (playerMode.value != PlayerMode.AUDIO_ONLY) {
            playbackPosition = player.currentPosition
        }
        val context = getApplication<Application>()
        if (!userLeaveHint && !isPaused() && playerMode.value == PlayerMode.NORMAL && context.prefs().getBoolean(C.PLAYER_LOCK_SCREEN_AUDIO, true)) {
            startAudioOnly(true)
        } else {
            super.onPause()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val playerError = player.playerError
        if (playerError != null) {
            if (playerError.type == ExoPlaybackException.TYPE_SOURCE &&
                playerError.sourceException.let { it is HttpDataSource.InvalidResponseCodeException }) {
                val context = getApplication<Application>()
                val skipToken = context.prefs().getString(C.TOKEN_SKIP_VIDEO_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
                when {
                    skipToken == 1 && shouldRetry -> {
                        shouldRetry = false
                        playVideo(false)
                    }
                    skipToken == 2 && shouldRetry -> {
                        shouldRetry = false
                        playVideo(true)
                    }
                    else -> {
                        if (playerError.sourceException.let { it is HttpDataSource.InvalidResponseCodeException && it.responseCode == 403 }) {
                            context.toast(R.string.video_subscribers_only)
                        }
                    }
                }
            } else {
                super.onPlayerError(error)
            }
        }
    }

    override fun onCleared() {
        if (playerMode.value == PlayerMode.NORMAL && this::video.isInitialized) { //TODO
            video.id?.toLongOrNull()?.let { playerRepository.saveVideoPosition(VideoPosition(it, player.currentPosition)) }
        }
        super.onCleared()
    }

    fun checkBookmark() {
        video.id?.let {
            viewModelScope.launch {
                bookmarkItem.postValue( bookmarksRepository.getBookmarkByVideoId(it))
            }
        }
    }

    fun saveBookmark(context: Context, helixClientId: String?, helixToken: String?, gqlClientId: String?) {
        GlobalScope.launch {
            if (bookmarkItem.value != null) {
                bookmarksRepository.deleteBookmark(context, bookmarkItem.value!!)
            } else {
                if (!video.id.isNullOrBlank()) {
                    try {
                        Glide.with(context)
                            .asBitmap()
                            .load(video.thumbnail)
                            .into(object: CustomTarget<Bitmap>() {
                                override fun onLoadCleared(placeholder: Drawable?) {

                                }

                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    DownloadUtils.savePng(context, "thumbnails", video.id!!, resource)
                                }
                            })
                    } catch (e: Exception) {

                    }
                }
                if (!video.channelId.isNullOrBlank()) {
                    try {
                        Glide.with(context)
                            .asBitmap()
                            .load(video.channelLogo)
                            .into(object: CustomTarget<Bitmap>() {
                                override fun onLoadCleared(placeholder: Drawable?) {

                                }

                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    DownloadUtils.savePng(context, "profile_pics", video.channelId!!, resource)
                                }
                            })
                    } catch (e: Exception) {

                    }
                }
                val userTypes = video.channelId?.let { repository.loadUserTypes(listOf(it), helixClientId, helixToken, gqlClientId) }?.first()
                val downloadedThumbnail = video.id?.let { File(context.filesDir.toString() + File.separator + "thumbnails" + File.separator + "${it}.png").absolutePath }
                val downloadedLogo = video.channelId?.let { File(context.filesDir.toString() + File.separator + "profile_pics" + File.separator + "${it}.png").absolutePath }
                bookmarksRepository.saveBookmark(Bookmark(
                    videoId = video.id,
                    userId = video.channelId,
                    userLogin = video.channelLogin,
                    userName = video.channelName,
                    userType = userTypes?.type,
                    userBroadcasterType = userTypes?.broadcaster_type,
                    userLogo = downloadedLogo,
                    gameId = video.gameId,
                    gameName = video.gameName,
                    title = video.title,
                    createdAt = video.created_at,
                    thumbnail = downloadedThumbnail,
                    type = video.type,
                    duration = video.duration,
                    animatedPreviewURL = video.animatedPreviewURL
                ))
            }
        }
    }
}