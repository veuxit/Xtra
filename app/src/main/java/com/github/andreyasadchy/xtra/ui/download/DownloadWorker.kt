package com.github.andreyasadchy.xtra.ui.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver.SCHEME_CONTENT
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import com.iheartradio.m3u8.data.TrackData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.appendingSink
import okio.buffer
import okio.sink
import okio.use
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    @Inject
    lateinit var offlineRepository: OfflineRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var offlineVideo: OfflineVideo
    private var progress = 0

    override suspend fun doWork(): Result {
        offlineVideo = offlineRepository.getVideoById(inputData.getInt(KEY_VIDEO_ID, 0)) ?: return Result.failure()
        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADING })
        setForeground(createForegroundInfo())
        return withContext(Dispatchers.IO) {
            val sourceUrl = offlineVideo.sourceUrl
            if (sourceUrl?.endsWith(".m3u8") == true) {
                val path = offlineVideo.downloadPath!!
                val from = offlineVideo.fromTime!!
                val to = offlineVideo.toTime!!
                val isShared = path.toUri().scheme == SCHEME_CONTENT
                val playlist = okHttpClient.newCall(Request.Builder().url(sourceUrl).build()).execute().use { response ->
                    response.body()?.byteStream().use {
                        PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                    }
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
                val urlPath = sourceUrl.substringBeforeLast('/') + "/"
                val tracks = ArrayList<TrackData>()
                for (i in fromIndex + offlineVideo.progress..toIndex) {
                    val track = playlist.tracks[i]
                    tracks.add(
                        track.buildUpon()
                            .withUri(track.uri.replace("-unmuted", "-muted"))
                            .build()
                    )
                }
                val videoFileUri = if (offlineVideo.url.isNotBlank()) {
                    progress = offlineVideo.progress
                    offlineVideo.url
                } else {
                    val fileName = "${offlineVideo.videoId ?: ""}${offlineVideo.quality ?: ""}${offlineVideo.downloadDate}.${tracks.first().uri.substringAfterLast(".")}"
                    val fileUri = if (isShared) {
                        val directory = DocumentFile.fromTreeUri(applicationContext, path.toUri())
                        directory?.createFile("", fileName)!!.uri.toString()
                    } else {
                        "$path${File.separator}$fileName"
                    }
                    val startPosition = relativeStartTimes[fromIndex]
                    offlineRepository.updateVideo(offlineVideo.apply {
                        url = fileUri
                        duration = (relativeStartTimes[toIndex] + durations[toIndex] - startPosition) - 1000L
                        sourceStartPosition = startPosition
                        maxProgress = toIndex - fromIndex + 1
                        vod = false
                    })
                    fileUri
                }
                val requestSemaphore = Semaphore(context.prefs().getInt(C.DOWNLOAD_CONCURRENT_LIMIT, 10))
                val mutexMap = mutableMapOf<Int, Mutex>()
                val count = MutableStateFlow(0)
                val collector = launch {
                    count.collect {
                        mutexMap[it]?.unlock()
                        mutexMap.remove(it)
                    }
                }
                val jobs = tracks.map {
                    async {
                        requestSemaphore.withPermit {
                            okHttpClient.newCall(Request.Builder().url(urlPath + it.uri).build()).execute().use { response ->
                                val mutex = Mutex()
                                val id = tracks.indexOf(it)
                                if (count.value != id) {
                                    mutex.lock()
                                    mutexMap[id] = mutex
                                }
                                mutex.withLock {
                                    if (isShared) {
                                        context.contentResolver.openOutputStream(videoFileUri.toUri(), "wa")!!.sink().buffer().use { sink ->
                                            response.body()?.source()?.let { sink.writeAll(it) }
                                        }
                                    } else {
                                        File(videoFileUri).appendingSink().buffer().use { sink ->
                                            response.body()?.source()?.let { sink.writeAll(it) }
                                        }
                                    }
                                }
                            }
                            count.update { it + 1 }
                            progress += 1
                            offlineRepository.updateVideo(offlineVideo.apply { progress = this@DownloadWorker.progress })
                            setForeground(createForegroundInfo())
                        }
                    }
                }
                jobs.awaitAll()
                collector.cancel()
                if (offlineVideo.progress < offlineVideo.maxProgress) {
                    offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_PENDING })
                } else {
                    offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
                }
            } else {
                val isShared = offlineVideo.url.toUri().scheme == SCHEME_CONTENT
                if (offlineVideo.vod) {
                    val requestSemaphore = Semaphore(context.prefs().getInt(C.DOWNLOAD_CONCURRENT_LIMIT, 10))
                    val jobs = if (isShared) {
                        val playlist = context.contentResolver.openInputStream(offlineVideo.url.toUri()).use {
                            PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                        }
                        val directory = DocumentFile.fromTreeUri(context, offlineVideo.url.substringBefore("/document/").toUri())!!
                        val videoDirectory = directory.findFile(offlineVideo.url.substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))!!
                        playlist.tracks.map {
                            async {
                                requestSemaphore.withPermit {
                                    val uri = it.uri.substringAfterLast("%2F")
                                    if (videoDirectory.findFile(uri) == null) {
                                        downloadShared(offlineVideo.sourceUrl + uri, videoDirectory.createFile("", uri)!!.uri.toString())
                                    }
                                    progress += 1
                                    offlineRepository.updateVideo(offlineVideo.apply { progress = this@DownloadWorker.progress })
                                    setForeground(createForegroundInfo())
                                }
                            }
                        }
                    } else {
                        val playlist = FileInputStream(File(offlineVideo.url)).use {
                            PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                        }
                        playlist.tracks.map {
                            async {
                                requestSemaphore.withPermit {
                                    val output = File(offlineVideo.url).parent!! + "/" + it.uri
                                    if (!File(output).exists()) {
                                        download(offlineVideo.sourceUrl + it.uri, output)
                                    }
                                    progress += 1
                                    offlineRepository.updateVideo(offlineVideo.apply { progress = this@DownloadWorker.progress })
                                    setForeground(createForegroundInfo())
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                    if (offlineVideo.progress < offlineVideo.maxProgress) {
                        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_PENDING })
                    } else {
                        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
                    }
                } else {
                    if (isShared) {
                        downloadShared(offlineVideo.sourceUrl!!, offlineVideo.url)
                    } else {
                        if (!File(offlineVideo.url).exists()) {
                            download(offlineVideo.sourceUrl!!, offlineVideo.url)
                        }
                    }
                    offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
                }
            }
            Result.success()
        }
    }

    private fun downloadShared(url: String, output: String) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            context.contentResolver.openOutputStream(output.toUri())!!.sink().buffer().use { sink ->
                response.body()?.source()?.let { sink.writeAll(it) }
            }
        }
    }

    private fun download(url: String, output: String) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            File(output).sink().buffer().use { sink ->
                response.body()?.source()?.let { sink.writeAll(it) }
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = context.getString(R.string.notification_downloads_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                NotificationChannel(channelId, ContextCompat.getString(context, R.string.notification_downloads_channel_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    notificationManager.createNotificationChannel(this)
                }
            }
        }
        val notification = NotificationCompat.Builder(context, channelId).apply {
            setGroup(GROUP_KEY)
            setContentTitle(ContextCompat.getString(context, R.string.downloading))
            setContentText(offlineVideo.name)
            setSmallIcon(android.R.drawable.stat_sys_download)
            setProgress(offlineVideo.maxProgress, offlineVideo.progress, false)
            setOngoing(true)
            setContentIntent(PendingIntent.getActivity(context, REQUEST_CODE_DOWNLOAD,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.KEY_CODE, MainActivity.INTENT_OPEN_DOWNLOADS_TAB)
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            addAction(android.R.drawable.ic_delete, ContextCompat.getString(context, R.string.stop), WorkManager.getInstance(context).createCancelPendingIntent(id))
        }.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(offlineVideo.id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(offlineVideo.id, notification)
        }
    }

    companion object {
        private const val REQUEST_CODE_DOWNLOAD = 0

        const val GROUP_KEY = "com.github.andreyasadchy.xtra.DOWNLOADS"

        const val KEY_VIDEO_ID = "KEY_VIDEO_ID"
    }
}
