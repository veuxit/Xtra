package com.github.andreyasadchy.xtra.ui.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@HiltWorker
class DownloadWorker @AssistedInject constructor(@Assisted context: Context, @Assisted parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

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
            if (offlineVideo.vod) {
                val playlist = FileInputStream(File(offlineVideo.url)).use {
                    PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                }
                val requestSemaphore = Semaphore(applicationContext.prefs().getInt(C.DOWNLOAD_CONCURRENT_LIMIT, 10))
                val jobs = playlist.tracks.map {
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
                jobs.awaitAll()
                if (offlineVideo.progress < offlineVideo.maxProgress) {
                    offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_PENDING })
                } else {
                    offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
                }
            } else {
                if (!File(offlineVideo.url).exists()) {
                    download(offlineVideo.sourceUrl!!, offlineVideo.url)
                }
                offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
            }
            Result.success()
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
        val channelId = applicationContext.getString(R.string.notification_downloads_channel_id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                NotificationChannel(channelId, applicationContext.getString(R.string.notification_downloads_channel_title), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    notificationManager.createNotificationChannel(this)
                }
            }
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId).apply {
            setGroup(GROUP_KEY)
            setContentTitle(applicationContext.getString(R.string.downloading))
            setContentText(offlineVideo.name)
            setSmallIcon(android.R.drawable.stat_sys_download)
            setProgress(offlineVideo.maxProgress, offlineVideo.progress, false)
            setOngoing(true)
            setContentIntent(PendingIntent.getActivity(applicationContext, REQUEST_CODE_DOWNLOAD,
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.KEY_CODE, MainActivity.INTENT_OPEN_DOWNLOADS_TAB)
                }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT))
            addAction(android.R.drawable.ic_delete, applicationContext.getString(R.string.stop), WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
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
