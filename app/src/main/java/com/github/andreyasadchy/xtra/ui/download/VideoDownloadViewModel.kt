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
import com.github.andreyasadchy.xtra.model.ui.Video
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
class VideoDownloadViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val playerRepository: PlayerRepository,
    private val offlineRepository: OfflineRepository) : ViewModel() {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    private val _videoInfo = MutableLiveData<VideoDownloadInfo?>()
    val videoInfo: LiveData<VideoDownloadInfo?>
        get() = _videoInfo

    fun setVideo(gqlHeaders: Map<String, String>, video: Video, playerType: String?, skipAccessToken: Int, enableIntegrity: Boolean) {
        if (_videoInfo.value == null) {
            viewModelScope.launch {
                try {
                    val map = if (skipAccessToken <= 1 && !video.animatedPreviewURL.isNullOrBlank()) {
                        TwitchApiHelper.getVideoUrlMapFromPreview(video.animatedPreviewURL, video.type)
                    } else {
                        val response = playerRepository.loadVideoPlaylist(gqlHeaders, video.id, playerType, enableIntegrity)
                        if (response.isSuccessful) {
                            val playlist = response.body()!!.string()
                            val qualities = "NAME=\"(.*)\"".toRegex().findAll(playlist).map { it.groupValues[1] }.toMutableList()
                            val urls = "https://.*\\.m3u8".toRegex().findAll(playlist).map(MatchResult::value).toMutableList()
                            qualities.zip(urls).toMap(mutableMapOf())
                        } else {
                            if (skipAccessToken == 2 && !video.animatedPreviewURL.isNullOrBlank()) {
                                TwitchApiHelper.getVideoUrlMapFromPreview(video.animatedPreviewURL, video.type)
                            } else {
                                throw IllegalAccessException()
                            }
                        }
                    }.apply {
                        entries.find { it.key.startsWith("audio", true) }?.let {
                            remove(it.key)
                            put(ContextCompat.getString(applicationContext, R.string.audio_only), it.value)
                        }
                    }
                    _videoInfo.postValue(VideoDownloadInfo(video, map, video.duration?.let { TwitchApiHelper.getDuration(it)?.times(1000) } ?: 0, 0))
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

    fun setVideoInfo(videoInfo: VideoDownloadInfo) {
        if (_videoInfo.value != videoInfo) {
            _videoInfo.value = videoInfo
        }
    }

    fun download(url: String, path: String, quality: String, from: Long, to: Long, downloadChat: Boolean, downloadChatEmotes: Boolean, playlistToFile: Boolean, wifiOnly: Boolean) {
        GlobalScope.launch {
            with(_videoInfo.value!!.video) {
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
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
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