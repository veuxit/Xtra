package com.github.andreyasadchy.xtra.ui.saved

import android.content.Context
import android.util.JsonReader
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import com.github.andreyasadchy.xtra.util.m3u8.Segment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class SavedPagerViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val offlineRepository: OfflineRepository) : ViewModel() {

    fun saveFolders(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
                            val playlist = applicationContext.contentResolver.openInputStream(playlistFile.uri)!!.use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            var totalDuration = 0L
                            val segments = ArrayList<Segment>()
                            playlist.segments.forEach { segment ->
                                totalDuration += (segment.duration * 1000f).toLong()
                                segments.add(segment.copy(uri = videoDirectory?.uri.toString() + "%2F" + segment.uri.substringAfterLast("%2F").substringAfterLast("/")))
                            }
                            applicationContext.contentResolver.openOutputStream(playlistFile.uri)!!.use {
                                PlaylistUtils.writeMediaPlaylist(playlist.copy(
                                    initSegmentUri = playlist.initSegmentUri?.let { uri -> videoDirectory?.uri.toString() + "%2F" + uri.substringAfterLast("%2F").substringAfterLast("/") },
                                    segments = segments
                                ), it)
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
                                thumbnail = segments.getOrNull(max(0,  (segments.size / 2) - 1))?.uri,
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

    fun saveVideos(list: List<String>) {
        viewModelScope.launch {
            val chatFiles = mutableMapOf<String, String>()
            list.filter { it.endsWith(".json") }.forEach {
                chatFiles[it.substringAfterLast("%2F").removeSuffix(".json").removeSuffix("_chat")] = it
            }
            list.filter { !it.endsWith(".json") }.forEach { url ->
                val existingVideo = offlineRepository.getVideoByUrl(url)
                if (existingVideo == null) {
                    val fileName = url.substringAfterLast("%2F").removeSuffix(".mp4").removeSuffix(".ts")
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
