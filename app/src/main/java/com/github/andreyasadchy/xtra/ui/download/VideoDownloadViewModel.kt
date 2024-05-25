package com.github.andreyasadchy.xtra.ui.download

import android.content.ContentResolver
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
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

    fun download(url: String, path: String, quality: String, from: Long, to: Long, downloadChat: Boolean, downloadChatEmotes: Boolean, playlistToFile: Boolean, useWorkManager: Boolean) {
        GlobalScope.launch {
            val video = _videoInfo.value!!.video
            if (playlistToFile) {
                val offlineVideo = DownloadUtils.prepareDownload(
                    context = applicationContext,
                    downloadable = video,
                    url = url,
                    path = "",
                    downloadDate = System.currentTimeMillis(),
                    downloadPath = path,
                    fromTime = from,
                    toTime = to,
                    quality = quality,
                    downloadChat = downloadChat,
                    downloadChatEmotes = downloadChatEmotes)
                val videoId = offlineRepository.saveVideo(offlineVideo).toInt()
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    videoId.toString(),
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                        .build()
                )
            } else {
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
                val downloadDate = System.currentTimeMillis()
                if (path.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                    val directory = DocumentFile.fromTreeUri(applicationContext, path.toUri())
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
                    val playlistFile = videoDirectory?.createFile("", "${downloadDate}.m3u8")!!
                    applicationContext.contentResolver.openOutputStream(playlistFile.uri).use {
                        PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                    }
                    val offlineVideo = DownloadUtils.prepareDownload(
                        context = applicationContext,
                        downloadable = video,
                        url = urlPath,
                        path = playlistFile.uri.toString(),
                        duration = duration,
                        downloadDate = downloadDate,
                        startPosition = startPosition,
                        segmentFrom = fromIndex,
                        segmentTo = toIndex,
                        quality = quality,
                        downloadChat = downloadChat,
                        downloadChatEmotes = downloadChatEmotes)
                    val videoId = offlineRepository.saveVideo(offlineVideo).toInt()
                    if (useWorkManager || downloadChat) {
                        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                            videoId.toString(),
                            ExistingWorkPolicy.KEEP,
                            OneTimeWorkRequestBuilder<DownloadWorker>()
                                .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                                .build()
                        )
                    } else {
                        val request = Request(videoId, urlPath, path)
                        offlineRepository.saveRequest(request)

                        DownloadUtils.download(applicationContext, request)
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
                    val playlistUri = "$directory${downloadDate}.m3u8"
                    FileOutputStream(playlistUri).use {
                        PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                    }
                    val offlineVideo = DownloadUtils.prepareDownload(
                        context = applicationContext,
                        downloadable = video,
                        url = urlPath,
                        path = playlistUri,
                        duration = duration,
                        downloadDate = downloadDate,
                        startPosition = startPosition,
                        segmentFrom = fromIndex,
                        segmentTo = toIndex,
                        quality = quality,
                        downloadChat = downloadChat,
                        downloadChatEmotes = downloadChatEmotes)
                    val videoId = offlineRepository.saveVideo(offlineVideo).toInt()
                    if (useWorkManager || downloadChat) {
                        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                            videoId.toString(),
                            ExistingWorkPolicy.KEEP,
                            OneTimeWorkRequestBuilder<DownloadWorker>()
                                .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
                                .build()
                        )
                    } else {
                        val request = Request(videoId, urlPath, directory)
                        offlineRepository.saveRequest(request)

                        DownloadUtils.download(applicationContext, request)
                    }
                }
            }
        }
    }
}