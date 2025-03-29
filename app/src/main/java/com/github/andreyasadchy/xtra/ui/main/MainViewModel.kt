package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.ui.download.StreamDownloadWorker
import com.github.andreyasadchy.xtra.ui.download.VideoDownloadWorker
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.toast
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import retrofit2.HttpException
import java.io.File
import java.util.Timer
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: ApiRepository,
    private val offlineRepository: OfflineRepository,
    private val authRepository: AuthRepository,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val checkNetworkStatus = MutableStateFlow(false)
    val isNetworkAvailable = MutableStateFlow<Boolean?>(null)

    var isPlayerMaximized = false
        private set

    var isPlayerOpened = false
        private set

    var sleepTimer: Timer? = null
    var sleepTimerEndTime = 0L

    val video = MutableStateFlow<Video?>(null)
    val clip = MutableStateFlow<Clip?>(null)
    val user = MutableStateFlow<User?>(null)

    val updateUrl = MutableSharedFlow<String?>()

    fun onMaximize() {
        isPlayerMaximized = true
    }

    fun onMinimize() {
        isPlayerMaximized = false
    }

    fun onPlayerStarted() {
        isPlayerOpened = true
        isPlayerMaximized = true
    }

    fun onPlayerClosed() {
        isPlayerOpened = false
        isPlayerMaximized = false
    }

    fun loadVideo(videoId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (video.value == null) {
            viewModelScope.launch {
                try {
                    video.value = repository.loadVideo(videoId, helixHeaders, gqlHeaders, checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun loadClip(clipId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (clip.value == null) {
            viewModelScope.launch {
                try {
                    clip.value = repository.loadClip(clipId, helixHeaders, gqlHeaders, checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun loadUser(login: String? = null, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (user.value == null) {
            viewModelScope.launch {
                try {
                    user.value = repository.loadCheckUser(channelLogin = login, helixHeaders = helixHeaders, gqlHeaders = gqlHeaders, checkIntegrity = checkIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun downloadStream(filesDir: String, stream: Stream, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            with(stream) {
                if (!channelLogin.isNullOrBlank()) {
                    val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                        thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "thumbnails").mkdir()
                            val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(filePath).sink().buffer().use { sink ->
                                                sink.writeAll(response.body()!!.source())
                                            }
                                        }
                                    }
                                } catch (e: Exception) {

                                }
                            }
                            filePath
                        }
                    }
                    val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                        channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "profile_pics").mkdir()
                            val filePath = filesDir + File.separator + "profile_pics" + File.separator + id
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(filePath).sink().buffer().use { sink ->
                                                sink.writeAll(response.body()!!.source())
                                            }
                                        }
                                    }
                                } catch (e: Exception) {

                                }
                            }
                            filePath
                        }
                    }
                    val videoId = offlineRepository.saveVideo(
                        OfflineVideo(
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
                        )
                    ).toInt()
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

    fun downloadVideo(filesDir: String, video: Video, url: String, path: String, quality: String, from: Long, to: Long, downloadChat: Boolean, downloadChatEmotes: Boolean, playlistToFile: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            with(video) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "thumbnails").mkdir()
                        val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body()!!.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        filePath
                    }
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val filePath = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body()!!.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        filePath
                    }
                }
                val videoId = offlineRepository.saveVideo(
                    OfflineVideo(
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
                    )
                ).toInt()
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

    fun downloadClip(filesDir: String, clip: Clip, url: String, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            with(clip) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "thumbnails").mkdir()
                        val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body()!!.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        filePath
                    }
                }
                val downloadedLogo = channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val filePath = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body()!!.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        filePath
                    }
                }
                val videoId = offlineRepository.saveVideo(
                    OfflineVideo(
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
                    )
                ).toInt()
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

    fun validate(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, accountId: String?, accountLogin: String?, activity: Activity) {
        viewModelScope.launch {
            try {
                val helixToken = helixHeaders[C.HEADER_TOKEN]
                if (!helixToken.isNullOrBlank()) {
                    val response = authRepository.validate(helixToken)
                    if (response.clientId.isNotBlank() && response.clientId == helixHeaders[C.HEADER_CLIENT_ID]) {
                        if ((!response.userId.isNullOrBlank() && response.userId != accountId) || (!response.login.isNullOrBlank() && response.login != accountLogin)) {
                            activity.tokenPrefs().edit {
                                putString(C.USER_ID, response.userId?.takeIf { it.isNotBlank() } ?: accountId)
                                putString(C.USERNAME, response.login?.takeIf { it.isNotBlank() } ?: accountLogin)
                            }
                        }
                    } else {
                        throw IllegalStateException("401")
                    }
                }
                val gqlToken = gqlHeaders[C.HEADER_TOKEN]
                if (!gqlToken.isNullOrBlank()) {
                    val response = authRepository.validate(gqlToken)
                    if (response.clientId.isNotBlank() && response.clientId == gqlHeaders[C.HEADER_CLIENT_ID]) {
                        if ((!response.userId.isNullOrBlank() && response.userId != accountId) || (!response.login.isNullOrBlank() && response.login != accountLogin)) {
                            activity.tokenPrefs().edit {
                                putString(C.USER_ID, response.userId?.takeIf { it.isNotBlank() } ?: accountId)
                                putString(C.USERNAME, response.login?.takeIf { it.isNotBlank() } ?: accountLogin)
                            }
                        }
                    } else {
                        throw IllegalStateException("401")
                    }
                }
            } catch (e: Exception) {
                if ((e is IllegalStateException && e.message == "401") || (e is HttpException && e.code() == 401)) {
                    activity.toast(R.string.token_expired)
                    (activity as? MainActivity)?.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                }
            }
        }
        TwitchApiHelper.checkedValidation = true
    }

    fun checkUpdates(url: String, lastChecked: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            updateUrl.emit(
                try {
                    json.decodeFromString<JsonObject>(
                        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { it.body()!!.string() }
                    )["assets"]?.jsonArray?.find {
                        it.jsonObject.getValue("content_type").jsonPrimitive.contentOrNull == "application/vnd.android.package-archive"
                    }?.jsonObject?.let { obj ->
                        obj.getValue("updated_at").jsonPrimitive.contentOrNull?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                            if (it > lastChecked) {
                                obj.getValue("browser_download_url").jsonPrimitive.contentOrNull
                            } else null
                        }
                    }
                } catch (e: Exception) {
                    null
                }
            )
        }
        TwitchApiHelper.checkedUpdates = true
    }

    fun downloadUpdate(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) {
                            val packageInstaller = applicationContext.packageManager.packageInstaller
                            val sessionId = packageInstaller.createSession(
                                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                            )
                            val session = packageInstaller.openSession(sessionId)
                            session.openWrite("package", 0, -1).sink().buffer().use { sink ->
                                sink.writeAll(response.body()!!.source())
                            }
                            session.commit(
                                PendingIntent.getActivity(
                                    applicationContext,
                                    0,
                                    Intent(applicationContext, MainActivity::class.java).apply {
                                        setAction(MainActivity.INTENT_INSTALL_UPDATE)
                                    },
                                    PendingIntent.FLAG_MUTABLE
                                ).intentSender
                            )
                            session.close()
                        }
                    }
                } else {
                    val path = "${applicationContext.filesDir.path}/update/update.apk"
                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) {
                            File(applicationContext.filesDir.path, "update").mkdir()
                            File(path).sink().buffer().use {
                                it.writeAll(response.body()!!.source())
                            }
                        }
                    }
                    applicationContext.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(path.toUri(), "application/vnd.android.package-archive")
                    })
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteOldImages() {
        viewModelScope.launch(Dispatchers.IO) {
            offlineRepository.deleteOldImages()
        }
    }
}