package com.github.andreyasadchy.xtra.ui.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver.SCHEME_CONTENT
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.util.Base64
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
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import com.iheartradio.m3u8.data.TrackData
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.min

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    @Inject
    lateinit var repository: ApiRepository

    @Inject
    lateinit var playerRepository: PlayerRepository

    @Inject
    lateinit var graphQLRepository: GraphQLRepository

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
                    response.body.byteStream().use {
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
                                            sink.writeAll(response.body.source())
                                        }
                                    } else {
                                        File(videoFileUri).appendingSink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
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
                val chatJob = startChatJob(this, path)
                jobs.awaitAll()
                collector.cancel()
                chatJob.join()
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
                    val chatJob = startChatJob(this, if (isShared) {
                        offlineVideo.url.substringBefore("/document/")
                    } else {
                        offlineVideo.url.substringBeforeLast("/").substringBeforeLast("/")
                    })
                    jobs.awaitAll()
                    chatJob.join()
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
                sink.writeAll(response.body.source())
            }
        }
    }

    private fun download(url: String, output: String) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            File(output).sink().buffer().use { sink ->
                sink.writeAll(response.body.source())
            }
        }
    }

    private fun startChatJob(coroutineScope: CoroutineScope, path: String): Job {
        return coroutineScope.launch {
            if (offlineVideo.downloadChat == true && offlineVideo.chatUrl == null) {
                offlineVideo.videoId?.let { videoId ->
                    val isShared = path.toUri().scheme == SCHEME_CONTENT
                    val fileName = "${videoId}${offlineVideo.quality ?: ""}${offlineVideo.downloadDate}_chat.json"
                    val fileUri = if (isShared) {
                        val directory = DocumentFile.fromTreeUri(applicationContext, path.toUri())
                        (directory?.findFile(fileName) ?: directory?.createFile("", fileName))!!.uri.toString()
                    } else {
                        "$path${File.separator}$fileName"
                    }
                    val downloadEmotes = offlineVideo.downloadChatEmotes == true
                    val startTimeSeconds = (offlineVideo.sourceStartPosition!! / 1000).toInt()
                    val durationSeconds = (offlineVideo.duration!! / 1000).toInt()
                    val endTimeSeconds = startTimeSeconds + durationSeconds
                    val gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true)
                    val helixClientId = context.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi")
                    val helixToken = Account.get(context).helixToken
                    val emoteQuality = context.prefs().getString(C.CHAT_IMAGE_QUALITY, "4") ?: "4"
                    val channelId = offlineVideo.channelId
                    val channelLogin = offlineVideo.channelLogin
                    val savedTwitchEmotes = mutableListOf<String>()
                    val badgeList = mutableListOf<TwitchBadge>().apply {
                        if (downloadEmotes) {
                            val channelBadges = try { repository.loadChannelBadges(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, emoteQuality, false) } catch (e: Exception) { emptyList() }
                            addAll(channelBadges)
                            val globalBadges = try { repository.loadGlobalBadges(helixClientId, helixToken, gqlHeaders, emoteQuality, false) } catch (e: Exception) { emptyList() }
                            addAll(globalBadges.filter { badge -> badge.setId !in channelBadges.map { it.setId } })
                        }
                    }
                    val savedBadges = mutableListOf<TwitchBadge>()
                    val cheerEmoteList = if (downloadEmotes) {
                        try {
                            repository.loadCheerEmotes(helixClientId, helixToken, gqlHeaders, channelId, channelLogin, animateGifs = true, checkIntegrity = false)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } else emptyList()
                    val emoteList = mutableListOf<Emote>().apply {
                        if (downloadEmotes) {
                            if (channelId != null) {
                                try { playerRepository.loadStvEmotes(channelId).body()?.emotes?.let { addAll(it) } } catch (e: Exception) {}
                                try { playerRepository.loadBttvEmotes(channelId).body()?.emotes?.let { addAll(it) } } catch (e: Exception) {}
                                try { playerRepository.loadFfzEmotes(channelId).body()?.emotes?.let { addAll(it) } } catch (e: Exception) {}
                            }
                            try { playerRepository.loadGlobalStvEmotes().body()?.emotes?.let { addAll(it) } } catch (e: Exception) {}
                            try { playerRepository.loadGlobalBttvEmotes().body()?.emotes?.let { addAll(it) } } catch (e: Exception) {}
                            try { playerRepository.loadGlobalFfzEmotes().body()?.emotes?.let { addAll(it) } } catch (e: Exception) {}
                        }
                    }
                    val savedEmotes = mutableListOf<String>()
                    val comments = mutableListOf<JsonObject>()
                    val twitchEmotes = mutableListOf<JSONObject>()
                    val twitchBadges = mutableListOf<JSONObject>()
                    val cheerEmotes = mutableListOf<JSONObject>()
                    val emotes = mutableListOf<JSONObject>()
                    var cursor: String? = null
                    do {
                        val get = if (cursor == null) {
                            graphQLRepository.loadVideoMessagesDownload(gqlHeaders, videoId, offset = startTimeSeconds)
                        } else {
                            graphQLRepository.loadVideoMessagesDownload(gqlHeaders, videoId, cursor = cursor)
                        }
                        cursor = get.cursor
                        comments.addAll(get.data)
                        if (downloadEmotes) {
                            get.emotes.forEach {
                                if (!savedTwitchEmotes.contains(it)) {
                                    savedTwitchEmotes.add(it)
                                    val emote = TwitchEmote(id = it)
                                    val url = when (emoteQuality) {
                                        "4" -> emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x
                                        "3" -> emote.url3x ?: emote.url2x ?: emote.url1x
                                        "2" -> emote.url2x ?: emote.url1x
                                        else -> emote.url1x
                                    }!!
                                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                        twitchEmotes.add(JSONObject().apply {
                                            put("data", Base64.encodeToString(response.body.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING))
                                            put("id", it)
                                        })
                                    }
                                }
                            }
                            get.badges.forEach {
                                val badge = badgeList.find { badge -> badge.setId == it.setId && badge.version == it.version }
                                if (badge != null && !savedBadges.contains(badge)) {
                                    savedBadges.add(badge)
                                    val url = when (emoteQuality) {
                                        "4" -> badge.url4x ?: badge.url3x ?: badge.url2x ?: badge.url1x
                                        "3" -> badge.url3x ?: badge.url2x ?: badge.url1x
                                        "2" -> badge.url2x ?: badge.url1x
                                        else -> badge.url1x
                                    }!!
                                    okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                        twitchBadges.add(JSONObject().apply {
                                            put("data", Base64.encodeToString(response.body.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING))
                                            put("setId", badge.setId)
                                            put("version", badge.version)
                                        })
                                    }
                                }
                            }
                            get.words.forEach { word ->
                                if (!savedEmotes.contains(word)) {
                                    val bitsCount = word.takeLastWhile { it.isDigit() }
                                    val cheerEmote = if (bitsCount.isNotEmpty()) {
                                        val bitsName = word.substringBeforeLast(bitsCount)
                                        cheerEmoteList.findLast { it.name.equals(bitsName, true) && it.minBits <= bitsCount.toInt() }
                                    } else null
                                    if (cheerEmote != null) {
                                        savedEmotes.add(word)
                                        val url = when (emoteQuality) {
                                            "4" -> cheerEmote.url4x ?: cheerEmote.url3x ?: cheerEmote.url2x ?: cheerEmote.url1x
                                            "3" -> cheerEmote.url3x ?: cheerEmote.url2x ?: cheerEmote.url1x
                                            "2" -> cheerEmote.url2x ?: cheerEmote.url1x
                                            else -> cheerEmote.url1x
                                        }!!
                                        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                            cheerEmotes.add(JSONObject().apply {
                                                put("data", Base64.encodeToString(response.body.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING))
                                                put("name", cheerEmote.name)
                                                put("minBits", cheerEmote.minBits)
                                                put("color", cheerEmote.color)
                                            })
                                        }
                                    } else {
                                        val emote = emoteList.find { it.name == word }
                                        if (emote != null) {
                                            savedEmotes.add(word)
                                            val url = when (emoteQuality) {
                                                "4" -> emote.url4x ?: emote.url3x ?: emote.url2x ?: emote.url1x
                                                "3" -> emote.url3x ?: emote.url2x ?: emote.url1x
                                                "2" -> emote.url2x ?: emote.url1x
                                                else -> emote.url1x
                                            }!!
                                            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                                                emotes.add(JSONObject().apply {
                                                    put("data", Base64.encodeToString(response.body.source().readByteArray(), Base64.NO_WRAP or Base64.NO_PADDING))
                                                    put("name", emote.name)
                                                    put("isZeroWidth", emote.isZeroWidth)
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (get.lastOffsetSeconds != null) {
                            offlineRepository.updateVideo(offlineVideo.apply {
                                chatProgress = min((((get.lastOffsetSeconds - startTimeSeconds).toFloat() / durationSeconds) * 100f).toInt(), 100)
                            })
                        }
                    } while (get.lastOffsetSeconds?.let { it < endTimeSeconds } != false && !get.cursor.isNullOrBlank() && get.hasNextPage != false)
                    if (isShared) {
                        context.contentResolver.openOutputStream(fileUri.toUri())!!.bufferedWriter()
                    } else {
                        FileOutputStream(fileUri).bufferedWriter()
                    }.use { fileWriter ->
                        JsonWriter(fileWriter).use { writer ->
                            writer.beginObject()
                            writer.name("comments")
                            writer.beginArray()
                            comments.forEach { json ->
                                writer.beginObject()
                                json.keySet().forEach { key ->
                                    writeJsonElement(key, json.get(key), writer)
                                }
                                writer.endObject()
                            }
                            writer.endArray()
                            if (downloadEmotes) {
                                writer.name("twitchEmotes")
                                writer.beginArray()
                                twitchEmotes.forEach { json ->
                                    writer.beginObject()
                                    json.keys().forEach { key ->
                                        when (val value = json.get(key)) {
                                            is String -> writer.name(key).value(value)
                                            is Boolean -> writer.name(key).value(value)
                                            is Int -> writer.name(key).value(value)
                                        }
                                    }
                                    writer.endObject()
                                }
                                writer.endArray()
                                writer.name("twitchBadges")
                                writer.beginArray()
                                twitchBadges.forEach { json ->
                                    writer.beginObject()
                                    json.keys().forEach { key ->
                                        when (val value = json.get(key)) {
                                            is String -> writer.name(key).value(value)
                                            is Boolean -> writer.name(key).value(value)
                                            is Int -> writer.name(key).value(value)
                                        }
                                    }
                                    writer.endObject()
                                }
                                writer.endArray()
                                writer.name("cheerEmotes")
                                writer.beginArray()
                                cheerEmotes.forEach { json ->
                                    writer.beginObject()
                                    json.keys().forEach { key ->
                                        when (val value = json.get(key)) {
                                            is String -> writer.name(key).value(value)
                                            is Boolean -> writer.name(key).value(value)
                                            is Int -> writer.name(key).value(value)
                                        }
                                    }
                                    writer.endObject()
                                }
                                writer.endArray()
                                writer.name("emotes")
                                writer.beginArray()
                                emotes.forEach { json ->
                                    writer.beginObject()
                                    json.keys().forEach { key ->
                                        when (val value = json.get(key)) {
                                            is String -> writer.name(key).value(value)
                                            is Boolean -> writer.name(key).value(value)
                                            is Int -> writer.name(key).value(value)
                                        }
                                    }
                                    writer.endObject()
                                }
                                writer.endArray()
                            }
                            writer.name("startTime").value(startTimeSeconds)
                            writer.name("video")
                            writer.beginObject()
                            writer.name("id").value(videoId)
                            writer.name("title").value(offlineVideo.name)
                            writer.name("uploadDate").value(offlineVideo.uploadDate)
                            writer.name("channelId").value(offlineVideo.channelId)
                            writer.name("channelLogin").value(offlineVideo.channelLogin)
                            writer.name("channelName").value(offlineVideo.channelName)
                            writer.name("gameId").value(offlineVideo.gameId)
                            writer.name("gameSlug").value(offlineVideo.gameSlug)
                            writer.name("gameName").value(offlineVideo.gameName)
                            writer.endObject()
                            writer.endObject()
                        }
                    }
                    offlineRepository.updateVideo(offlineVideo.apply {
                        chatUrl = fileUri
                    })
                }
            }
        }
    }

    private fun writeJsonElement(key: String, value: JsonElement, writer: JsonWriter) {
        when {
            value.isJsonObject -> {
                writer.name(key)
                writer.beginObject()
                value.asJsonObject.entrySet().forEach {
                    writeJsonElement(it.key, it.value, writer)
                }
                writer.endObject()
            }
            value.isJsonArray -> {
                writer.name(key)
                writer.beginArray()
                value.asJsonArray.forEach { json ->
                    if (json.isJsonObject) {
                        writer.beginObject()
                        json.asJsonObject.entrySet().forEach {
                            writeJsonElement(it.key, it.value, writer)
                        }
                        writer.endObject()
                    }
                }
                writer.endArray()
            }
            value.isJsonPrimitive -> {
                when {
                    value.asJsonPrimitive.isString -> writer.name(key).value(value.asString)
                    value.asJsonPrimitive.isBoolean -> writer.name(key).value(value.asBoolean)
                    value.asJsonPrimitive.isNumber -> writer.name(key).value(value.asNumber)
                }
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
