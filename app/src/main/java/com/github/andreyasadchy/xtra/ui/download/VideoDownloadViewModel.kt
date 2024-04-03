package com.github.andreyasadchy.xtra.ui.download

import android.app.Application
import android.content.ContentResolver
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.VideoDownloadInfo
import com.github.andreyasadchy.xtra.model.offline.Request
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.toast
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import com.iheartradio.m3u8.PlaylistWriter
import com.iheartradio.m3u8.data.Playlist
import com.iheartradio.m3u8.data.TrackData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class VideoDownloadViewModel @Inject constructor(
    application: Application,
    private val playerRepository: PlayerRepository,
    private val offlineRepository: OfflineRepository
) : AndroidViewModel(application) {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    private val _videoInfo = MutableLiveData<VideoDownloadInfo?>()
    val videoInfo: LiveData<VideoDownloadInfo?>
        get() = _videoInfo

    fun setVideo(gqlHeaders: Map<String, String>, video: Video, playerType: String?, skipAccessToken: Int, enableIntegrity: Boolean) {
        if (_videoInfo.value == null) {
            viewModelScope.launch(Dispatchers.IO) {
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
                            put(ContextCompat.getString(getApplication(), R.string.audio_only), it.value)
                        }
                    }
                    _videoInfo.postValue(VideoDownloadInfo(video, map, video.duration?.let { TwitchApiHelper.getDuration(it)?.times(1000) } ?: 0, 0))
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                    if (e is IllegalAccessException) {
                        launch(Dispatchers.Main) {
                            val context = getApplication<Application>()
                            context.toast(R.string.video_subscribers_only)
                            _videoInfo.value = null
                        }
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

    fun download(url: String, path: String, quality: String, from: Long, to: Long, useWorkManager: Boolean) {
        GlobalScope.launch {
            val context = getApplication<Application>()
            val video = _videoInfo.value!!.video
            val playlist = ByteArrayInputStream(playerRepository.getResponse(url = url).toByteArray()).use {
                PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
            }
            val targetDuration = playlist.targetDuration * 1000L
            var totalDuration = 0L
            val size = playlist.tracks.size
            val relativeStartTimes = ArrayList<Long>(size)
            val durations = ArrayList<Long>(size)
            var relativeTime = 0L
            playlist.tracks.forEach {
                val duration = (it.trackInfo.duration * 1000f).toLong()
                durations.add(duration)
                totalDuration += duration
                relativeStartTimes.add(relativeTime)
                relativeTime += duration
            }
            val fromIndex = if (from == 0L) {
                0
            } else {
                val min = from - targetDuration
                relativeStartTimes.binarySearch(comparison = { time ->
                    when {
                        time > from -> 1
                        time < min -> -1
                        else -> 0
                    }
                }).let { if (it < 0) -it else it }
            }
            val toIndex = if (to in relativeStartTimes.last()..totalDuration) {
                relativeStartTimes.lastIndex
            } else {
                val max = to + targetDuration
                relativeStartTimes.binarySearch(comparison = { time ->
                    when {
                        time > max -> 1
                        time < to -> -1
                        else -> 0
                    }
                }).let { if (it < 0) -it else it }
            }
            val startPosition = relativeStartTimes[fromIndex]
            val duration = (relativeStartTimes[toIndex] + durations[toIndex] - startPosition) - 1000L
            val urlPath = url.substringBeforeLast('/') + "/"
            val videoDirectoryName = if (!video.id.isNullOrBlank()) {
                "${video.id}${if (!quality.contains("Audio", true)) quality else "audio"}"
            } else {
                "${System.currentTimeMillis()}"
            }
            if (path.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                val directory = DocumentFile.fromTreeUri(context, path.toUri())
                val videoDirectory = directory?.findFile(videoDirectoryName) ?: directory?.createDirectory(videoDirectoryName)
                val tracks = ArrayList<TrackData>()
                for (i in fromIndex..toIndex) {
                    val track = playlist.tracks[i]
                    tracks.add(
                        track.buildUpon()
                            .withUri(videoDirectory?.uri.toString() + "%2F" + track.uri.replace("-unmuted", "-muted"))
                            .build()
                    )
                }
                val playlistFile = videoDirectory?.createFile("", "${System.currentTimeMillis()}.m3u8")!!
                context.contentResolver.openOutputStream(playlistFile.uri).use {
                    PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                }
                val offlineVideo = DownloadUtils.prepareDownload(context, video, urlPath, playlistFile.uri.toString(), duration, startPosition, fromIndex, toIndex)
                val videoId = offlineRepository.saveVideo(offlineVideo).toInt()
                if (useWorkManager) {
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        videoId.toString(),
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<DownloadWorker>()
                            .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                            .build()
                    )
                } else {
                    val request = Request(videoId, urlPath, path)
                    offlineRepository.saveRequest(request)

                    DownloadUtils.download(context, request)
                }
            } else {
                val tracks = ArrayList<TrackData>()
                for (i in fromIndex..toIndex) {
                    val track = playlist.tracks[i]
                    tracks.add(
                        track.buildUpon()
                            .withUri(track.uri.replace("-unmuted", "-muted"))
                            .build()
                    )
                }
                val directory = "$path${File.separator}$videoDirectoryName${File.separator}"
                File(directory).mkdir()
                val playlistUri = "$directory${System.currentTimeMillis()}.m3u8"
                FileOutputStream(playlistUri).use {
                    PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                }
                val offlineVideo = DownloadUtils.prepareDownload(context, video, urlPath, playlistUri, duration, startPosition, fromIndex, toIndex)
                val videoId = offlineRepository.saveVideo(offlineVideo).toInt()
                if (useWorkManager) {
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        videoId.toString(),
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<DownloadWorker>()
                            .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                            .build()
                    )
                } else {
                    val request = Request(videoId, urlPath, directory)
                    offlineRepository.saveRequest(request)

                    DownloadUtils.download(context, request)
                }
            }
        }
    }
}