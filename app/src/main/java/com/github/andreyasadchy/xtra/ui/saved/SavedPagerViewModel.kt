package com.github.andreyasadchy.xtra.ui.saved

import android.content.Context
import android.os.Build
import android.util.JsonReader
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import com.iheartradio.m3u8.PlaylistWriter
import com.iheartradio.m3u8.data.Playlist
import com.iheartradio.m3u8.data.TrackData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.use
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class SavedPagerViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val offlineRepository: OfflineRepository) : ViewModel() {

    fun saveFolders(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val chatFiles = mutableMapOf<String, String>()
                val directory = DocumentFile.fromTreeUri(applicationContext, url.substringBefore("/document/").toUri())
                directory?.listFiles()?.let { files ->
                    files.filter { it.isFile && it.name?.endsWith(".json") == true }.forEach { chatFile ->
                        chatFile.name?.let {
                            chatFiles[it.removeSuffix(".json").removeSuffix("_chat")] = chatFile.uri.toString()
                        }
                    }
                    files.filter { it.isDirectory }.forEach { videoDirectory ->
                        videoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true }.forEach { playlistFile ->
                            val existingVideo = offlineRepository.getVideoByUrl(playlistFile.uri.toString())
                            if (existingVideo == null) {
                                val playlist = applicationContext.contentResolver.openInputStream(playlistFile.uri).use {
                                    PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                                }
                                var totalDuration = 0L
                                val tracks = ArrayList<TrackData>()
                                playlist.tracks.forEach { track ->
                                    totalDuration += (track.trackInfo.duration * 1000f).toLong()
                                    tracks.add(
                                        track.buildUpon()
                                            .withUri(videoDirectory?.uri.toString() + "%2F" + track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                            .build()
                                    )
                                }
                                applicationContext.contentResolver.openOutputStream(playlistFile.uri).use {
                                    PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                                }
                                val chatFile = chatFiles[videoDirectory.name + playlistFile.name?.removeSuffix(".m3u8")]
                                var id: String? = null
                                var title: String? = null
                                var uploadDate: Long? = null
                                var channelId: String? = null
                                var channelLogin: String? = null
                                var channelName: String? = null
                                var gameId: String? = null
                                var gameSlug: String? = null
                                var gameName: String? = null
                                chatFile?.let { uri ->
                                    try {
                                        applicationContext.contentResolver.openInputStream(uri.toUri())?.bufferedReader()?.use { fileReader ->
                                            JsonReader(fileReader).use { reader ->
                                                reader.beginObject()
                                                while (reader.hasNext()) {
                                                    when (reader.nextName()) {
                                                        "video" -> {
                                                            reader.beginObject()
                                                            while (reader.hasNext()) {
                                                                when (reader.nextName()) {
                                                                    "id" -> id = reader.nextString()
                                                                    "title" -> title = reader.nextString()
                                                                    "uploadDate" -> uploadDate = reader.nextLong()
                                                                    "channelId" -> channelId = reader.nextString()
                                                                    "channelLogin" -> channelLogin = reader.nextString()
                                                                    "channelName" -> channelName = reader.nextString()
                                                                    "gameId" -> gameId = reader.nextString()
                                                                    "gameSlug" -> gameSlug = reader.nextString()
                                                                    "gameName" -> gameName = reader.nextString()
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            reader.endObject()
                                                        }
                                                        else -> reader.skipValue()
                                                    }
                                                }
                                                reader.endObject()
                                            }
                                        }
                                    } catch (e: Exception) {

                                    }
                                }
                                offlineRepository.saveVideo(OfflineVideo(
                                    url = playlistFile.uri.toString(),
                                    name = if (!title.isNullOrBlank()) title else null ?: videoDirectory.name,
                                    channelId = if (!channelId.isNullOrBlank()) channelId else null,
                                    channelLogin = if (!channelLogin.isNullOrBlank()) channelLogin else null,
                                    channelName = if (!channelName.isNullOrBlank()) channelName else null,
                                    thumbnail = tracks.getOrNull(max(0,  (tracks.size / 2) - 1))?.uri,
                                    gameId = if (!gameId.isNullOrBlank()) gameId else null,
                                    gameSlug = if (!gameSlug.isNullOrBlank()) gameSlug else null,
                                    gameName = if (!gameName.isNullOrBlank()) gameName else null,
                                    duration = totalDuration,
                                    uploadDate = if (uploadDate != null) uploadDate else null,
                                    progress = 100,
                                    maxProgress = 100,
                                    status = OfflineVideo.STATUS_DOWNLOADED,
                                    videoId = if (!id.isNullOrBlank()) id else null,
                                    chatUrl = chatFile
                                ))
                            }
                        }
                    }
                }
            } else {
                val chatFiles = mutableMapOf<String, String>()
                File(url).listFiles()?.let { files ->
                    files.filter { it.isFile && it.name.endsWith(".json") }.forEach { chatFile ->
                        chatFile.name.let {
                            chatFiles[it.removeSuffix(".json").removeSuffix("_chat")] = chatFile.path
                        }
                    }
                    files.filter { it.isDirectory }.forEach { videoDirectory ->
                        videoDirectory.listFiles()?.filter { it.name.endsWith(".m3u8") }?.forEach { playlistFile ->
                            val existingVideo = offlineRepository.getVideoByUrl(playlistFile.path)
                            if (existingVideo == null) {
                                val playlist = FileInputStream(playlistFile).use {
                                    PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                                }
                                var totalDuration = 0L
                                val tracks = ArrayList<TrackData>()
                                playlist.tracks.forEach { track ->
                                    totalDuration += (track.trackInfo.duration * 1000f).toLong()
                                    tracks.add(
                                        track.buildUpon()
                                            .withUri(track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                            .build()
                                    )
                                }
                                FileOutputStream(playlistFile).use {
                                    PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                                }
                                val chatFile = chatFiles[videoDirectory.name + playlistFile.name.removeSuffix(".m3u8")]
                                var id: String? = null
                                var title: String? = null
                                var uploadDate: Long? = null
                                var channelId: String? = null
                                var channelLogin: String? = null
                                var channelName: String? = null
                                var gameId: String? = null
                                var gameSlug: String? = null
                                var gameName: String? = null
                                chatFile?.let { uri ->
                                    try {
                                        FileInputStream(File(uri)).bufferedReader().use { fileReader ->
                                            JsonReader(fileReader).use { reader ->
                                                reader.beginObject()
                                                while (reader.hasNext()) {
                                                    when (reader.nextName()) {
                                                        "video" -> {
                                                            reader.beginObject()
                                                            while (reader.hasNext()) {
                                                                when (reader.nextName()) {
                                                                    "id" -> id = reader.nextString()
                                                                    "title" -> title = reader.nextString()
                                                                    "uploadDate" -> uploadDate = reader.nextLong()
                                                                    "channelId" -> channelId = reader.nextString()
                                                                    "channelLogin" -> channelLogin = reader.nextString()
                                                                    "channelName" -> channelName = reader.nextString()
                                                                    "gameId" -> gameId = reader.nextString()
                                                                    "gameSlug" -> gameSlug = reader.nextString()
                                                                    "gameName" -> gameName = reader.nextString()
                                                                    else -> reader.skipValue()
                                                                }
                                                            }
                                                            reader.endObject()
                                                        }

                                                        else -> reader.skipValue()
                                                    }
                                                }
                                                reader.endObject()
                                            }
                                        }
                                    } catch (e: Exception) {

                                    }
                                }
                                offlineRepository.saveVideo(OfflineVideo(
                                    url = playlistFile.path,
                                    name = if (!title.isNullOrBlank()) title else null ?: videoDirectory.name,
                                    channelId = if (!channelId.isNullOrBlank()) channelId else null,
                                    channelLogin = if (!channelLogin.isNullOrBlank()) channelLogin else null,
                                    channelName = if (!channelName.isNullOrBlank()) channelName else null,
                                    thumbnail = videoDirectory.path + File.separator + tracks.getOrNull(max(0,  (tracks.size / 2) - 1))?.uri,
                                    gameId = if (!gameId.isNullOrBlank()) gameId else null,
                                    gameSlug = if (!gameSlug.isNullOrBlank()) gameSlug else null,
                                    gameName = if (!gameName.isNullOrBlank()) gameName else null,
                                    duration = totalDuration,
                                    uploadDate = if (uploadDate != null) uploadDate else null,
                                    progress = 100,
                                    maxProgress = 100,
                                    status = OfflineVideo.STATUS_DOWNLOADED,
                                    videoId = if (!id.isNullOrBlank()) id else null,
                                    chatUrl = chatFile
                                ))
                            }
                        }
                    }
                }
            }
        }
    }

    fun saveVideos(list: List<String>) {
        viewModelScope.launch {
            val chatFiles = mutableMapOf<String, String>()
            list.filter { it.endsWith(".json") }.forEach {
                chatFiles[it.substringAfterLast("%2F").substringAfterLast("/").removeSuffix(".json").removeSuffix("_chat")] = it
            }
            list.filter { !it.endsWith(".json") }.forEach { url ->
                val existingVideo = offlineRepository.getVideoByUrl(url)
                if (existingVideo == null) {
                    val fileName = url.substringAfterLast("%2F").substringAfterLast("/").removeSuffix(".mp4").removeSuffix(".ts")
                    val chatFile = chatFiles[fileName]
                    var id: String? = null
                    var title: String? = null
                    var uploadDate: Long? = null
                    var channelId: String? = null
                    var channelLogin: String? = null
                    var channelName: String? = null
                    var gameId: String? = null
                    var gameSlug: String? = null
                    var gameName: String? = null
                    chatFile?.let { uri ->
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                applicationContext.contentResolver.openInputStream(uri.toUri())?.bufferedReader()
                            } else {
                                FileInputStream(File(uri)).bufferedReader()
                            }?.use { fileReader ->
                                JsonReader(fileReader).use { reader ->
                                    reader.beginObject()
                                    while (reader.hasNext()) {
                                        when (reader.nextName()) {
                                            "video" -> {
                                                reader.beginObject()
                                                while (reader.hasNext()) {
                                                    when (reader.nextName()) {
                                                        "id" -> id = reader.nextString()
                                                        "title" -> title = reader.nextString()
                                                        "uploadDate" -> uploadDate = reader.nextLong()
                                                        "channelId" -> channelId = reader.nextString()
                                                        "channelLogin" -> channelLogin = reader.nextString()
                                                        "channelName" -> channelName = reader.nextString()
                                                        "gameId" -> gameId = reader.nextString()
                                                        "gameSlug" -> gameSlug = reader.nextString()
                                                        "gameName" -> gameName = reader.nextString()
                                                        else -> reader.skipValue()
                                                    }
                                                }
                                                reader.endObject()
                                            }
                                            else -> reader.skipValue()
                                        }
                                    }
                                    reader.endObject()
                                }
                            }
                        } catch (e: Exception) {

                        }
                    }
                    offlineRepository.saveVideo(OfflineVideo(
                        url = url,
                        name = if (!title.isNullOrBlank()) title else null ?: fileName,
                        channelId = if (!channelId.isNullOrBlank()) channelId else null,
                        channelLogin = if (!channelLogin.isNullOrBlank()) channelLogin else null,
                        channelName = if (!channelName.isNullOrBlank()) channelName else null,
                        thumbnail = url,
                        gameId = if (!gameId.isNullOrBlank()) gameId else null,
                        gameSlug = if (!gameSlug.isNullOrBlank()) gameSlug else null,
                        gameName = if (!gameName.isNullOrBlank()) gameName else null,
                        uploadDate = if (uploadDate != null) uploadDate else null,
                        progress = 100,
                        maxProgress = 100,
                        status = OfflineVideo.STATUS_DOWNLOADED,
                        videoId = if (!id.isNullOrBlank()) id else null,
                        chatUrl = chatFile
                    ))
                }
            }
        }
    }
}
