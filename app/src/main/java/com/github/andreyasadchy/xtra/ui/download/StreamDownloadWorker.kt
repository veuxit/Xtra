package com.github.andreyasadchy.xtra.ui.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
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
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.ChatReadWebSocket
import com.github.andreyasadchy.xtra.util.chat.ChatUtils
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import com.github.andreyasadchy.xtra.util.prefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.appendingSink
import okio.buffer
import okio.sink
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Random
import javax.inject.Inject
import kotlin.math.max

@HiltWorker
class StreamDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    @Inject
    lateinit var repository: ApiRepository

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var offlineRepository: OfflineRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var offlineVideo: OfflineVideo
    private var chatReadWebSocket: ChatReadWebSocket? = null
    private var chatFileWriter: BufferedWriter? = null
    private var chatPosition: Long = 0

    override suspend fun doWork(): Result {
        val firstVideo = offlineRepository.getVideoById(inputData.getInt(KEY_VIDEO_ID, 0)) ?: return Result.failure()
        offlineVideo = firstVideo
        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_WAITING_FOR_STREAM })
        setForeground(createForegroundInfo(false, firstVideo))
        return runBlocking {
            val path = offlineVideo.downloadPath!!
            val channelLogin = offlineVideo.channelLogin!!
            val quality = offlineVideo.quality
            val offlineCheck = max(context.prefs().getString(C.DOWNLOAD_STREAM_OFFLINE_CHECK, "10")?.toLongOrNull() ?: 10L, 2L) * 1000L
            val startWait = (context.prefs().getString(C.DOWNLOAD_STREAM_START_WAIT, "120")?.toLongOrNull())?.times(60000L)
            val endWait = (context.prefs().getString(C.DOWNLOAD_STREAM_END_WAIT, "15")?.toLongOrNull())?.times(60000L)
            val proxyHost = context.prefs().getString(C.PROXY_HOST, null)
            val proxyPort = context.prefs().getString(C.PROXY_PORT, null)?.toIntOrNull()
            val proxyMultivariantPlaylist = context.prefs().getBoolean(C.PROXY_MULTIVARIANT_PLAYLIST, false)
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, context.prefs().getBoolean(C.TOKEN_INCLUDE_TOKEN_STREAM, true))
            val randomDeviceId = context.prefs().getBoolean(C.TOKEN_RANDOM_DEVICEID, true)
            val xDeviceId = context.prefs().getString(C.TOKEN_XDEVICEID, "twitch-web-wall-mason")
            val playerType = context.prefs().getString(C.TOKEN_PLAYERTYPE, "site")
            val supportedCodecs = context.prefs().getString(C.TOKEN_SUPPORTED_CODECS, "av1,h265,h264")
            val proxyPlaybackAccessToken = context.prefs().getBoolean(C.PROXY_PLAYBACK_ACCESS_TOKEN, false)
            val proxyUser = context.prefs().getString(C.PROXY_USER, null)
            val proxyPassword = context.prefs().getString(C.PROXY_PASSWORD, null)
            val enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false)
            var playlistUrl = playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, false, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
            var loop = true
            var startTime = System.currentTimeMillis()
            var endTime = startWait?.let { System.currentTimeMillis() + it }
            while (loop) {
                val playlist = okHttpClient.newCall(Request.Builder().url(playlistUrl).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body()!!.string()
                    } else null
                }
                if (!playlist.isNullOrBlank()) {
                    val names = "NAME=\"(.*)\"".toRegex().findAll(playlist).map { it.groupValues[1] }.toMutableList()
                    val urls = "https://.*\\.m3u8".toRegex().findAll(playlist).map(MatchResult::value).toMutableList()
                    val map = names.zip(urls).toMap(mutableMapOf())
                    if (map.isNotEmpty()) {
                        val mediaPlaylistUrl = if (!quality.isNullOrBlank()) {
                            quality.split("p").let { targetQuality ->
                                targetQuality[0].filter(Char::isDigit).toIntOrNull()?.let { targetRes ->
                                    val targetFps = if (targetQuality.size >= 2) targetQuality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                                    map.entries.find { entry ->
                                        entry.key.split("p").let { quality ->
                                            quality[0].filter(Char::isDigit).toIntOrNull()?.let { qualityRes ->
                                                val qualityFps = if (quality.size >= 2) quality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                                                (targetRes == qualityRes && targetFps >= qualityFps) || targetRes > qualityRes
                                            } ?: false
                                        }
                                    }
                                }
                            }?.value ?: map.values.first()
                        } else {
                            map.values.first()
                        }
                        val url = if ((proxyPlaybackAccessToken || proxyMultivariantPlaylist) && !proxyHost.isNullOrBlank() && proxyPort != null) {
                            val url = if (proxyPlaybackAccessToken) {
                                playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, true, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
                            } else {
                                playlistUrl
                            }
                            val newPlaylist = playerRepository.loadStreamPlaylistResponse(url, proxyMultivariantPlaylist, proxyHost, proxyPort, proxyUser, proxyPassword)
                            val newNames = "NAME=\"(.*)\"".toRegex().findAll(newPlaylist).map { it.groupValues[1] }.toMutableList()
                            val newUrls = "https://.*\\.m3u8".toRegex().findAll(newPlaylist).map(MatchResult::value).toMutableList()
                            val newMap = newNames.zip(newUrls).toMap(mutableMapOf())
                            if (newMap.isNotEmpty()) {
                                if (!quality.isNullOrBlank()) {
                                    quality.split("p").let { targetQuality ->
                                        targetQuality[0].filter(Char::isDigit).toIntOrNull()?.let { targetRes ->
                                            val targetFps = if (targetQuality.size >= 2) targetQuality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                                            newMap.entries.find { entry ->
                                                entry.key.split("p").let { quality ->
                                                    quality[0].filter(Char::isDigit).toIntOrNull()?.let { qualityRes ->
                                                        val qualityFps = if (quality.size >= 2) quality[1].filter(Char::isDigit).toIntOrNull() ?: 30 else 30
                                                        (targetRes == qualityRes && targetFps >= qualityFps) || targetRes > qualityRes
                                                    } ?: false
                                                }
                                            }
                                        }
                                    }?.value ?: newMap.values.first()
                                } else {
                                    newMap.values.first()
                                }
                            } else mediaPlaylistUrl
                        } else {
                            mediaPlaylistUrl
                        }
                        offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADING })
                        setForeground(createForegroundInfo(true, firstVideo))
                        val done = try {
                            download(channelLogin, url, path)
                        } finally {
                            chatReadWebSocket?.disconnect()
                            chatFileWriter?.close()
                        }
                        if (done) {
                            if (offlineVideo.downloadChat && !offlineVideo.chatUrl.isNullOrBlank()) {
                                val chatUrl = offlineVideo.chatUrl!!
                                val isShared = chatUrl.toUri().scheme == ContentResolver.SCHEME_CONTENT
                                if (isShared) {
                                    context.contentResolver.openFileDescriptor(chatUrl.toUri(), "rw")!!.use {
                                        FileOutputStream(it.fileDescriptor).use { output ->
                                            output.channel.truncate(offlineVideo.chatBytes)
                                        }
                                    }
                                } else {
                                    FileOutputStream(chatUrl).use { output ->
                                        output.channel.truncate(offlineVideo.chatBytes)
                                    }
                                }
                                if (isShared) {
                                    context.contentResolver.openOutputStream(chatUrl.toUri(), "wa")!!.bufferedWriter()
                                } else {
                                    FileOutputStream(chatUrl, true).bufferedWriter()
                                }.use { fileWriter ->
                                    fileWriter.write("}")
                                }
                            }
                            offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_DOWNLOADED })
                            if (endWait == null || endWait > 0) {
                                val newId = offlineRepository.saveVideo(OfflineVideo(
                                    channelId = offlineVideo.channelId,
                                    channelLogin = offlineVideo.channelLogin,
                                    channelName = offlineVideo.channelName,
                                    channelLogo = offlineVideo.channelLogo,
                                    downloadPath = offlineVideo.downloadPath,
                                    status = OfflineVideo.STATUS_WAITING_FOR_STREAM,
                                    quality = offlineVideo.quality,
                                    downloadChat = offlineVideo.downloadChat,
                                    downloadChatEmotes = offlineVideo.downloadChatEmotes,
                                    live = true
                                ))
                                val newVideo = offlineRepository.getVideoById(newId.toInt())!!
                                offlineVideo = newVideo
                            }
                        } else {
                            offlineRepository.updateVideo(offlineVideo.apply { status = OfflineVideo.STATUS_WAITING_FOR_STREAM })
                        }
                        setForeground(createForegroundInfo(false, firstVideo))
                        endTime = endWait?.let { System.currentTimeMillis() + it }
                        if (endWait == null || endWait > 0) {
                            playlistUrl = playerRepository.loadStreamPlaylistUrl(gqlHeaders, channelLogin, randomDeviceId, xDeviceId, playerType, supportedCodecs, false, proxyHost, proxyPort, proxyUser, proxyPassword, enableIntegrity)
                        }
                    }
                }
                val currentTime = System.currentTimeMillis()
                if (endTime == null || currentTime < endTime) {
                    val timeTaken = currentTime - startTime
                    if (timeTaken < offlineCheck) {
                        delay(offlineCheck - timeTaken)
                    }
                    startTime = System.currentTimeMillis()
                } else {
                    loop = false
                }
            }
            Result.success()
        }
    }

    private suspend fun download(channelLogin: String, sourceUrl: String, path: String): Boolean = withContext(Dispatchers.IO) {
        val isShared = path.toUri().scheme == ContentResolver.SCHEME_CONTENT
        val liveCheck = max(context.prefs().getString(C.DOWNLOAD_STREAM_LIVE_CHECK, "2")?.toLongOrNull() ?: 2L, 2L) * 1000L
        val downloadDate = System.currentTimeMillis()
        var startTime = System.currentTimeMillis()
        var lastUrl = offlineVideo.lastSegmentUrl
        var initSegmentUri: String? = null
        val firstUrls = okHttpClient.newCall(Request.Builder().url(sourceUrl).build()).execute().use { response ->
            if (response.isSuccessful) {
                val playlist = try {
                    response.body()!!.byteStream().use {
                        PlaylistUtils.parseMediaPlaylist(it)
                    }
                } catch (e: Exception) {
                    return@withContext true
                }
                if (playlist.segments.isNotEmpty()) {
                    val urls = playlist.segments.takeLastWhile { it.uri != lastUrl }
                    urls.lastOrNull()?.let { lastUrl = it.uri }
                    val streamStartTime = urls.firstOrNull()?.programDateTime
                    if (offlineVideo.downloadChat && !streamStartTime.isNullOrBlank()) {
                        startChatJob(this, channelLogin, path, downloadDate, streamStartTime)
                    }
                    initSegmentUri = playlist.initSegmentUri
                    urls.map { it.uri }
                } else {
                    return@withContext false
                }
            } else {
                return@withContext false
            }
        }
        val videoFileUri = if (!offlineVideo.url.isNullOrBlank()) {
            val fileUri = offlineVideo.url!!
            if (isShared) {
                context.contentResolver.openFileDescriptor(fileUri.toUri(), "rw")!!.use {
                    FileOutputStream(it.fileDescriptor).use { output ->
                        output.channel.truncate(offlineVideo.bytes)
                    }
                }
            } else {
                FileOutputStream(fileUri).use { output ->
                    output.channel.truncate(offlineVideo.bytes)
                }
            }
            fileUri
        } else {
            val fileName = "${offlineVideo.channelLogin ?: ""}${offlineVideo.quality ?: ""}${downloadDate}.${firstUrls.first().substringAfterLast(".")}"
            val fileUri = if (isShared) {
                val directory = DocumentFile.fromTreeUri(applicationContext, path.toUri())!!
                (directory.findFile(fileName) ?: directory.createFile("", fileName))!!.uri.toString()
            } else {
                "$path${File.separator}$fileName"
            }
            val initSegmentBytes = initSegmentUri?.let {
                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                    if (isShared) {
                        context.contentResolver.openOutputStream(fileUri.toUri(), "wa")!!.sink().buffer()
                    } else {
                        File(fileUri).appendingSink().buffer()
                    }.use { sink ->
                        sink.writeAll(response.body()!!.source())
                    }
                    response.body()!!.contentLength()
                }
            }
            offlineRepository.updateVideo(offlineVideo.apply {
                url = fileUri
                initSegmentBytes?.let { bytes += it }
            })
            if (offlineVideo.name.isNullOrBlank()) {
                launch {
                    var attempt = 1
                    while (attempt <= 10) {
                        delay(10000L)
                        val stream = try {
                            repository.loadStream(
                                channelId = offlineVideo.channelId,
                                channelLogin = channelLogin,
                                helixHeaders = TwitchApiHelper.getHelixHeaders(context),
                                gqlHeaders = TwitchApiHelper.getGQLHeaders(context),
                                checkIntegrity = false
                            )
                        } catch (e: Exception) {
                            null
                        }
                        if (stream != null) {
                            val downloadedThumbnail = stream.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                                stream.thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                                    val filesDir = context.filesDir.path
                                    File(filesDir, "thumbnails").mkdir()
                                    val filePath = filesDir + File.separator + "thumbnails" + File.separator + id
                                    launch(Dispatchers.IO) {
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
                            offlineRepository.updateVideo(offlineVideo.apply {
                                name = stream.title
                                thumbnail = downloadedThumbnail
                                gameId = stream.gameId
                                gameSlug = stream.gameSlug
                                gameName = stream.gameName
                                uploadDate = stream.startedAt?.let { TwitchApiHelper.parseIso8601DateUTC(it) }
                            })
                            attempt += 10
                        }
                        attempt += 1
                    }
                }
            }
            fileUri
        }
        val requestSemaphore = Semaphore(context.prefs().getInt(C.DOWNLOAD_CONCURRENT_LIMIT, 10))
        if (isShared) {
            context.contentResolver.openOutputStream(videoFileUri.toUri(), "wa")!!.sink().buffer()
        } else {
            File(videoFileUri).appendingSink().buffer()
        }.use { sink ->
            val firstMutexMap = mutableMapOf<Int, Mutex>()
            val firstCount = MutableStateFlow(0)
            val firstCollector = launch {
                firstCount.collect {
                    firstMutexMap[it]?.unlock()
                    firstMutexMap.remove(it)
                }
            }
            val firstJobs = firstUrls.map {
                async {
                    requestSemaphore.withPermit {
                        okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                            val mutex = Mutex()
                            val id = firstUrls.indexOf(it)
                            if (firstCount.value != id) {
                                mutex.lock()
                                firstMutexMap[id] = mutex
                            }
                            mutex.withLock {
                                sink.writeAll(response.body()!!.source())
                                offlineRepository.updateVideo(offlineVideo.apply {
                                    bytes += response.body()!!.contentLength()
                                    chatBytes = chatPosition
                                    lastSegmentUrl = lastUrl
                                })
                            }
                        }
                        firstCount.update { it + 1 }
                    }
                }
            }
            firstJobs.awaitAll()
            firstCollector.cancel()
            while (true) {
                okHttpClient.newCall(Request.Builder().url(sourceUrl).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val playlist = try {
                            response.body()!!.byteStream().use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                        } catch (e: Exception) {
                            return@withContext true
                        }
                        if (playlist.segments.isNotEmpty()) {
                            val urls = playlist.segments.map { it.uri }.takeLastWhile { it != lastUrl }
                            urls.lastOrNull()?.let { lastUrl = it }
                            val mutexMap = mutableMapOf<Int, Mutex>()
                            val count = MutableStateFlow(0)
                            val collector = launch {
                                count.collect {
                                    mutexMap[it]?.unlock()
                                    mutexMap.remove(it)
                                }
                            }
                            val jobs = urls.map {
                                async {
                                    requestSemaphore.withPermit {
                                        okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                            val mutex = Mutex()
                                            val id = urls.indexOf(it)
                                            if (count.value != id) {
                                                mutex.lock()
                                                mutexMap[id] = mutex
                                            }
                                            mutex.withLock {
                                                sink.writeAll(response.body()!!.source())
                                                offlineRepository.updateVideo(offlineVideo.apply {
                                                    bytes += response.body()!!.contentLength()
                                                    chatBytes = chatPosition
                                                    lastSegmentUrl = lastUrl
                                                })
                                            }
                                        }
                                        count.update { it + 1 }
                                    }
                                }
                            }
                            jobs.awaitAll()
                            collector.cancel()
                            if (playlist.end) {
                                return@withContext true
                            }
                        } else {
                            return@withContext true
                        }
                    } else {
                        return@withContext true
                    }
                }
                val timeTaken = System.currentTimeMillis() - startTime
                if ((timeTaken) < liveCheck) {
                    delay(liveCheck - timeTaken)
                }
                startTime = System.currentTimeMillis()
            }
        }
        false
    }

    private fun startChatJob(coroutineScope: CoroutineScope, channelLogin: String, path: String, downloadDate: Long, streamStartTime: String) {
        coroutineScope.launch {
            val isShared = path.toUri().scheme == ContentResolver.SCHEME_CONTENT
            val fileName = "${channelLogin}${offlineVideo.quality ?: ""}${downloadDate}_chat.json"
            val resumed = !offlineVideo.chatUrl.isNullOrBlank()
            val savedTwitchEmotes = mutableListOf<String>()
            val savedBadges = mutableListOf<Pair<String, String>>()
            val savedEmotes = mutableListOf<String>()
            val fileUri = if (resumed) {
                val fileUri = offlineVideo.chatUrl!!
                if (isShared) {
                    context.contentResolver.openFileDescriptor(fileUri.toUri(), "rw")!!.use {
                        FileOutputStream(it.fileDescriptor).use { output ->
                            output.channel.truncate(offlineVideo.chatBytes)
                        }
                    }
                } else {
                    FileOutputStream(fileUri).use { output ->
                        output.channel.truncate(offlineVideo.chatBytes)
                    }
                }
                if (isShared) {
                    context.contentResolver.openOutputStream(fileUri.toUri(), "wa")!!.bufferedWriter()
                } else {
                    FileOutputStream(fileUri, true).bufferedWriter()
                }.use { fileWriter ->
                    fileWriter.write("}")
                }
                if (isShared) {
                    context.contentResolver.openInputStream(fileUri.toUri())?.bufferedReader()
                } else {
                    FileInputStream(File(fileUri)).bufferedReader()
                }?.use { fileReader ->
                    JsonReader(fileReader).use { reader ->
                        reader.isLenient = true
                        var token: JsonToken
                        do {
                            token = reader.peek()
                            when (token) {
                                JsonToken.END_DOCUMENT -> {}
                                JsonToken.BEGIN_OBJECT -> {
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.peek()) {
                                            JsonToken.NAME -> {
                                                when (reader.nextName()) {
                                                    "twitchEmotes" -> {
                                                        reader.beginArray()
                                                        while (reader.hasNext()) {
                                                            reader.beginObject()
                                                            var id: String? = null
                                                            while (reader.hasNext()) {
                                                                when (reader.nextName()) {
                                                                    "id" -> id = reader.nextString()
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            if (!id.isNullOrBlank()) {
                                                                savedTwitchEmotes.add(id)
                                                            }
                                                            reader.endObject()
                                                        }
                                                        reader.endArray()
                                                    }
                                                    "twitchBadges" -> {
                                                        reader.beginArray()
                                                        while (reader.hasNext()) {
                                                            reader.beginObject()
                                                            var setId: String? = null
                                                            var version: String? = null
                                                            while (reader.hasNext()) {
                                                                when (reader.nextName()) {
                                                                    "setId" -> setId = reader.nextString()
                                                                    "version" -> version = reader.nextString()
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            if (!setId.isNullOrBlank() && !version.isNullOrBlank()) {
                                                                savedBadges.add(Pair(setId, version))
                                                            }
                                                            reader.endObject()
                                                        }
                                                        reader.endArray()
                                                    }
                                                    "cheerEmotes" -> {
                                                        reader.beginArray()
                                                        while (reader.hasNext()) {
                                                            reader.beginObject()
                                                            var name: String? = null
                                                            while (reader.hasNext()) {
                                                                when (reader.nextName()) {
                                                                    "name" -> name = reader.nextString()
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            if (!name.isNullOrBlank()) {
                                                                savedEmotes.add(name)
                                                            }
                                                            reader.endObject()
                                                        }
                                                        reader.endArray()
                                                    }
                                                    "emotes" -> {
                                                        reader.beginArray()
                                                        while (reader.hasNext()) {
                                                            reader.beginObject()
                                                            var name: String? = null
                                                            while (reader.hasNext()) {
                                                                when (reader.nextName()) {
                                                                    "name" -> name = reader.nextString()
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            if (!name.isNullOrBlank()) {
                                                                savedEmotes.add(name)
                                                            }
                                                            reader.endObject()
                                                        }
                                                        reader.endArray()
                                                    }
                                                    else -> reader.skipValue()
                                                }
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                                else -> reader.skipValue()
                            }
                        } while (token != JsonToken.END_DOCUMENT)
                    }
                }
                fileUri
            } else {
                val fileUri = if (isShared) {
                    val directory = DocumentFile.fromTreeUri(applicationContext, path.toUri())
                    (directory?.findFile(fileName) ?: directory?.createFile("", fileName))!!.uri.toString()
                } else {
                    "$path${File.separator}$fileName"
                }
                offlineRepository.updateVideo(offlineVideo.apply {
                    chatUrl = fileUri
                })
                fileUri
            }
            val downloadEmotes = offlineVideo.downloadChatEmotes
            val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true)
            val helixHeaders = TwitchApiHelper.getHelixHeaders(context)
            val emoteQuality = context.prefs().getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4"
            val channelId = offlineVideo.channelId
            val badgeList = mutableListOf<TwitchBadge>().apply {
                if (downloadEmotes) {
                    val channelBadges = try { repository.loadChannelBadges(helixHeaders, gqlHeaders, channelId, channelLogin, emoteQuality, false) } catch (e: Exception) { emptyList() }
                    addAll(channelBadges)
                    val globalBadges = try { repository.loadGlobalBadges(helixHeaders, gqlHeaders, emoteQuality, false) } catch (e: Exception) { emptyList() }
                    addAll(globalBadges.filter { badge -> badge.setId !in channelBadges.map { it.setId } })
                }
            }
            val cheerEmoteList = if (downloadEmotes) {
                try {
                    repository.loadCheerEmotes(helixHeaders, gqlHeaders, channelId, channelLogin, animateGifs = true, checkIntegrity = false)
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
            val emoteList = mutableListOf<Emote>().apply {
                if (downloadEmotes) {
                    if (channelId != null) {
                        try { addAll(playerRepository.loadStvEmotes(channelId).second) } catch (e: Exception) {}
                        try { addAll(playerRepository.loadBttvEmotes(channelId)) } catch (e: Exception) {}
                        try { addAll(playerRepository.loadFfzEmotes(channelId)) } catch (e: Exception) {}
                    }
                    try { addAll(playerRepository.loadGlobalStvEmotes()) } catch (e: Exception) {}
                    try { addAll(playerRepository.loadGlobalBttvEmotes()) } catch (e: Exception) {}
                    try { addAll(playerRepository.loadGlobalFfzEmotes()) } catch (e: Exception) {}
                }
            }
            chatFileWriter = if (isShared) {
                context.contentResolver.openOutputStream(fileUri.toUri(), if (resumed) "wa" else "w")!!.bufferedWriter()
            } else {
                FileOutputStream(fileUri, resumed).bufferedWriter()
            }
            chatPosition = offlineVideo.chatBytes
            JsonWriter(chatFileWriter).let { writer ->
                var position = chatPosition
                writer.beginObject().also { position += 1 }
                if (!resumed) {
                    writer.name("video".also { position += it.length + 3 })
                    writer.beginObject().also { position += 1 }
                    offlineVideo.name?.let { value -> writer.name("title".also { position += it.length + 4 }).value(value.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 }) }
                    offlineVideo.uploadDate?.let { value -> writer.name("uploadDate".also { position += it.length + 4 }).value(value.also { position += it.toString().length }) }
                    offlineVideo.channelId?.let { value -> writer.name("channelId".also { position += it.length + 4 }).value(value.also { position += it.length + 2 }) }
                    offlineVideo.channelLogin?.let { value -> writer.name("channelLogin".also { position += it.length + 4 }).value(value.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 }) }
                    offlineVideo.channelName?.let { value -> writer.name("channelName".also { position += it.length + 4 }).value(value.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 }) }
                    offlineVideo.gameId?.let { value -> writer.name("gameId".also { position += it.length + 4 }).value(value.also { position += it.length + 2 }) }
                    offlineVideo.gameSlug?.let { value -> writer.name("gameSlug".also { position += it.length + 4 }).value(value.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 }) }
                    offlineVideo.gameName?.let { value -> writer.name("gameName".also { position += it.length + 4 }).value(value.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 }) }
                    writer.endObject()
                    writer.name("liveStartTime".also { position += it.length + 4 }).value(streamStartTime.also { position += it.length + 2 })
                }
                chatPosition = position
                chatReadWebSocket = ChatReadWebSocket(false, channelLogin, okHttpClient, coroutineScope,
                    webSocketListener = object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            chatReadWebSocket?.apply {
                                isActive = true
                                write("CAP REQ :twitch.tv/tags twitch.tv/commands")
                                write("NICK justinfan${Random().nextInt(((9999 - 1000) + 1)) + 1000}") //random number between 1000 and 9999
                                write("JOIN $hashChannelName")
                                ping()
                            }
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val list = mutableListOf<String>()
                            text.removeSuffix("\r\n").split("\r\n").forEach {
                                it.run {
                                    when {
                                        contains("PRIVMSG") -> list.add(this)
                                        contains("USERNOTICE") -> list.add(this)
                                        contains("CLEARMSG") -> list.add(this)
                                        contains("CLEARCHAT") -> list.add(this)
                                        contains("NOTICE") -> {}
                                        contains("ROOMSTATE") -> {}
                                        startsWith("PING") -> chatReadWebSocket?.write("PONG")
                                        startsWith("PONG") -> chatReadWebSocket?.pongReceived = true
                                        startsWith("RECONNECT") -> chatReadWebSocket?.reconnect()
                                    }
                                }
                            }
                            if (list.isNotEmpty()) {
                                writer.name("liveComments".also { position += it.length + 4 })
                                writer.beginArray().also { position += 1 }
                                list.forEach { message ->
                                    writer.value(message.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 3 })
                                }
                                writer.endArray()
                                if (downloadEmotes) {
                                    list.forEach { message ->
                                        val userNotice = when {
                                            message.contains("PRIVMSG") -> false
                                            message.contains("USERNOTICE") -> true
                                            else -> null
                                        }
                                        if (userNotice != null) {
                                            val chatMessage = ChatUtils.parseChatMessage(message, userNotice)
                                            val twitchEmotes = mutableListOf<TwitchEmote>()
                                            val twitchBadges = mutableListOf<TwitchBadge>()
                                            val cheerEmotes = mutableListOf<CheerEmote>()
                                            val emotes = mutableListOf<Emote>()
                                            chatMessage.emotes?.forEach {
                                                if (it.id != null && !savedTwitchEmotes.contains(it.id)) {
                                                    savedTwitchEmotes.add(it.id)
                                                    twitchEmotes.add(it)
                                                }
                                            }
                                            chatMessage.badges?.forEach {
                                                val pair = Pair(it.setId, it.version)
                                                if (!savedBadges.contains(pair)) {
                                                    savedBadges.add(pair)
                                                    val badge = badgeList.find { badge -> badge.setId == it.setId && badge.version == it.version }
                                                    if (badge != null) {
                                                        twitchBadges.add(badge)
                                                    }
                                                }
                                            }
                                            chatMessage.message?.split(" ")?.forEach { word ->
                                                if (!savedEmotes.contains(word)) {
                                                    val cheerEmote = if (chatMessage.bits != null) {
                                                        val bitsCount = word.takeLastWhile { it.isDigit() }
                                                        val bitsName = word.substringBeforeLast(bitsCount)
                                                        if (bitsCount.isNotEmpty()) {
                                                            cheerEmoteList.findLast { it.name.equals(bitsName, true) && it.minBits <= bitsCount.toInt() }
                                                        } else null
                                                    } else null
                                                    if (cheerEmote != null) {
                                                        savedEmotes.add(word)
                                                        cheerEmotes.add(cheerEmote)
                                                    } else {
                                                        val emote = emoteList.find { it.name == word }
                                                        if (emote != null) {
                                                            savedEmotes.add(word)
                                                            emotes.add(emote)
                                                        }
                                                    }
                                                }
                                            }
                                            if (twitchEmotes.isNotEmpty()) {
                                                writer.name("twitchEmotes".also { position += it.length + 4 })
                                                writer.beginArray().also { position += 1 }
                                                val last = twitchEmotes.lastOrNull()
                                                twitchEmotes.forEach { emote ->
                                                    val url = when (emoteQuality) {
                                                        "4" -> emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x
                                                        "3" -> emote.url3x ?: emote.url2x ?: emote.url1x
                                                        "2" -> emote.url2x ?: emote.url1x
                                                        else -> emote.url1x
                                                    }!!
                                                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                                        writer.beginObject().also { position += 1 }
                                                        writer.name("data".also { position += it.length + 3 }).value(Base64.encodeToString(response.body()!!.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING).also { position += it.toByteArray().size + 2 })
                                                        writer.name("id".also { position += it.length + 4 }).value(emote.id.also { position += it.toString().toByteArray().size + it.toString().count { c -> c == '"' || c == '\\' } + 2 })
                                                        writer.endObject().also { position += 1 }
                                                    }
                                                    if (emote != last) {
                                                        position += 1
                                                    }
                                                }
                                                writer.endArray().also { position += 1 }
                                            }
                                            if (twitchBadges.isNotEmpty()) {
                                                writer.name("twitchBadges".also { position += it.length + 4 })
                                                writer.beginArray().also { position += 1 }
                                                val last = twitchBadges.lastOrNull()
                                                twitchBadges.forEach { badge ->
                                                    val url = when (emoteQuality) {
                                                        "4" -> badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x
                                                        "3" -> badge.url3x ?: badge.url2x ?: badge.url1x
                                                        "2" -> badge.url2x ?: badge.url1x
                                                        else -> badge.url1x
                                                    }!!
                                                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                                        writer.beginObject().also { position += 1 }
                                                        writer.name("data".also { position += it.length + 3 }).value(Base64.encodeToString(response.body()!!.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING).also { position += it.toByteArray().size + 2 })
                                                        writer.name("setId".also { position += it.length + 4 }).value(badge.setId.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 })
                                                        writer.name("version".also { position += it.length + 4 }).value(badge.version.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 })
                                                        writer.endObject().also { position += 1 }
                                                    }
                                                    if (badge != last) {
                                                        position += 1
                                                    }
                                                }
                                                writer.endArray().also { position += 1 }
                                            }
                                            if (cheerEmotes.isNotEmpty()) {
                                                writer.name("cheerEmotes".also { position += it.length + 4 })
                                                writer.beginArray().also { position += 1 }
                                                val last = cheerEmotes.lastOrNull()
                                                cheerEmotes.forEach { cheerEmote ->
                                                    val url = when (emoteQuality) {
                                                        "4" -> cheerEmote.url4x ?: cheerEmote.url3x ?: cheerEmote.url2x ?: cheerEmote.url1x
                                                        "3" -> cheerEmote.url3x ?: cheerEmote.url2x ?: cheerEmote.url1x
                                                        "2" -> cheerEmote.url2x ?: cheerEmote.url1x
                                                        else -> cheerEmote.url1x
                                                    }!!
                                                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                                        writer.beginObject().also { position += 1 }
                                                        writer.name("data".also { position += it.length + 3 }).value(Base64.encodeToString(response.body()!!.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING).also { position += it.toByteArray().size + 2 })
                                                        writer.name("name".also { position += it.length + 4 }).value(cheerEmote.name.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 })
                                                        writer.name("minBits".also { position += it.length + 4 }).value(cheerEmote.minBits.also { position += it.toString().length })
                                                        cheerEmote.color?.let { value -> writer.name("color".also { position += it.length + 4 }).value(value.also { position += it.toByteArray().size + it.count { c -> c == '"' || c == '\\' } + 2 }) }
                                                        writer.endObject().also { position += 1 }
                                                    }
                                                    if (cheerEmote != last) {
                                                        position += 1
                                                    }
                                                }
                                                writer.endArray().also { position += 1 }
                                            }
                                            if (emotes.isNotEmpty()) {
                                                writer.name("emotes".also { position += it.length + 4 })
                                                writer.beginArray().also { position += 1 }
                                                val last = emotes.lastOrNull()
                                                emotes.forEach { emote ->
                                                    val url = when (emoteQuality) {
                                                        "4" -> emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x
                                                        "3" -> emote.url3x ?: emote.url2x ?: emote.url1x
                                                        "2" -> emote.url2x ?: emote.url1x
                                                        else -> emote.url1x
                                                    }!!
                                                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                                        writer.beginObject().also { position += 1 }
                                                        writer.name("data".also { position += it.length + 3 }).value(Base64.encodeToString(response.body()!!.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING).also { position += it.toByteArray().size + 2 })
                                                        writer.name("name".also { position += it.length + 4 }).value(emote.name.also { position += it.toString().toByteArray().size + it.toString().count { c -> c == '"' || c == '\\' } + 2 })
                                                        writer.name("isZeroWidth".also { position += it.length + 4 }).value(emote.isZeroWidth.also { position += it.toString().length })
                                                        writer.endObject().also { position += 1 }
                                                    }
                                                    if (emote != last) {
                                                        position += 1
                                                    }
                                                }
                                                writer.endArray().also { position += 1 }
                                            }
                                        }
                                    }
                                }
                                chatPosition = position
                            }
                        }
                    }
                ).apply { connect() }
            }
        }
    }

    private fun createForegroundInfo(live: Boolean, offlineVideo: OfflineVideo): ForegroundInfo {
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
            setContentTitle(ContextCompat.getString(context, if (live) R.string.downloading else R.string.download_waiting_for_stream))
            setContentText(offlineVideo.channelName)
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setContentIntent(
                PendingIntent.getActivity(
                    context,
                    offlineVideo.id,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        action = MainActivity.INTENT_OPEN_DOWNLOADS_TAB
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            addAction(android.R.drawable.ic_delete, ContextCompat.getString(context, R.string.stop), WorkManager.getInstance(context).createCancelPendingIntent(id))
        }.build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(offlineVideo.id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(offlineVideo.id, notification)
        }
    }

    companion object {
        const val GROUP_KEY = "com.github.andreyasadchy.xtra.DOWNLOADS"

        const val KEY_VIDEO_ID = "KEY_VIDEO_ID"
    }
}
