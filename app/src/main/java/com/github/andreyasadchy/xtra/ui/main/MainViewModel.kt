package com.github.andreyasadchy.xtra.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
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
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.AuthRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.io.File
import java.io.FileOutputStream
import java.util.Timer
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val offlineRepository: OfflineRepository,
    private val authRepository: AuthRepository,
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
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

    fun loadVideo(videoId: String?, useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (video.value == null) {
            viewModelScope.launch {
                video.value = try {
                    val response = graphQLRepository.loadQueryVideo(useCronet, gqlHeaders, videoId)
                    if (enableIntegrity && integrity.value == null) {
                        response.errors?.find { it.message == "failed integrity check" }?.let {
                            integrity.value = "refresh"
                            return@launch
                        }
                    }
                    response.data!!.let { item ->
                        item.video?.let {
                            Video(
                                id = videoId,
                                channelId = it.owner?.id,
                                channelLogin = it.owner?.login,
                                channelName = it.owner?.displayName,
                                type = it.broadcastType?.toString(),
                                title = it.title,
                                uploadDate = it.createdAt?.toString(),
                                duration = it.lengthSeconds?.toString(),
                                thumbnailUrl = it.previewThumbnailURL,
                                profileImageUrl = it.owner?.profileImageURL,
                                animatedPreviewURL = it.animatedPreviewURL,
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getVideos(
                                useCronet = useCronet,
                                headers = helixHeaders,
                                ids = videoId?.let { listOf(it) }
                            ).data.firstOrNull()?.let {
                                Video(
                                    id = it.id,
                                    channelId = it.channelId,
                                    channelLogin = it.channelLogin,
                                    channelName = it.channelName,
                                    title = it.title,
                                    viewCount = it.viewCount,
                                    uploadDate = it.uploadDate,
                                    duration = it.duration,
                                    thumbnailUrl = it.thumbnailUrl,
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    fun loadClip(clipId: String?, useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (clip.value == null) {
            viewModelScope.launch {
                clip.value = try {
                    val user = try {
                        graphQLRepository.loadClipData(useCronet, gqlHeaders, clipId).data?.clip
                    } catch (e: Exception) {
                        null
                    }
                    val clip = graphQLRepository.loadClipVideo(useCronet, gqlHeaders, clipId).also { response ->
                        if (enableIntegrity && integrity.value == null) {
                            response.errors?.find { it.message == "failed integrity check" }?.let {
                                integrity.value = "refresh"
                                return@launch
                            }
                        }
                    }.data?.clip
                    Clip(
                        id = clipId,
                        channelId = user?.broadcaster?.id,
                        channelLogin = user?.broadcaster?.login,
                        channelName = user?.broadcaster?.displayName,
                        profileImageUrl = user?.broadcaster?.profileImageURL,
                        videoId = clip?.video?.id,
                        duration = clip?.durationSeconds,
                        vodOffset = clip?.videoOffsetSeconds ?: user?.videoOffsetSeconds
                    )
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getClips(
                                useCronet = useCronet,
                                headers = helixHeaders,
                                ids = clipId?.let { listOf(it) }
                            ).data.firstOrNull()?.let {
                                Clip(
                                    id = it.id,
                                    channelId = it.channelId,
                                    channelName = it.channelName,
                                    videoId = it.videoId,
                                    vodOffset = it.vodOffset,
                                    gameId = it.gameId,
                                    title = it.title,
                                    viewCount = it.viewCount,
                                    uploadDate = it.createdAt,
                                    duration = it.duration,
                                    thumbnailUrl = it.thumbnailUrl,
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    fun loadUser(login: String?, useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (user.value == null) {
            viewModelScope.launch {
                user.value = try {
                    val response = graphQLRepository.loadQueryUser(useCronet, gqlHeaders, login = login)
                    if (enableIntegrity && integrity.value == null) {
                        response.errors?.find { it.message == "failed integrity check" }?.let {
                            integrity.value = "refresh"
                            return@launch
                        }
                    }
                    response.data!!.user?.let {
                        User(
                            channelId = it.id,
                            channelLogin = it.login,
                            channelName = it.displayName,
                            profileImageUrl = it.profileImageURL
                        )
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getUsers(
                                useCronet = useCronet,
                                headers = helixHeaders,
                                logins = login?.let { listOf(it) }
                            ).data.firstOrNull()?.let {
                                User(
                                    channelId = it.channelId,
                                    channelLogin = it.channelLogin,
                                    channelName = it.channelName,
                                    type = it.type,
                                    broadcasterType = it.broadcasterType,
                                    profileImageUrl = it.profileImageUrl,
                                    createdAt = it.createdAt,
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    fun downloadStream(useCronet: Boolean, filesDir: String, id: String?, title: String?, startedAt: String?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            if (!channelLogin.isNullOrBlank()) {
                val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "thumbnails").mkdir()
                        val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                if (useCronet && cronetEngine != null) {
                                    val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                    cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                    val response = request.future.get()
                                    if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                        FileOutputStream(filePath).use {
                                            it.write(response.responseBody as ByteArray)
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(filePath).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
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
                                if (useCronet && cronetEngine != null) {
                                    val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                    cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                    val response = request.future.get()
                                    if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                        FileOutputStream(filePath).use {
                                            it.write(response.responseBody as ByteArray)
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(filePath).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
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

    fun downloadVideo(useCronet: Boolean, filesDir: String, id: String?, title: String?, uploadDate: String?, type: String?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?, url: String, path: String, quality: String, from: Long, to: Long, downloadChat: Boolean, downloadChatEmotes: Boolean, playlistToFile: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            val downloadedThumbnail = id.takeIf { !it.isNullOrBlank() }?.let { id ->
                thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                    File(filesDir, "thumbnails").mkdir()
                    val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            if (useCronet && cronetEngine != null) {
                                val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                val response = request.future.get()
                                if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                    FileOutputStream(filePath).use {
                                        it.write(response.responseBody as ByteArray)
                                    }
                                }
                            } else {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
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
                            if (useCronet && cronetEngine != null) {
                                val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                val response = request.future.get()
                                if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                    FileOutputStream(filePath).use {
                                        it.write(response.responseBody as ByteArray)
                                    }
                                }
                            } else {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
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

    fun downloadClip(useCronet: Boolean, filesDir: String, clipId: String?, title: String?, uploadDate: String?, duration: Double?, videoId: String?, vodOffset: Int?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, thumbnail: String?, gameId: String?, gameSlug: String?, gameName: String?, url: String, path: String, quality: String, downloadChat: Boolean, downloadChatEmotes: Boolean, wifiOnly: Boolean) {
        viewModelScope.launch {
            val downloadedThumbnail = clipId.takeIf { !it.isNullOrBlank() }?.let { id ->
                thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                    File(filesDir, "thumbnails").mkdir()
                    val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            if (useCronet && cronetEngine != null) {
                                val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                val response = request.future.get()
                                if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                    FileOutputStream(filePath).use {
                                        it.write(response.responseBody as ByteArray)
                                    }
                                }
                            } else {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
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
                            if (useCronet && cronetEngine != null) {
                                val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                val response = request.future.get()
                                if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                    FileOutputStream(filePath).use {
                                        it.write(response.responseBody as ByteArray)
                                    }
                                }
                            } else {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(filePath).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
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
                    clipId = clipId,
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

    fun validate(useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, accountId: String?, accountLogin: String?, activity: Activity) {
        viewModelScope.launch {
            try {
                val helixToken = helixHeaders[C.HEADER_TOKEN]
                if (!helixToken.isNullOrBlank()) {
                    val response = authRepository.validate(useCronet, helixToken)
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
                    val response = authRepository.validate(useCronet, gqlToken)
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
                if (e is IllegalStateException && e.message == "401") {
                    activity.toast(R.string.token_expired)
                    (activity as? MainActivity)?.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                }
            }
        }
        TwitchApiHelper.checkedValidation = true
    }

    fun deleteOldImages() {
        viewModelScope.launch(Dispatchers.IO) {
            offlineRepository.deleteOldImages()
        }
    }
}