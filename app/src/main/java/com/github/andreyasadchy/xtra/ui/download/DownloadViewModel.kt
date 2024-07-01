package com.github.andreyasadchy.xtra.ui.download

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoDownloadInfo
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.toast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val playerRepository: PlayerRepository,
    private val offlineRepository: OfflineRepository) : ViewModel() {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    private val _videoInfo = MutableLiveData<VideoDownloadInfo?>()
    val videoInfo: LiveData<VideoDownloadInfo?>
        get() = _videoInfo

    private val _qualities = MutableLiveData<Map<String, Pair<String, String>>?>()
    val qualities: LiveData<Map<String, Pair<String, String>>?>
        get() = _qualities

    fun setStream(gqlHeaders: Map<String, String>, stream: Stream, qualities: Map<String, String>?, randomDeviceId: Boolean?, xDeviceId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean) {
        if (_qualities.value == null) {
            if (!qualities.isNullOrEmpty()) {
                val map = mutableMapOf<String, Pair<String, String>>()
                qualities.entries.forEach {
                    if (it.key.equals("source", true)) {
                        map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                    } else {
                        map[it.key] = Pair(it.key, it.value)
                    }
                }
                map.apply {
                    if (containsKey("audio_only")) {
                        remove("audio_only")?.let { url ->
                            put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                        }
                    }
                }
                _qualities.value = map
            } else {
                viewModelScope.launch {
                    val default = mutableMapOf("source" to "", "1080p60" to "", "1080p30" to "", "720p60" to "", "720p30" to "", "480p30" to "", "360p30" to "", "160p30" to "", "audio_only" to "")
                    try {
                        val urls = if (!stream.channelLogin.isNullOrBlank()) {
                            val playlist = playerRepository.loadStreamPlaylist(gqlHeaders, stream.channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, enableIntegrity)
                            if (!playlist.isNullOrBlank()) {
                                val names = "NAME=\"(.*)\"".toRegex().findAll(playlist).map { it.groupValues[1] }.toMutableList()
                                val urls = "https://.*\\.m3u8".toRegex().findAll(playlist).map(MatchResult::value).toMutableList()
                                names.zip(urls).toMap(mutableMapOf()).takeIf { it.isNotEmpty() } ?: default
                            } else default
                        } else default
                        val map = mutableMapOf<String, Pair<String, String>>()
                        urls.entries.forEach {
                            if (it.key.equals("source", true)) {
                                map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                            } else {
                                map[it.key] = Pair(it.key, it.value)
                            }
                        }
                        map.apply {
                            if (containsKey("audio_only")) {
                                remove("audio_only")?.let { url ->
                                    put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                }
                            }
                        }
                        _qualities.postValue(map)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        } else {
                            val map = mutableMapOf<String, Pair<String, String>>()
                            default.entries.forEach {
                                if (it.key.equals("source", true)) {
                                    map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                                } else {
                                    map[it.key] = Pair(it.key, it.value)
                                }
                            }
                            map.apply {
                                if (containsKey("audio_only")) {
                                    remove("audio_only")?.let { url ->
                                        put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                    }
                                }
                            }
                            _qualities.value = map
                        }
                    }
                }
            }
        }
    }

    fun setVideo(gqlHeaders: Map<String, String>, video: Video, videoInfo: VideoDownloadInfo?, playerType: String?, skipAccessToken: Int, enableIntegrity: Boolean) {
        if (_qualities.value == null) {
            if (videoInfo != null && !videoInfo.qualities.isNullOrEmpty()) {
                val map = mutableMapOf<String, Pair<String, String>>()
                videoInfo.qualities.forEach {
                    if (it.key.equals("source", true)) {
                        map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                    } else {
                        map[it.key] = Pair(it.key, it.value)
                    }
                }
                map.apply {
                    if (containsKey("audio_only")) {
                        remove("audio_only")?.let { url ->
                            put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                        }
                    }
                }
                _qualities.value = map
                _videoInfo.value = videoInfo
            } else {
                viewModelScope.launch {
                    try {
                        val map = if (skipAccessToken <= 1 && !video.animatedPreviewURL.isNullOrBlank()) {
                            val urls = TwitchApiHelper.getVideoUrlMapFromPreview(video.animatedPreviewURL, video.type)
                            val map = mutableMapOf<String, Pair<String, String>>()
                            urls.entries.forEach {
                                if (it.key.equals("source", true)) {
                                    map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                                } else {
                                    map[it.key] = Pair(it.key, it.value)
                                }
                            }
                            map.apply {
                                if (containsKey("audio_only")) {
                                    remove("audio_only")?.let { url ->
                                        put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                    }
                                }
                            }
                        } else {
                            val response = playerRepository.loadVideoPlaylist(gqlHeaders, video.id, playerType, enableIntegrity)
                            if (response.isSuccessful) {
                                val playlist = response.body()!!.string()
                                val qualities = "NAME=\"(.+?)\"".toRegex().findAll(playlist).map { it.groupValues[1] }.toMutableList()
                                val codecs = "CODECS=\"(.+?)\\.".toRegex().findAll(playlist).map {
                                    when(it.groupValues[1]) {
                                        "av01" -> "AV1"
                                        "hvc1" -> "H.265"
                                        "avc1" -> "H.264"
                                        else -> it.groupValues[1]
                                    }
                                }.toMutableList()
                                if (codecs.all { it == "H.264" || it == "mp4a" }) {
                                    codecs.clear()
                                }
                                val urls = "https://.*\\.m3u8".toRegex().findAll(playlist).map(MatchResult::value).toMutableList()
                                val map = mutableMapOf<String, Pair<String, String>>()
                                qualities.forEachIndexed { index, quality ->
                                    if (quality.equals("source", true)) {
                                        map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(quality, urls[index])
                                    } else {
                                        if (!quality.startsWith("audio", true)) {
                                            val name = codecs.getOrNull(index)?.let { codec ->
                                                "$quality $codec"
                                            } ?: quality
                                            map[name] = Pair(quality, urls[index])
                                        } else {
                                            map["audio_only"] = Pair("audio_only", urls[index])
                                        }
                                    }
                                }
                                map.apply {
                                    if (containsKey("audio_only")) {
                                        remove("audio_only")?.let { url ->
                                            put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                        }
                                    }
                                }
                            } else {
                                if (skipAccessToken == 2 && !video.animatedPreviewURL.isNullOrBlank()) {
                                    val urls = TwitchApiHelper.getVideoUrlMapFromPreview(video.animatedPreviewURL, video.type)
                                    val map = mutableMapOf<String, Pair<String, String>>()
                                    urls.entries.forEach {
                                        if (it.key.equals("source", true)) {
                                            map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                                        } else {
                                            map[it.key] = Pair(it.key, it.value)
                                        }
                                    }
                                    map.apply {
                                        if (containsKey("audio_only")) {
                                            remove("audio_only")?.let { url ->
                                                put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                            }
                                        }
                                    }
                                } else {
                                    throw IllegalAccessException()
                                }
                            }
                        }
                        _qualities.value = map
                        _videoInfo.value = VideoDownloadInfo(
                            totalDuration = video.duration?.let { TwitchApiHelper.getDuration(it)?.times(1000) } ?: 0,
                            currentPosition = 0
                        )
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                        if (e is IllegalAccessException) {
                            applicationContext.toast(ContextCompat.getString(applicationContext, R.string.video_subscribers_only))
                            _videoInfo.value = null
                        }
                    }
                }
            }
        }
    }

    fun setClip(gqlHeaders: Map<String, String>, clip: Clip, qualities: Map<String, String>?, skipAccessToken: Int) {
        if (_qualities.value == null) {
            if (!qualities.isNullOrEmpty()) {
                val map = mutableMapOf<String, Pair<String, String>>()
                qualities.entries.forEach {
                    if (it.key.equals("source", true)) {
                        map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                    } else {
                        map[it.key] = Pair(it.key, it.value)
                    }
                }
                map.apply {
                    if (containsKey("audio_only")) {
                        remove("audio_only")?.let { url ->
                            put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                        }
                    }
                }
                _qualities.value = map
            } else {
                viewModelScope.launch {
                    try {
                        val urls = if (skipAccessToken <= 1 && !clip.thumbnailUrl.isNullOrBlank()) {
                            TwitchApiHelper.getClipUrlMapFromPreview(clip.thumbnailUrl)
                        } else {
                            graphQLRepository.loadClipUrls(
                                headers = gqlHeaders,
                                slug = clip.id
                            ) ?: if (skipAccessToken == 2 && !clip.thumbnailUrl.isNullOrBlank()) {
                                TwitchApiHelper.getClipUrlMapFromPreview(clip.thumbnailUrl)
                            } else null
                        }
                        val map = mutableMapOf<String, Pair<String, String>>()
                        urls?.entries?.forEach {
                            if (it.key.equals("source", true)) {
                                map[ContextCompat.getString(applicationContext, R.string.source)] = Pair(it.key, it.value)
                            } else {
                                map[it.key] = Pair(it.key, it.value)
                            }
                        }
                        map.apply {
                            if (containsKey("audio_only")) {
                                remove("audio_only")?.let { url ->
                                    put(ContextCompat.getString(applicationContext, R.string.audio_only), url)
                                }
                            }
                        }
                        _qualities.postValue(map)
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") {
                            _integrity.postValue(true)
                        }
                    }
                }
            }
        }
    }

    fun downloadStream(stream: Stream, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        GlobalScope.launch {
            with(stream) {
                if (!channelLogin.isNullOrBlank()) {
                    val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let {
                        DownloadUtils.savePng(applicationContext, thumbnail, "thumbnails", it)
                    }
                    val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let {
                        DownloadUtils.savePng(applicationContext, channelLogo, "profile_pics", it)
                    }
                    val videoId = offlineRepository.saveVideo(OfflineVideo(
                        name = title,
                        channelId = channelId,
                        channelLogin = channelLogin,
                        channelName = channelName,
                        channelLogo = downloadedLogo,
                        thumbnail = downloadedThumbnail,
                        gameId = gameId,
                        gameSlug = gameSlug,
                        gameName = gameName,
                        uploadDate = startedAt?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                        downloadDate = System.currentTimeMillis(),
                        downloadPath = path,
                        status = OfflineVideo.STATUS_BLOCKED,
                        quality = if (!quality.contains("Audio", true)) quality else "audio",
                        downloadChat = downloadChat,
                        downloadChatEmotes = downloadChatEmotes,
                        live = true
                    )).toInt()
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        channelLogin,
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequestBuilder<StreamDownloadWorker>()
                            .setInputData(workDataOf(StreamDownloadWorker.KEY_VIDEO_ID to videoId))
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                                    .build()
                            )
                            .build()
                    )
                }
            }
        }
    }

    fun downloadVideo(video: Video, url: String, path: String, quality: String, from: Long, to: Long, downloadChat: Boolean, downloadChatEmotes: Boolean, playlistToFile: Boolean, wifiOnly: Boolean) {
        GlobalScope.launch {
            with(video) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(applicationContext, thumbnail, "thumbnails", it)
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(applicationContext, channelLogo, "profile_pics", it)
                }
                val videoId = offlineRepository.saveVideo(OfflineVideo(
                    sourceUrl = url,
                    name = title,
                    channelId = channelId,
                    channelLogin = channelLogin,
                    channelName = channelName,
                    channelLogo = downloadedLogo,
                    thumbnail = downloadedThumbnail,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
                    uploadDate = uploadDate?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                    downloadDate = System.currentTimeMillis(),
                    downloadPath = path,
                    fromTime = from,
                    toTime = to,
                    status = OfflineVideo.STATUS_BLOCKED,
                    type = type,
                    videoId = id,
                    quality = if (!quality.contains("Audio", true)) quality else "audio",
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes,
                    playlistToFile = playlistToFile
                )).toInt()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "download",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                        .setInputData(workDataOf(VideoDownloadWorker.KEY_VIDEO_ID to videoId))
                        .addTag(videoId.toString())
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                )
            }
        }
    }

    fun downloadClip(clip: Clip, url: String, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        GlobalScope.launch {
            with(clip) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(applicationContext, thumbnail, "thumbnails", it)
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(applicationContext, channelLogo, "profile_pics", it)
                }
                val videoId = offlineRepository.saveVideo(OfflineVideo(
                    sourceUrl = url,
                    sourceStartPosition = vodOffset?.toLong()?.times(1000L),
                    name = title,
                    channelId = channelId,
                    channelLogin = channelLogin,
                    channelName = channelName,
                    channelLogo = downloadedLogo,
                    thumbnail = downloadedThumbnail,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
                    duration = duration?.toLong()?.times(1000L),
                    uploadDate = uploadDate?.let { TwitchApiHelper.parseIso8601DateUTC(it) },
                    downloadDate = System.currentTimeMillis(),
                    downloadPath = path,
                    status = OfflineVideo.STATUS_BLOCKED,
                    videoId = videoId,
                    clipId = id,
                    quality = if (!quality.contains("Audio", true)) quality else "audio",
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes
                )).toInt()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "download",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                        .setInputData(workDataOf(VideoDownloadWorker.KEY_VIDEO_ID to videoId))
                        .addTag(videoId.toString())
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                )
            }
        }
    }
}