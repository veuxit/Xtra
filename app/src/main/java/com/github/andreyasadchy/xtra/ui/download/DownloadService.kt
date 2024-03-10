package com.github.andreyasadchy.xtra.ui.download

import android.annotation.SuppressLint
import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.model.offline.Request
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.FetchProvider
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import com.iheartradio.m3u8.data.MediaPlaylist
import com.iheartradio.m3u8.data.TrackData
import com.tonyodev.fetch2.AbstractFetchListener
import com.tonyodev.fetch2.Download
import com.tonyodev.fetch2.Fetch
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.use
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import javax.inject.Inject
import kotlin.math.min
import com.tonyodev.fetch2.Request as FetchRequest


@AndroidEntryPoint
class DownloadService : IntentService(TAG) {

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_TAG = "NotifActionReceiver"

        private const val ENQUEUE_SIZE = 15
        private const val REQUEST_CODE_DOWNLOAD = 0
        private const val REQUEST_CODE_PLAY = 1

        const val GROUP_KEY = "com.github.andreyasadchy.xtra.DOWNLOADS"
        const val KEY_REQUEST = "request"
        const val KEY_WIFI = "wifi"

        const val ACTION_CANCEL = "com.github.andreyasadchy.xtra.ACTION_DOWNLOAD_CANCEL"
        const val ACTION_PAUSE = "com.github.andreyasadchy.xtra.ACTION_DOWNLOAD_PAUSE"
        const val ACTION_RESUME = "com.github.andreyasadchy.xtra.ACTION_DOWNLOAD_RESUME"

        val activeRequests = HashSet<Int>()
    }

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var offlineRepository: OfflineRepository

    @Inject
    lateinit var fetchProvider: FetchProvider
    private lateinit var fetch: Fetch
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var request: Request
    private lateinit var offlineVideo: OfflineVideo

    private lateinit var playlist: MediaPlaylist

    private val notificationActionReceiver = NotificationActionReceiver()
    private lateinit var pauseAction: NotificationCompat.Action
    private lateinit var resumeAction: NotificationCompat.Action

    init {
        setIntentRedelivery(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreate() {
        super.onCreate()
        pauseAction = createAction(R.string.pause, ACTION_PAUSE, 1)
        resumeAction = createAction(R.string.resume, ACTION_RESUME, 2)
        registerReceiver(notificationActionReceiver, IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
        })
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("CheckResult")
    override fun onHandleIntent(intent: Intent?) {
        request = intent!!.getParcelableExtra(KEY_REQUEST)!!
        offlineVideo = runBlocking { offlineRepository.getVideoById(request.offlineVideoId) }
            ?: return //Download was canceled
        Log.d(TAG, "Starting download. Id: ${offlineVideo.id}")
        fetch = fetchProvider.get(offlineVideo.id)
        val countDownLatch = CountDownLatch(1)
        val channelId = getString(R.string.notification_downloads_channel_id)
        notificationBuilder = NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setGroup(GROUP_KEY)
            setContentTitle(getString(R.string.downloading))
            setOngoing(true)
            setContentText(offlineVideo.name)
            val clickIntent = Intent(this@DownloadService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.KEY_CODE, MainActivity.INTENT_OPEN_DOWNLOADS_TAB)
            }
            setContentIntent(PendingIntent.getActivity(this@DownloadService, REQUEST_CODE_DOWNLOAD, clickIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT))
            addAction(pauseAction)
        }

        notificationManager = NotificationManagerCompat.from(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(channelId) == null) {
                NotificationChannel(channelId, getString(R.string.notification_downloads_channel_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    manager.createNotificationChannel(this)
                }
            }
        }
        updateProgress(offlineVideo.maxProgress, offlineVideo.progress)
        if (offlineVideo.vod) {
            fetch.addListener(object : AbstractFetchListener() {
                var activeDownloadsCount = 0

                override fun onAdded(download: Download) {
                    activeDownloadsCount++
                }

                override fun onCompleted(download: Download) {
                    with(offlineVideo) {
                        if (++progress < maxProgress) {
                            Log.d(TAG, "$progress / $maxProgress")
                            updateProgress(maxProgress, progress)
                            if (--activeDownloadsCount == 0) {
                                enqueueNext()
                            }
                        } else {
                            onDownloadCompleted()
                            countDownLatch.countDown()
                        }
                    }
                }

                override fun onDeleted(download: Download) {
                    if (--activeDownloadsCount == 0) {
                        offlineRepository.deleteVideo(applicationContext, offlineVideo)
                        stopForegroundInternal(true)
                        if (offlineVideo.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                            val directory = DocumentFile.fromTreeUri(applicationContext, request.path.toUri())
                            val videoDirectory = directory?.findFile(offlineVideo.url.substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))
                            if (videoDirectory != null && videoDirectory.listFiles().isEmpty()) {
                                directory.delete()
                            }
                        } else {
                            val directory = File(request.path)
                            if (directory.exists() && directory.list().isEmpty()) {
                                directory.deleteRecursively()
                            }
                        }
                        countDownLatch.countDown()
                    }
                }
            })
            GlobalScope.launch {
                try {
                    playlist = if (offlineVideo.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                        applicationContext.contentResolver.openInputStream(offlineVideo.url.toUri()).use {
                            PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                        }
                    } else {
                        FileInputStream(File(offlineVideo.url)).use {
                            PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                        }
                    }
                    enqueueNext()
                } catch (e: Exception) {

                }
            }
        } else {
            fetch.addListener(object : AbstractFetchListener() {
                override fun onCompleted(download: Download) {
                    onDownloadCompleted()
                    countDownLatch.countDown()
                }

                override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
                    offlineVideo.progress = download.progress
                    updateProgress(100, download.progress)
                }

                override fun onDeleted(download: Download) {
                    offlineRepository.deleteVideo(applicationContext, offlineVideo)
                    stopForegroundInternal(true)
                    countDownLatch.countDown()
                }
            })
            fetch.enqueue(FetchRequest(request.url, request.path).apply { groupId = request.offlineVideoId })
        }
        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADING })
        startForeground(offlineVideo.id, notificationBuilder.build())
        countDownLatch.await()
        activeRequests.remove(request.offlineVideoId)
        fetch.close()
    }

    @Deprecated("Deprecated in Java")
    override fun onDestroy() {
        unregisterReceiver(notificationActionReceiver)
        super.onDestroy()
    }

    private fun enqueueNext() {
        val requests = mutableListOf<FetchRequest>()
        val tracks: List<TrackData>
        try {
            tracks = playlist.tracks
        } catch (e: UninitializedPropertyAccessException) {
            GlobalScope.launch {
                delay(3000L)
                enqueueNext()
            }
            return
        }
        with(request) {
            val current = offlineVideo.progress
            if (offlineVideo.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                val directory = DocumentFile.fromTreeUri(applicationContext, path.toUri())!!
                val videoDirectory = directory.findFile(offlineVideo.url.substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))!!
                try {
                    for (i in current..min(current + ENQUEUE_SIZE, tracks.lastIndex)) {
                        val track = tracks[i]
                        val trackUri = track.uri.substringAfterLast("%2F")
                        val trackFile = videoDirectory.findFile(trackUri) ?: videoDirectory.createFile("", trackUri)
                        requests.add(FetchRequest(url + trackUri, trackFile!!.uri.toString()).apply { groupId = offlineVideoId })
                    }
                } catch (e: IndexOutOfBoundsException) {
                }
            } else {
                try {
                    for (i in current..min(current + ENQUEUE_SIZE, tracks.lastIndex)) {
                        val track = tracks[i]
                        requests.add(FetchRequest(url + track.uri, path + track.uri).apply { groupId = offlineVideoId })
                    }
                } catch (e: IndexOutOfBoundsException) {
                }
            }
        }
        fetch.enqueue(requests)
    }

    private fun stopForegroundInternal(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(if (removeNotification) Service.STOP_FOREGROUND_REMOVE else Service.STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(removeNotification)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun onDownloadCompleted() {
        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
        offlineRepository.deleteRequest(request)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.KEY_VIDEO, offlineVideo)
            putExtra(MainActivity.KEY_CODE, MainActivity.INTENT_OPEN_DOWNLOADED_VIDEO)
        }
        notificationBuilder.apply {
            setAutoCancel(true)
            setContentTitle(getString(R.string.downloaded))
            setProgress(0, 0, false)
            setOngoing(false)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentIntent(PendingIntent.getActivity(this@DownloadService, REQUEST_CODE_PLAY, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT))
            mActions.clear()
        }
        notificationManager.notify(offlineVideo.id, notificationBuilder.build())
        stopForegroundInternal(false)
    }

    private fun updateProgress(maxProgress: Int, progress: Int) {
        notificationManager.notify(offlineVideo.id, notificationBuilder.setProgress(maxProgress, progress, false).build())
        offlineRepository.updateVideo(offlineVideo)
    }

    private fun createAction(@StringRes title: Int, action: String, requestCode: Int) = NotificationCompat.Action(0, getString(title), PendingIntent.getBroadcast(this, requestCode, Intent(action), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT))

    inner class NotificationActionReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CANCEL -> {
                    Log.d(NOTIFICATION_TAG, "Canceled download. Id: ${offlineVideo.id}")
                    GlobalScope.launch {
                        try {
                            activeRequests.remove(offlineVideo.id)
                            fetch.deleteAll()
                        } catch (e: Exception) {
                        }
                    }
                }
                ACTION_PAUSE -> {
                    Log.d(NOTIFICATION_TAG, "Paused download. Id: ${offlineVideo.id}")
                    notificationManager.notify(offlineVideo.id, notificationBuilder.run {
                        mActions.removeAt(0)
                        mActions.add(resumeAction)
                        build()
                    })
                    fetch.pauseGroup(offlineVideo.id)
                }
                ACTION_RESUME -> {
                    Log.d(NOTIFICATION_TAG, "Resumed download. Id: ${offlineVideo.id}")
                    notificationManager.notify(offlineVideo.id, notificationBuilder.run {
                        mActions.removeAt(0)
                        mActions.add(pauseAction)
                        build()
                    })
                    fetch.resumeGroup(offlineVideo.id)
                }
            }
        }
    }
}

//                val mainFile = File(path + "${System.currentTimeMillis()}.ts")
//                try {
//                    for (i in segmentFrom!!..segmentTo!!) {
//                        val file = File("$path${playlist.tracks[i].uri}")
//                        mainFile.appendBytes(file.readBytes())
//                        file.delete()
//                    }
//                } catch (e: UninitializedPropertyAccessException) {
//                    GlobalScope.launch {
//                        delay(3000L)
//                        onDownloadCompleted()
//                    }
//                    return
//                } catch (e: IndexOutOfBoundsException) {
//                    Crashlytics.log("DownloadService.onDownloadCompleted: Playlist tracks size: ${playlist.tracks.size}. Segment from $segmentFrom. Segment to: $segmentTo.")
//                    Crashlytics.logException(e)
//                }
//                Log.d(TAG, "Merged videos")