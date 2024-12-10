package com.github.andreyasadchy.xtra.ui.player

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoDownloadInfo
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.player.lowlatency.DefaultHlsPlaylistParserFactory
import com.github.andreyasadchy.xtra.player.lowlatency.DefaultHlsPlaylistTracker
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.LinkedList
import java.util.Timer
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.concurrent.schedule


@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var offlineRepository: OfflineRepository

    private var mediaSession: MediaSession? = null
    private var playerMode = PlayerMode.NORMAL
    private var background = false
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var usingProxy = false
    private var stopProxy = false

    override fun onCreate() {
        super.onCreate()
        val prefs = prefs()
        val player = ExoPlayer.Builder(this).apply {
            setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(
                prefs.getString(C.PLAYER_BUFFER_MIN, "15000")?.toIntOrNull() ?: 15000,
                prefs.getString(C.PLAYER_BUFFER_MAX, "50000")?.toIntOrNull() ?: 50000,
                prefs.getString(C.PLAYER_BUFFER_PLAYBACK, "2000")?.toIntOrNull() ?: 2000,
                prefs.getString(C.PLAYER_BUFFER_REBUFFER, "5000")?.toIntOrNull() ?: 5000
            ).build())
            setAudioAttributes(AudioAttributes.DEFAULT, prefs.getBoolean(C.PLAYER_AUDIO_FOCUS, false))
            setHandleAudioBecomingNoisy(prefs.getBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true))
            setSeekBackIncrementMs(prefs.getString(C.PLAYER_REWIND, "10000")?.toLongOrNull() ?: 10000)
            setSeekForwardIncrementMs(prefs.getString(C.PLAYER_FORWARD, "10000")?.toLongOrNull() ?: 10000)
        }.build().apply {
            if (item != null) {
                (urls.values.elementAtOrNull(qualityUrlIndex) ?: urls.values.elementAtOrNull(previousUrlIndex) ?: urls.values.firstOrNull())?.let { url ->
                    when (item) {
                        is Stream -> {
                            (item as Stream).let { item ->
                                HlsMediaSource.Factory(DefaultDataSource.Factory(this@PlaybackService, OkHttpDataSource.Factory(okHttpClient).apply {
                                    headers?.let {
                                        setDefaultRequestProperties(it)
                                    }
                                })).apply {
                                    setPlaylistParserFactory(DefaultHlsPlaylistParserFactory())
                                    setPlaylistTrackerFactory(DefaultHlsPlaylistTracker.FACTORY)
                                    setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
                                    if (prefs.getBoolean(C.PLAYER_SUBTITLES, false) || prefs.getBoolean(C.PLAYER_MENU_SUBTITLES, false)) {
                                        setAllowChunklessPreparation(false)
                                    }
                                }.createMediaSource(MediaItem.Builder().apply {
                                    setUri(url)
                                    setMimeType(MimeTypes.APPLICATION_M3U8)
                                    setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                                        prefs.getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                                        prefs.getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                                        prefs.getString(C.PLAYER_LIVE_TARGET_OFFSET, "5000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                                    }.build())
                                    setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(item.title)
                                        .setArtist(item.channelName)
                                        .setArtworkUri(item.channelLogo?.toUri())
                                        .build())
                                }.build()).let { setMediaSource(it) }
                                volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                setPlaybackSpeed(1f)
                                prepare()
                                playWhenReady = true
                                setVideoQuality()
                            }
                        }
                        is Video -> {
                            (item as Video).let { item ->
                                HlsMediaSource.Factory(DefaultDataSource.Factory(this@PlaybackService, OkHttpDataSource.Factory(okHttpClient))).apply {
                                    setPlaylistParserFactory(DefaultHlsPlaylistParserFactory())
                                    if (usingPlaylist && (prefs.getBoolean(C.PLAYER_SUBTITLES, false) || prefs.getBoolean(C.PLAYER_MENU_SUBTITLES, false))) {
                                        setAllowChunklessPreparation(false)
                                    }
                                }.createMediaSource(MediaItem.Builder()
                                    .setUri(url)
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(item.title)
                                        .setArtist(item.channelName)
                                        .setArtworkUri(item.channelLogo?.toUri())
                                        .build())
                                    .build()
                                ).let { setMediaSource(it) }
                                volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                prepare()
                                playWhenReady = true
                                seekTo(if (prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) getPosition() else 0)
                                setVideoQuality()
                            }
                        }
                        is Clip -> {
                            (item as Clip).let { item ->
                                setMediaItem(MediaItem.Builder()
                                    .setUri(url)
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(item.title)
                                        .setArtist(item.channelName)
                                        .setArtworkUri(item.channelLogo?.toUri())
                                        .build())
                                    .build())
                                volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                prepare()
                                playWhenReady = true
                                setVideoQuality()
                            }
                        }
                        is OfflineVideo -> {
                            (item as OfflineVideo).let { item ->
                                setMediaItem(MediaItem.Builder()
                                    .setUri(url)
                                    .setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(item.name)
                                        .setArtist(item.channelName)
                                        .build())
                                    .build())
                                volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                prepare()
                                playWhenReady = true
                                seekTo(if (prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) getPosition() else 0)
                                setVideoQuality()
                            }
                        }
                    }
                }
            }
            reinitializeDynamicsProcessing(audioSessionId)
            addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (!tracks.isEmpty && usingPlaylist && (qualityIndex != -1 && qualityIndex != audioIndex)) {
                        updateVideoQuality(qualityUrlIndex)
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    val manifest = mediaSession?.player?.currentManifest as? HlsManifest
                    if (urls.isEmpty() && manifest is HlsManifest) {
                        manifest.multivariantPlaylist.let { playlist ->
                            val tags = playlist.tags
                            val qualityNames = mutableListOf<String>()
                            val codecs = mutableListOf<String>()
                            val map = mutableMapOf<String, Pair<String, String>>()
                            val audioOnly = ContextCompat.getString(this@PlaybackService, R.string.audio_only)
                            val qualityPattern = Pattern.compile("NAME=\"(.+?)\"")
                            val codecPattern = Pattern.compile("CODECS=\"(.+?)\\.")
                            var trackIndex = 0
                            tags.forEach { tag ->
                                if (tag.startsWith("#EXT-X-MEDIA")) {
                                    val matcher = qualityPattern.matcher(tag)
                                    if (matcher.find()) {
                                        val quality = matcher.group(1)!!
                                        qualityNames.add(quality)
                                    }
                                }
                                if (tag.startsWith("#EXT-X-STREAM-INF")) {
                                    val matcher = codecPattern.matcher(tag)
                                    if (matcher.find()) {
                                        val codec = matcher.group(1)!!
                                        codecs.add(when(codec) {
                                            "av01" -> "AV1"
                                            "hvc1" -> "H.265"
                                            "avc1" -> "H.264"
                                            else -> codec
                                        })
                                    }
                                }
                            }
                            if (codecs.all { it == "H.264" || it == "mp4a" }) {
                                codecs.clear()
                            }
                            qualityNames.forEachIndexed { index, quality ->
                                val url = playlist.variants[trackIndex++].url.toString()
                                if (!quality.startsWith("audio", true)) {
                                    val name = codecs.getOrNull(index)?.let { codec ->
                                        "$quality $codec"
                                    } ?: quality
                                    map[name] = Pair(quality, url)
                                } else {
                                    map[audioOnly] = Pair("audio_only", url)
                                }
                            }
                            map.apply {
                                if (containsKey(audioOnly)) {
                                    remove(audioOnly)?.let { url ->
                                        put(audioOnly, url) //move audio option to bottom
                                    }
                                } else {
                                    put(audioOnly, Pair("audio_only", ""))
                                }
                            }
                            urls = map.values.associate { it.first to it.second }
                            qualities = LinkedList(map.keys).apply {
                                if (usingAutoQuality) {
                                    addFirst(ContextCompat.getString(this@PlaybackService, R.string.auto))
                                }
                                if (usingChatOnlyQuality) {
                                    add(ContextCompat.getString(this@PlaybackService, R.string.chat_only))
                                }
                            }
                            setQualityIndex()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (background) {
                        prepare()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    reinitializeDynamicsProcessing(audioSessionId)
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(PendingIntent.getActivity(this, REQUEST_CODE_RESUME,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    action = MainActivity.INTENT_OPEN_PLAYER
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                        .add(SessionCommand(START_STREAM, Bundle.EMPTY))
                        .add(SessionCommand(START_VIDEO, Bundle.EMPTY))
                        .add(SessionCommand(START_CLIP, Bundle.EMPTY))
                        .add(SessionCommand(START_OFFLINE_VIDEO, Bundle.EMPTY))
                        .add(SessionCommand(CHANGE_QUALITY, Bundle.EMPTY))
                        .add(SessionCommand(START_AUDIO_ONLY, Bundle.EMPTY))
                        .add(SessionCommand(SWITCH_AUDIO_MODE, Bundle.EMPTY))
                        .add(SessionCommand(TOGGLE_DYNAMICS_PROCESSING, Bundle.EMPTY))
                        .add(SessionCommand(TOGGLE_PROXY, Bundle.EMPTY))
                        .add(SessionCommand(MOVE_BACKGROUND, Bundle.EMPTY))
                        .add(SessionCommand(MOVE_FOREGROUND, Bundle.EMPTY))
                        .add(SessionCommand(CLEAR, Bundle.EMPTY))
                        .add(SessionCommand(GET_URLS, Bundle.EMPTY))
                        .add(SessionCommand(GET_LAST_TAG, Bundle.EMPTY))
                        .add(SessionCommand(GET_QUALITIES, Bundle.EMPTY))
                        .add(SessionCommand(GET_QUALITY_TEXT, Bundle.EMPTY))
                        .add(SessionCommand(GET_MEDIA_PLAYLIST, Bundle.EMPTY))
                        .add(SessionCommand(GET_MULTIVARIANT_PLAYLIST, Bundle.EMPTY))
                        .add(SessionCommand(GET_VIDEO_DOWNLOAD_INFO, Bundle.EMPTY))
                        .add(SessionCommand(GET_ERROR_CODE, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(sessionCommands, connectionResult.availablePlayerCommands)
                }

                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    return when (customCommand.customAction) {
                        START_STREAM -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                customCommand.customExtras.getParcelable(ITEM, Stream::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                customCommand.customExtras.getParcelable(ITEM)
                            }?.let { item ->
                                usingProxy = false
                                stopProxy = false
                                usingPlaylist = true
                                usingAutoQuality = true
                                usingChatOnlyQuality = true
                                val uri = customCommand.customExtras.getString(URI)
                                val headers = args.getStringArray(HEADERS_KEYS)?.let { keys ->
                                    args.getStringArray(HEADERS_VALUES)?.let { values ->
                                        keys.zip(values).toMap(mutableMapOf())
                                    }
                                }
                                val playlistAsData = customCommand.customExtras.getBoolean(PLAYLIST_AS_DATA)
                                Companion.item = item
                                Companion.headers = headers
                                HlsMediaSource.Factory(DefaultDataSource.Factory(this@PlaybackService, OkHttpDataSource.Factory(okHttpClient).apply {
                                    if (headers != null) {
                                        setDefaultRequestProperties(headers)
                                    }
                                })).apply {
                                    setPlaylistParserFactory(DefaultHlsPlaylistParserFactory())
                                    setPlaylistTrackerFactory(DefaultHlsPlaylistTracker.FACTORY)
                                    setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
                                    if (prefs.getBoolean(C.PLAYER_SUBTITLES, false) || prefs.getBoolean(C.PLAYER_MENU_SUBTITLES, false)) {
                                        setAllowChunklessPreparation(false)
                                    }
                                }.createMediaSource(MediaItem.Builder().apply {
                                    if (playlistAsData) {
                                        setUri("data:;base64,${uri}")
                                    } else {
                                        setUri(uri?.toUri())
                                    }
                                    setMimeType(MimeTypes.APPLICATION_M3U8)
                                    setLiveConfiguration(MediaItem.LiveConfiguration.Builder().apply {
                                        prefs.getString(C.PLAYER_LIVE_MIN_SPEED, "")?.toFloatOrNull()?.let { setMinPlaybackSpeed(it) }
                                        prefs.getString(C.PLAYER_LIVE_MAX_SPEED, "")?.toFloatOrNull()?.let { setMaxPlaybackSpeed(it) }
                                        prefs.getString(C.PLAYER_LIVE_TARGET_OFFSET, "5000")?.toLongOrNull()?.let { setTargetOffsetMs(it) }
                                    }.build())
                                    setMediaMetadata(MediaMetadata.Builder()
                                        .setTitle(item.title)
                                        .setArtist(item.channelName)
                                        .setArtworkUri(item.channelLogo?.toUri())
                                        .build())
                                }.build()).let { (session.player as? ExoPlayer)?.setMediaSource(it) }
                                session.player.volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                session.player.setPlaybackSpeed(1f)
                                session.player.prepare()
                                session.player.playWhenReady = true
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        START_VIDEO -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                customCommand.customExtras.getParcelable(ITEM, Video::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                customCommand.customExtras.getParcelable(ITEM)
                            }?.let { item ->
                                val usingPlaylist = customCommand.customExtras.getBoolean(USING_PLAYLIST)
                                if (usingPlaylist) {
                                    customCommand.customExtras.getString(URI)?.toUri()
                                } else {
                                    item.animatedPreviewURL?.let { preview ->
                                        val qualityMap = TwitchApiHelper.getVideoUrlMapFromPreview(preview, item.type)
                                        val map = mutableMapOf<String, Pair<String, String>>()
                                        qualityMap.forEach {
                                            if (it.key == "source") {
                                                map[ContextCompat.getString(this@PlaybackService, R.string.source)] = Pair(it.key, it.value)
                                            } else {
                                                map[it.key] = Pair(it.key, it.value)
                                            }
                                        }
                                        map.apply {
                                            if (containsKey("audio_only")) {
                                                remove("audio_only")?.let { url ->
                                                    put(ContextCompat.getString(this@PlaybackService, R.string.audio_only), url) //move audio option to bottom
                                                }
                                            } else {
                                                put(ContextCompat.getString(this@PlaybackService, R.string.audio_only), Pair("audio_only", ""))
                                            }
                                        }
                                        urls = map.values.associate { it.first to it.second }
                                        qualities = LinkedList(map.keys).apply {
                                            addFirst(ContextCompat.getString(this@PlaybackService, R.string.auto))
                                        }
                                        qualityIndex = 1
                                        urls.values.first().toUri()
                                    }
                                }?.let { url ->
                                    usingAutoQuality = true
                                    Companion.usingPlaylist = usingPlaylist
                                    Companion.item = item
                                    HlsMediaSource.Factory(DefaultDataSource.Factory(this@PlaybackService, OkHttpDataSource.Factory(okHttpClient))).apply {
                                        setPlaylistParserFactory(DefaultHlsPlaylistParserFactory())
                                        if (usingPlaylist && (prefs.getBoolean(C.PLAYER_SUBTITLES, false) || prefs.getBoolean(C.PLAYER_MENU_SUBTITLES, false))) {
                                            setAllowChunklessPreparation(false)
                                        }
                                    }.createMediaSource(MediaItem.Builder()
                                        .setUri(url)
                                        .setMediaMetadata(MediaMetadata.Builder()
                                            .setTitle(item.title)
                                            .setArtist(item.channelName)
                                            .setArtworkUri(item.channelLogo?.toUri())
                                            .build())
                                        .build()
                                    ).let { (session.player as? ExoPlayer)?.setMediaSource(it) }
                                    session.player.volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                    session.player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                    session.player.prepare()
                                    session.player.playWhenReady = true
                                    session.player.seekTo(customCommand.customExtras.getLong(PLAYBACK_POSITION))
                                }
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        START_CLIP -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                customCommand.customExtras.getParcelable(ITEM, Clip::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                customCommand.customExtras.getParcelable(ITEM)
                            }?.let { item ->
                                customCommand.customExtras.getStringArray(URLS_KEYS)?.let { keys ->
                                    customCommand.customExtras.getStringArray(URLS_VALUES)?.let { values ->
                                        val map = mutableMapOf<String, Pair<String, String>>()
                                        keys.forEachIndexed { index, key ->
                                            if (key == "source") {
                                                map[ContextCompat.getString(this@PlaybackService, R.string.source)] = Pair(key, values[index])
                                            } else {
                                                map[key] = Pair(key, values[index])
                                            }
                                        }
                                        map.apply {
                                            if (containsKey("audio_only")) {
                                                remove("audio_only")?.let { url ->
                                                    put(ContextCompat.getString(this@PlaybackService, R.string.audio_only), url) //move audio option to bottom
                                                }
                                            } else {
                                                put(ContextCompat.getString(this@PlaybackService, R.string.audio_only), Pair("audio_only", ""))
                                            }
                                        }
                                        Companion.item = item
                                        urls = map.values.associate { it.first to it.second }
                                        qualities = LinkedList(map.keys)
                                        setQualityIndex()
                                        (map.values.elementAtOrNull(qualityUrlIndex) ?: map.values.firstOrNull())?.second?.let { url ->
                                            session.player.setMediaItem(MediaItem.Builder()
                                                .setUri(url)
                                                .setMediaMetadata(MediaMetadata.Builder()
                                                    .setTitle(item.title)
                                                    .setArtist(item.channelName)
                                                    .setArtworkUri(item.channelLogo?.toUri())
                                                    .build())
                                                .build())
                                            session.player.volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                            session.player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                            session.player.prepare()
                                            session.player.playWhenReady = true
                                        }
                                    }
                                }
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        START_OFFLINE_VIDEO -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                customCommand.customExtras.getParcelable(ITEM, OfflineVideo::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                customCommand.customExtras.getParcelable(ITEM)
                            }?.let { item ->
                                item.url?.let { url ->
                                    Companion.item = item
                                    val map = mapOf(
                                        ContextCompat.getString(this@PlaybackService, R.string.source) to Pair("source", url),
                                        ContextCompat.getString(this@PlaybackService, R.string.audio_only) to Pair("audio_only", "")
                                    )
                                    urls = map.values.associate { it.first to it.second }
                                    qualities = LinkedList(map.keys)
                                    session.player.setMediaItem(MediaItem.Builder()
                                        .setUri(url)
                                        .setMediaMetadata(MediaMetadata.Builder()
                                            .setTitle(item.name)
                                            .setArtist(item.channelName)
                                            .setArtworkUri(item.channelLogo?.toUri())
                                            .build())
                                        .build())
                                    session.player.volume = prefs.getInt(C.PLAYER_VOLUME, 100) / 100f
                                    session.player.setPlaybackSpeed(prefs().getFloat(C.PLAYER_SPEED, 1f))
                                    session.player.prepare()
                                    session.player.playWhenReady = true
                                    session.player.seekTo(customCommand.customExtras.getLong(PLAYBACK_POSITION))
                                }
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CHANGE_QUALITY -> {
                            val index = customCommand.customExtras.getInt(INDEX)
                            changeQuality(index)
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to playerMode)))
                        }
                        START_AUDIO_ONLY -> {
                            startAudioOnly()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to playerMode)))
                        }
                        SWITCH_AUDIO_MODE -> {
                            if (playerMode != PlayerMode.AUDIO_ONLY) {
                                changeQuality(audioIndex)
                            } else {
                                changeQuality(previousIndex)
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to playerMode)))
                        }
                        TOGGLE_DYNAMICS_PROCESSING -> {
                            toggleDynamicsProcessing()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to dynamicsProcessing?.enabled)))
                        }
                        TOGGLE_PROXY -> {
                            if (!stopProxy) {
                                toggleProxy(customCommand.customExtras.getBoolean(USING_PROXY))
                            }
                            if (customCommand.customExtras.getBoolean(STOP_PROXY)) {
                                stopProxy = true
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        MOVE_BACKGROUND -> {
                            val pipMode = customCommand.customExtras.getBoolean(PIP_MODE)
                            if (prefs.getString(C.PLAYER_BACKGROUND_PLAYBACK, "0") == "2") {
                                savePosition()
                                session.player.stop()
                            } else {
                                if (playerMode == PlayerMode.NORMAL) {
                                    if (!pipMode && session.player.playbackState != Player.STATE_ENDED && session.player.playbackState != Player.STATE_IDLE && session.player.playWhenReady) {
                                        startAudioOnly()
                                    } else {
                                        savePosition()
                                        session.player.stop()
                                    }
                                }
                            }
                            background = true
                            val duration = customCommand.customExtras.getLong(DURATION)
                            if (duration > 0L) {
                                sleepTimer = Timer().apply {
                                    schedule(duration) {
                                        Handler(Looper.getMainLooper()).post {
                                            savePosition()
                                            session.player.pause()
                                            session.player.stop()
                                            stopSelf()
                                        }
                                    }
                                }
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to playerMode)))
                        }
                        MOVE_FOREGROUND -> {
                            background = false
                            sleepTimer?.let {
                                it.cancel()
                                sleepTimer = null
                            }
                            if (usingProxy) {
                                toggleProxy(false)
                            }
                            if (playerMode == PlayerMode.NORMAL) {
                                session.player.prepare()
                            } else if (playerMode == PlayerMode.AUDIO_ONLY) {
                                if (qualityIndex != audioIndex) {
                                    setVideoQuality()
                                }
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to playerMode)))
                        }
                        CLEAR -> {
                            clear()
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        GET_URLS -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                            URLS_KEYS to urls.keys.toTypedArray(),
                            URLS_VALUES to urls.values.toTypedArray()
                        )))
                        GET_LAST_TAG -> {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                                RESULT to (session.player.currentManifest as? HlsManifest)?.mediaPlaylist?.tags?.lastOrNull(),
                                USING_PROXY to usingProxy,
                                STOP_PROXY to stopProxy,
                                ITEM to (urls.values.elementAtOrNull(qualityUrlIndex) ?: urls.values.firstOrNull())
                            )))
                        }
                        GET_QUALITIES -> {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                                RESULT to qualities.toTypedArray(),
                                INDEX to qualityIndex,
                            )))
                        }
                        GET_QUALITY_TEXT -> {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(RESULT to qualities.getOrNull(qualityIndex))))
                        }
                        GET_MEDIA_PLAYLIST -> {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                                RESULT to (session.player.currentManifest as? HlsManifest)?.mediaPlaylist?.tags?.dropLastWhile { it == "ads=true" }?.joinToString("\n")
                            )))
                        }
                        GET_MULTIVARIANT_PLAYLIST -> {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                                RESULT to (session.player.currentManifest as? HlsManifest)?.multivariantPlaylist?.tags?.joinToString("\n")
                            )))
                        }
                        GET_VIDEO_DOWNLOAD_INFO -> {
                            val info = (session.player.currentManifest as? HlsManifest)?.mediaPlaylist?.let { playlist ->
                                VideoDownloadInfo(
                                    qualities = urls,
                                    totalDuration = playlist.durationUs / 1000L,
                                    currentPosition = session.player.currentPosition
                                )
                            }
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                                RESULT to info
                            )))
                        }
                        GET_ERROR_CODE -> {
                            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, bundleOf(
                                RESULT to (session.player.playerError?.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode,
                            )))
                        }
                        else -> super.onCustomCommand(session, controller, customCommand, args)
                    }
                }
            })
            .build()
    }

    private fun changeQuality(index: Int) {
        previousIndex = qualityIndex
        qualityIndex = index
        setVideoQuality()
    }

    private fun setVideoQuality() {
        mediaSession?.player?.let { player ->
            val mode = playerMode
            if (mode != PlayerMode.NORMAL) {
                playerMode = PlayerMode.NORMAL
                if (mode == PlayerMode.AUDIO_ONLY) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
                    }.build()
                    if (player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO } == null) {
                        val position = player.currentPosition
                        mediaItem?.let { player.setMediaItem(it) }
                        player.prepare()
                        player.seekTo(position)
                    }
                } else if (mode == PlayerMode.DISABLED) {
                    player.prepare()
                }
            }
            when {
                qualityIndex > audioIndex -> {
                    player.stop()
                    playerMode = PlayerMode.DISABLED
                }
                qualityIndex == audioIndex -> {
                    if (usingProxy) {
                        toggleProxy(false)
                    }
                    val urlIndex = if (!urls.entries.elementAtOrNull(audioUrlIndex)?.value.isNullOrBlank()) {
                        audioUrlIndex
                    } else {
                        if (!urls.entries.elementAtOrNull(previousUrlIndex)?.value.isNullOrBlank()) {
                            previousUrlIndex
                        } else {
                            0
                        }
                    }
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                        setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                    }.build()
                    playerMode = PlayerMode.AUDIO_ONLY
                    updateVideoQuality(urlIndex)
                }
                else -> {
                    updateVideoQuality(qualityUrlIndex)
                    if (prefs().getString(C.PLAYER_DEFAULTQUALITY, "saved") == "saved") {
                        if (qualityIndex == -1) {
                            "Auto"
                        } else {
                            if (qualityIndex < audioIndex) qualities.getOrNull(qualityIndex) else null
                        }?.let { prefs().edit { putString(C.PLAYER_QUALITY, it) } }
                    }
                }
            }
        }
    }

    private fun startAudioOnly() {
        mediaSession?.player?.let { player ->
            if (usingProxy) {
                toggleProxy(false)
            }
            val urlIndex = if (prefs().getBoolean(C.PLAYER_USE_BACKGROUND_AUDIO_TRACK, false) && !urls.entries.elementAtOrNull(audioUrlIndex)?.value.isNullOrBlank()) {
                audioUrlIndex
            } else {
                if (!urls.entries.elementAtOrNull(qualityUrlIndex)?.value.isNullOrBlank()) {
                    qualityUrlIndex
                } else {
                    0
                }
            }
            if (prefs().getBoolean(C.PLAYER_DISABLE_BACKGROUND_VIDEO, true)) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
                }.build()
            }
            playerMode = PlayerMode.AUDIO_ONLY
            updateVideoQuality(urlIndex)
        }
    }

    private fun updateVideoQuality(urlIndex: Int) {
        mediaSession?.player?.let { player ->
            if (!usingPlaylist || urlIndex == audioUrlIndex) {
                urls.values.elementAtOrNull(urlIndex)?.let { url ->
                    val position = player.currentPosition
                    player.currentMediaItem?.let {
                        mediaItem = it
                        player.setMediaItem(it.buildUpon().setUri(url).build())
                    }
                    player.prepare()
                    player.seekTo(position)
                }
            } else {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                    if (urlIndex == -1) {
                        clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
                    } else {
                        if (!player.currentTracks.isEmpty) {
                            player.currentTracks.groups.find { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }?.let {
                                if (it.length - 1 >= urlIndex) {
                                    setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, urlIndex))
                                }
                            }
                        }
                    }
                }.build()
            }
        }
    }

    private fun setQualityIndex() {
        val defaultQuality = prefs().getString(C.PLAYER_DEFAULTQUALITY, "saved")?.substringBefore(" ")
        val savedQuality = prefs().getString(C.PLAYER_QUALITY, "720p60")?.substringBefore(" ")
        val index = when (defaultQuality) {
            "Source" -> {
                if (usingAutoQuality) {
                    if (qualities.size >= 2) 1 else null
                } else null
            }
            "saved" -> {
                if (savedQuality != "Auto") {
                    findQualityIndex(savedQuality)
                } else null
            }
            else -> findQualityIndex(defaultQuality)
        }
        qualityIndex = index ?: 0
    }

    private fun findQualityIndex(targetQualityString: String?): Int? {
        return targetQualityString?.split("p")?.let { targetQuality ->
            targetQuality[0].filter(Char::isDigit).toIntOrNull()?.let { targetRes ->
                val targetFps = if (targetQuality.size >= 2) targetQuality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                qualities.indexOf(qualities.find { qualityString ->
                    qualityString.split("p").let { quality ->
                        quality[0].filter(Char::isDigit).toIntOrNull()?.let { qualityRes ->
                            val qualityFps = if (quality.size >= 2) quality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                            (targetRes == qualityRes && targetFps >= qualityFps) || targetRes > qualityRes || qualities.indexOf(qualityString) == audioIndex - 1
                        } ?: false
                    }
                }).let { if (it != -1) it else null }
            }
        }
    }

    private fun toggleDynamicsProcessing() {
        (mediaSession?.player as? ExoPlayer)?.let { player ->
            val enable = !prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)
            prefs().edit { putBoolean(C.PLAYER_AUDIO_COMPRESSOR, enable) }
            if (enable) {
                if (dynamicsProcessing == null) {
                    reinitializeDynamicsProcessing(player.audioSessionId)
                } else {
                    dynamicsProcessing?.enabled = true
                }
            } else {
                dynamicsProcessing?.enabled = false
            }
        }
    }

    private fun reinitializeDynamicsProcessing(audioSessionId: Int) {
        dynamicsProcessing?.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs().getBoolean(C.PLAYER_AUDIO_COMPRESSOR, false)) {
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, null).apply {
                for (channelIdx in 0 until channelCount) {
                    for (bandIdx in 0 until getMbcByChannelIndex(channelIdx).bandCount) {
                        setMbcBandByChannelIndex(channelIdx, bandIdx,
                            getMbcBandByChannelIndex(channelIdx, bandIdx).apply {
                                attackTime = 0f
                                releaseTime = 0.25f
                                ratio = 1.6f
                                threshold = -50f
                                kneeWidth = 40f
                                preGain = 0f
                                postGain = 10f
                            })
                    }
                }
                enabled = true
            }
        }
    }

    private fun toggleProxy(enable: Boolean) {
        usingProxy = enable
        mediaSession?.player?.currentMediaItem?.let { item ->
            val proxyHost = prefs().getString(C.PROXY_HOST, null)
            val proxyPort = prefs().getString(C.PROXY_PORT, null)?.toIntOrNull()
            val proxyUser = prefs().getString(C.PROXY_USER, null)
            val proxyPassword = prefs().getString(C.PROXY_PASSWORD, null)
            HlsMediaSource.Factory(DefaultDataSource.Factory(this@PlaybackService, OkHttpDataSource.Factory(
                if (enable && !proxyHost.isNullOrBlank() && proxyPort != null) {
                    okHttpClient.newBuilder().apply {
                        proxySelector(object : ProxySelector() {
                            override fun select(u: URI): List<Proxy> {
                                return if (Regex("video-weaver\\.\\w+\\.hls\\.ttvnw\\.net").matches(u.host)) {
                                    listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)), Proxy.NO_PROXY)
                                } else {
                                    listOf(Proxy.NO_PROXY)
                                }
                            }

                            override fun connectFailed(u: URI, sa: SocketAddress, e: IOException) {}
                        })
                        if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
                            proxyAuthenticator { _, response ->
                                response.request.newBuilder().header(
                                    "Proxy-Authorization", Credentials.basic(proxyUser, proxyPassword)
                                ).build()
                            }
                        }
                    }.build()
                } else okHttpClient
            ).apply {
                headers?.let { setDefaultRequestProperties(it) }
            })).apply {
                setPlaylistParserFactory(DefaultHlsPlaylistParserFactory())
                setPlaylistTrackerFactory(DefaultHlsPlaylistTracker.FACTORY)
                setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
                if (prefs().getBoolean(C.PLAYER_SUBTITLES, false) || prefs().getBoolean(C.PLAYER_MENU_SUBTITLES, false)) {
                    setAllowChunklessPreparation(false)
                }
            }.createMediaSource(item).let { (mediaSession?.player as? ExoPlayer)?.setMediaSource(it) }
        }
    }

    private fun clear() {
        savePosition()
        item = null
        mediaItem = null
        headers = null
        playerMode = PlayerMode.NORMAL
        usingPlaylist = false
        usingAutoQuality = false
        usingChatOnlyQuality = false
        urls = emptyMap()
        qualities = emptyList()
        qualityIndex = 0
        previousIndex = 0
        mediaSession?.player?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
                setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, false)
            }.build()
            player.removeMediaItem(0)
        }
        sleepTimer?.let {
            it.cancel()
            sleepTimer = null
        }
    }

    private fun getPosition(): Long {
        return item?.let { item ->
            when (item) {
                is Video -> {
                    item.id?.toLongOrNull()?.let { id ->
                        runBlocking {
                            playerRepository.getVideoPosition(id)?.position
                        }
                    }
                }
                is OfflineVideo -> {
                    runBlocking {
                        offlineRepository.getVideoById(item.id)?.lastWatchPosition
                    }
                }
                else -> 0
            }
        } ?: 0
    }

    private fun savePosition() {
        item?.let { item ->
            mediaSession?.player?.let { player ->
                if (!player.currentTracks.isEmpty && prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true)) {
                    when (item) {
                        is Video -> {
                            item.id?.toLongOrNull()?.let { id ->
                                runBlocking {
                                    playerRepository.saveVideoPosition(VideoPosition(id, player.currentPosition))
                                }
                            }
                        }
                        is OfflineVideo -> {
                            runBlocking {
                                offlineRepository.updateVideoPosition(item.id, player.currentPosition)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        mediaSession?.player?.pause()
        mediaSession?.player?.stop()
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private var item: Parcelable? = null
        private var mediaItem: MediaItem? = null
        private var headers: Map<String, String>? = null

        private var usingPlaylist = false
        private var usingAutoQuality = false
        private var usingChatOnlyQuality = false

        private var urls: Map<String, String> = emptyMap()
        private var qualities: List<String> = emptyList()
        private var qualityIndex = 0
        private val qualityUrlIndex: Int
            get() = if (usingAutoQuality) qualityIndex - 1 else qualityIndex
        private var previousIndex = 0
        private val previousUrlIndex: Int
            get() = if (usingAutoQuality) previousIndex - 1 else previousIndex
        private val audioIndex: Int
            get() = if (usingChatOnlyQuality) qualities.lastIndex - 1 else qualities.lastIndex
        private val audioUrlIndex: Int
            get() = urls.size - 1

        private var sleepTimer: Timer? = null

        const val START_STREAM = "startStream"
        const val START_VIDEO = "startVideo"
        const val START_CLIP = "startClip"
        const val START_OFFLINE_VIDEO = "startOfflineVideo"

        const val CHANGE_QUALITY = "changeQuality"
        const val START_AUDIO_ONLY = "startAudioOnly"
        const val SWITCH_AUDIO_MODE = "switchAudioMode"
        const val TOGGLE_DYNAMICS_PROCESSING = "toggleDynamicsProcessing"
        const val TOGGLE_PROXY = "toggleProxy"
        const val MOVE_BACKGROUND = "moveBackground"
        const val MOVE_FOREGROUND = "moveForeground"
        const val CLEAR = "clear"

        const val GET_URLS = "getUrls"
        const val GET_LAST_TAG = "getLastTag"
        const val GET_QUALITIES = "getQualities"
        const val GET_QUALITY_TEXT = "getQualityText"
        const val GET_MEDIA_PLAYLIST = "getMediaPlaylist"
        const val GET_MULTIVARIANT_PLAYLIST = "getMultivariantPlaylist"
        const val GET_VIDEO_DOWNLOAD_INFO = "getVideoDownloadInfo"
        const val GET_ERROR_CODE = "getErrorCode"

        const val RESULT = "result"
        const val ITEM = "item"
        const val INDEX = "index"
        const val URI = "uri"
        const val URLS_KEYS = "urlsKeys"
        const val URLS_VALUES = "urlsValues"
        const val HEADERS_KEYS = "headersKeys"
        const val HEADERS_VALUES = "headersValues"
        const val USING_PLAYLIST = "usingPlaylist"
        const val PLAYLIST_AS_DATA = "playlistAsData"
        const val PLAYBACK_POSITION = "playbackPosition"
        const val PIP_MODE = "pipMode"
        const val DURATION = "duration"
        const val USING_PROXY = "usingProxy"
        const val STOP_PROXY = "stopProxy"

        const val REQUEST_CODE_RESUME = 2
    }
}