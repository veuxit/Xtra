package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.JsonReader
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.m3u8.PlaylistUtils
import com.github.andreyasadchy.xtra.util.m3u8.Segment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okio.appendingSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class DownloadsViewModel @Inject internal constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: OfflineRepository) : ViewModel() {

    var selectedVideo: OfflineVideo? = null
    private val videosInUse = mutableListOf<OfflineVideo>()
    private val currentDownloads = mutableListOf<Int>()
    private val liveDownloads = mutableListOf<String>()

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        repository.loadAllVideos()
    }.flow.cachedIn(viewModelScope)

    fun finishDownload(video: OfflineVideo) {
        video.chatUrl?.let { url ->
            val isShared = url.toUri().scheme == ContentResolver.SCHEME_CONTENT
            if (isShared) {
                applicationContext.contentResolver.openFileDescriptor(url.toUri(), "rw")!!.use {
                    FileOutputStream(it.fileDescriptor).use { output ->
                        output.channel.truncate(video.chatBytes)
                    }
                }
            } else {
                FileOutputStream(url).use { output ->
                    output.channel.truncate(video.chatBytes)
                }
            }
            if (isShared) {
                applicationContext.contentResolver.openOutputStream(url.toUri(), "wa")!!.bufferedWriter()
            } else {
                FileOutputStream(url, true).bufferedWriter()
            }.use { fileWriter ->
                fileWriter.write("}")
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateVideo(video.apply {
                status = OfflineVideo.STATUS_DOWNLOADED
            })
        }
    }

    fun checkLiveDownloadStatus(channelLogin: String) {
        if (!liveDownloads.contains(channelLogin)) {
            liveDownloads.add(channelLogin)
            viewModelScope.launch(Dispatchers.IO) {
                WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWorkFlow(channelLogin).collect { list ->
                    val work = list.lastOrNull()
                    when {
                        work == null || work.state.isFinished -> {
                            repository.getLiveDownload(channelLogin)?.let { video ->
                                if (video.status == OfflineVideo.STATUS_DOWNLOADING || video.status == OfflineVideo.STATUS_BLOCKED || video.status == OfflineVideo.STATUS_QUEUED || video.status == OfflineVideo.STATUS_QUEUED_WIFI || video.status == OfflineVideo.STATUS_WAITING_FOR_STREAM) {
                                    repository.updateVideo(video.apply {
                                        status = OfflineVideo.STATUS_PENDING
                                    })
                                }
                            }
                            cancel()
                        }
                        work.state == WorkInfo.State.ENQUEUED -> {
                            repository.getLiveDownload(channelLogin)?.let { video ->
                                repository.updateVideo(video.apply {
                                    status = if (work.constraints.requiredNetworkType == NetworkType.UNMETERED) {
                                        OfflineVideo.STATUS_QUEUED_WIFI
                                    } else {
                                        OfflineVideo.STATUS_QUEUED
                                    }
                                })
                            }
                        }
                        work.state == WorkInfo.State.BLOCKED -> {
                            repository.getLiveDownload(channelLogin)?.let { video ->
                                repository.updateVideo(video.apply {
                                    status = OfflineVideo.STATUS_BLOCKED
                                })
                            }
                        }
                    }
                }
            }.invokeOnCompletion {
                liveDownloads.remove(channelLogin)
            }
        }
    }

    fun checkDownloadStatus(videoId: Int) {
        if (!currentDownloads.contains(videoId)) {
            currentDownloads.add(videoId)
            viewModelScope.launch(Dispatchers.IO) {
                WorkManager.getInstance(applicationContext).getWorkInfosByTagFlow(videoId.toString()).collect { list ->
                    val work = list.lastOrNull()
                    when {
                        work == null || work.state.isFinished -> {
                            repository.getVideoById(videoId)?.let { video ->
                                if (video.status == OfflineVideo.STATUS_DOWNLOADING || video.status == OfflineVideo.STATUS_BLOCKED || video.status == OfflineVideo.STATUS_QUEUED || video.status == OfflineVideo.STATUS_QUEUED_WIFI) {
                                    repository.updateVideo(video.apply {
                                        status = OfflineVideo.STATUS_PENDING
                                    })
                                }
                            }
                            cancel()
                        }
                        work.state == WorkInfo.State.ENQUEUED -> {
                            repository.getVideoById(videoId)?.let { video ->
                                repository.updateVideo(video.apply {
                                    status = if (work.constraints.requiredNetworkType == NetworkType.UNMETERED) {
                                        OfflineVideo.STATUS_QUEUED_WIFI
                                    } else {
                                        OfflineVideo.STATUS_QUEUED
                                    }
                                })
                            }
                        }
                        work.state == WorkInfo.State.BLOCKED -> {
                            repository.getVideoById(videoId)?.let { video ->
                                repository.updateVideo(video.apply {
                                    status = OfflineVideo.STATUS_BLOCKED
                                })
                            }
                        }
                    }
                }
            }.invokeOnCompletion {
                currentDownloads.remove(videoId)
            }
        }
    }

    fun convertToFile(video: OfflineVideo) {
        val videoUrl = video.url
        if (!videosInUse.contains(video) && videoUrl != null) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_CONVERTING
                })
                if (videoUrl.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                    val oldPlaylistFile = DocumentFile.fromSingleUri(applicationContext, videoUrl.toUri())
                    if (oldPlaylistFile != null) {
                        val oldDirectory = DocumentFile.fromTreeUri(applicationContext, videoUrl.substringBefore("/document/").toUri())
                        val oldVideoDirectory = oldDirectory?.findFile(videoUrl.substringAfter("/document/").substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))
                        if (oldVideoDirectory != null) {
                            val oldPlaylist = applicationContext.contentResolver.openInputStream(oldPlaylistFile.uri)!!.use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            val videoFileName = "${video.videoId ?: ""}${video.quality ?: ""}${video.downloadDate}.${oldPlaylist.segments.first().uri.substringAfterLast(".")}"
                            val newVideoFile = oldDirectory.findFile(videoFileName) ?: oldDirectory.createFile("", videoFileName)
                            if (newVideoFile != null) {
                                val tracksToDelete = mutableListOf<String>()
                                oldPlaylist.segments.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                val playlists = oldVideoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != oldPlaylistFile.uri }
                                playlists.forEach { file ->
                                    val p = applicationContext.contentResolver.openInputStream(file.uri)!!.use {
                                        PlaylistUtils.parseMediaPlaylist(it)
                                    }
                                    p.segments.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                }
                                if (oldPlaylist.initSegmentUri != null && newVideoFile.length() == 0L) {
                                    val oldFile = oldVideoDirectory.findFile(oldPlaylist.initSegmentUri.substringAfterLast("%2F").substringAfterLast("/"))
                                    if (oldFile != null) {
                                        applicationContext.contentResolver.openOutputStream(newVideoFile.uri, "wa")!!.sink().buffer().use { sink ->
                                            sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                                        }
                                    }
                                }
                                repository.updateVideo(video.apply {
                                    maxProgress = tracksToDelete.count()
                                })
                                oldPlaylist.segments.forEach { track ->
                                    val oldFile = oldVideoDirectory.findFile(track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                    if (oldFile != null) {
                                        applicationContext.contentResolver.openOutputStream(newVideoFile.uri, "wa")!!.sink().buffer().use { sink ->
                                            sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                                        }
                                        if (tracksToDelete.contains(oldFile.name)) {
                                            oldFile.delete()
                                        }
                                    }
                                    repository.updateVideo(video.apply {
                                        progress += 1
                                    })
                                }
                                repository.updateVideo(video.apply {
                                    thumbnail.let {
                                        if (it == null || it == url || !File(it).exists()) {
                                            newVideoFile.uri.toString()
                                        }
                                    }
                                    url = newVideoFile.uri.toString()
                                })
                                if (playlists.isNotEmpty()) {
                                    oldPlaylistFile.delete()
                                } else {
                                    oldVideoDirectory.delete()
                                }
                            }
                        }
                    }
                } else {
                    val oldPlaylistFile = File(videoUrl)
                    if (oldPlaylistFile.exists()) {
                        val oldVideoDirectory = oldPlaylistFile.parentFile
                        val oldDirectory = oldVideoDirectory?.parentFile
                        if (oldVideoDirectory != null && oldDirectory != null) {
                            val oldPlaylist = FileInputStream(oldPlaylistFile).use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            val videoFileName = "${video.videoId ?: ""}${video.quality ?: ""}${video.downloadDate}.${oldPlaylist.segments.first().uri.substringAfterLast(".")}"
                            val newVideoFileUri = "${oldDirectory.path}${File.separator}$videoFileName"
                            val tracksToDelete = mutableListOf<String>()
                            oldPlaylist.segments.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            val playlists = oldVideoDirectory.listFiles(FileFilter { it.extension == "m3u8" && it != oldPlaylistFile })
                            playlists?.forEach { file ->
                                val p = PlaylistUtils.parseMediaPlaylist(file.inputStream())
                                p.segments.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            }
                            if (oldPlaylist.initSegmentUri != null && File(newVideoFileUri).length() == 0L) {
                                val oldFile = File(oldVideoDirectory.path + File.separator + oldPlaylist.initSegmentUri.substringAfterLast("%2F").substringAfterLast("/"))
                                if (oldFile.exists()) {
                                    File(newVideoFileUri).appendingSink().buffer().use { sink ->
                                        sink.writeAll(oldFile.source().buffer())
                                    }
                                }
                            }
                            repository.updateVideo(video.apply {
                                maxProgress = tracksToDelete.count()
                            })
                            oldPlaylist.segments.forEach { track ->
                                val oldFile = File(oldVideoDirectory.path + File.separator + track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                if (oldFile.exists()) {
                                    File(newVideoFileUri).appendingSink().buffer().use { sink ->
                                        sink.writeAll(oldFile.source().buffer())
                                    }
                                    if (tracksToDelete.contains(oldFile.name)) {
                                        oldFile.delete()
                                    }
                                }
                                repository.updateVideo(video.apply {
                                    progress += 1
                                })
                            }
                            repository.updateVideo(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        thumbnail = newVideoFileUri
                                    }
                                }
                                url = newVideoFileUri
                            })
                            if (playlists?.isNotEmpty() == true) {
                                oldPlaylistFile.delete()
                            } else {
                                oldVideoDirectory.deleteRecursively()
                            }
                        }
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateVideo(video.apply {
                        status = OfflineVideo.STATUS_DOWNLOADED
                    })
                }
            }
        }
    }

    fun moveToSharedStorage(newUri: Uri, video: OfflineVideo) {
        val videoUrl = video.url
        if (!videosInUse.contains(video) && videoUrl != null) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_MOVING
                })
                if (videoUrl.endsWith(".m3u8")) {
                    val oldPlaylistFile = File(videoUrl)
                    if (oldPlaylistFile.exists()) {
                        val oldVideoDirectory = oldPlaylistFile.parentFile
                        if (oldVideoDirectory != null) {
                            val newDirectory = DocumentFile.fromTreeUri(applicationContext, newUri)
                            val newVideoDirectory = newDirectory?.findFile(oldVideoDirectory.name) ?: newDirectory?.createDirectory(oldVideoDirectory.name)
                            val newPlaylistFile = newVideoDirectory?.createFile("", oldPlaylistFile.name)
                            if (newPlaylistFile != null) {
                                val oldPlaylist = FileInputStream(oldPlaylistFile).use {
                                    PlaylistUtils.parseMediaPlaylist(it)
                                }
                                val segments = ArrayList<Segment>()
                                oldPlaylist.segments.forEach { segment ->
                                    segments.add(segment.copy(uri = newVideoDirectory.uri.toString() + "%2F" + segment.uri.substringAfterLast("%2F").substringAfterLast("/")))
                                }
                                applicationContext.contentResolver.openOutputStream(newPlaylistFile.uri)!!.use {
                                    PlaylistUtils.writeMediaPlaylist(oldPlaylist.copy(
                                        initSegmentUri = oldPlaylist.initSegmentUri?.let { uri -> newVideoDirectory.uri.toString() + "%2F" + uri.substringAfterLast("%2F").substringAfterLast("/") },
                                        segments = segments
                                    ), it)
                                }
                                val tracksToDelete = mutableListOf<String>()
                                oldPlaylist.segments.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                val playlists = oldVideoDirectory.listFiles(FileFilter { it.extension == "m3u8" && it != oldPlaylistFile })
                                playlists?.forEach { file ->
                                    val p = PlaylistUtils.parseMediaPlaylist(file.inputStream())
                                    p.segments.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                }
                                if (oldPlaylist.initSegmentUri != null) {
                                    val oldFile = File(oldVideoDirectory.path + File.separator + oldPlaylist.initSegmentUri.substringAfterLast("%2F").substringAfterLast("/"))
                                    if (oldFile.exists()) {
                                        val newFile = newVideoDirectory.findFile(oldFile.name) ?: newVideoDirectory.createFile("", oldFile.name)
                                        if (newFile != null) {
                                            applicationContext.contentResolver.openOutputStream(newFile.uri)!!.sink().buffer().use { sink ->
                                                sink.writeAll(oldFile.source().buffer())
                                            }
                                        }
                                    }
                                }
                                repository.updateVideo(video.apply {
                                    maxProgress = tracksToDelete.count()
                                })
                                oldPlaylist.segments.forEach { track ->
                                    val oldFile = File(oldVideoDirectory.path + File.separator + track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                    if (oldFile.exists()) {
                                        val newFile = newVideoDirectory.findFile(oldFile.name) ?: newVideoDirectory.createFile("", oldFile.name)
                                        if (newFile != null) {
                                            applicationContext.contentResolver.openOutputStream(newFile.uri)!!.sink().buffer().use { sink ->
                                                sink.writeAll(oldFile.source().buffer())
                                            }
                                            if (tracksToDelete.contains(oldFile.name)) {
                                                oldFile.delete()
                                            }
                                        }
                                    }
                                    repository.updateVideo(video.apply {
                                        progress += 1
                                    })
                                }
                                val oldChatFile = video.chatUrl?.let { uri -> File(uri).takeIf { it.exists() } }
                                val newChatFile = oldChatFile?.let { newDirectory?.findFile(it.name) ?: newDirectory?.createFile("", it.name) }
                                if (newChatFile != null) {
                                    applicationContext.contentResolver.openOutputStream(newChatFile.uri)!!.sink().buffer().use { sink ->
                                        sink.writeAll(oldChatFile.source().buffer())
                                    }
                                }
                                repository.updateVideo(video.apply {
                                    thumbnail.let {
                                        if (it == null || it == url || !File(it).exists()) {
                                            oldPlaylist.segments.getOrNull(max(0,  (oldPlaylist.segments.size / 2) - 1))?.uri?.substringAfterLast("%2F")?.substringAfterLast("/")?.let { track ->
                                                newVideoDirectory.findFile(track)?.uri?.let { trackUri ->
                                                    thumbnail = trackUri.toString()
                                                }
                                            }
                                        }
                                    }
                                    url = newPlaylistFile.uri.toString()
                                    chatUrl = newChatFile?.uri?.toString()
                                })
                                if (playlists?.isNotEmpty() == true) {
                                    oldPlaylistFile.delete()
                                } else {
                                    oldVideoDirectory.deleteRecursively()
                                }
                                oldChatFile?.delete()
                            }
                        }
                    }
                } else {
                    val oldFile = File(videoUrl)
                    if (oldFile.exists()) {
                        val newDirectory = DocumentFile.fromTreeUri(applicationContext, newUri)
                        val newFile = newDirectory?.findFile(oldFile.name) ?: newDirectory?.createFile("", oldFile.name)
                        if (newFile != null) {
                            applicationContext.contentResolver.openOutputStream(newFile.uri)!!.sink().buffer().use { sink ->
                                sink.writeAll(oldFile.source().buffer())
                            }
                            val oldChatFile = video.chatUrl?.let { uri -> File(uri).takeIf { it.exists() } }
                            val newChatFile = oldChatFile?.let { newDirectory?.findFile(it.name) ?: newDirectory?.createFile("", it.name) }
                            if (newChatFile != null) {
                                applicationContext.contentResolver.openOutputStream(newChatFile.uri)!!.sink().buffer().use { sink ->
                                    sink.writeAll(oldChatFile.source().buffer())
                                }
                            }
                            repository.updateVideo(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        thumbnail = newFile.uri.toString()
                                    }
                                }
                                url = newFile.uri.toString()
                                chatUrl = newChatFile?.uri?.toString()
                            })
                            oldFile.delete()
                            oldChatFile?.delete()
                        }
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateVideo(video.apply {
                        status = OfflineVideo.STATUS_DOWNLOADED
                    })
                }
            }
        }
    }

    fun moveToAppStorage(path: String, video: OfflineVideo) {
        val videoUrl = video.url
        if (!videosInUse.contains(video) && videoUrl != null) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_MOVING
                })
                if (videoUrl.endsWith(".m3u8")) {
                    val oldPlaylistFile = DocumentFile.fromSingleUri(applicationContext, videoUrl.toUri())
                    if (oldPlaylistFile != null) {
                        val oldDirectory = DocumentFile.fromTreeUri(applicationContext, videoUrl.substringBefore("/document/").toUri())
                        val oldVideoDirectory = oldDirectory?.findFile(videoUrl.substringAfter("/document/").substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))
                        if (oldVideoDirectory != null) {
                            val newVideoDirectoryUri = "$path${File.separator}${oldVideoDirectory.name}${File.separator}"
                            File(newVideoDirectoryUri).mkdir()
                            val newPlaylistFileUri = "$newVideoDirectoryUri${oldPlaylistFile.name}"
                            val oldPlaylist = applicationContext.contentResolver.openInputStream(oldPlaylistFile.uri)!!.use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            val segments = ArrayList<Segment>()
                            oldPlaylist.segments.forEach { segment ->
                                segments.add(segment.copy(uri = newVideoDirectoryUri + segment.uri.substringAfterLast("%2F").substringAfterLast("/")))
                            }
                            FileOutputStream(newPlaylistFileUri).use {
                                PlaylistUtils.writeMediaPlaylist(oldPlaylist.copy(
                                    initSegmentUri = oldPlaylist.initSegmentUri?.let { uri -> newVideoDirectoryUri + uri.substringAfterLast("%2F").substringAfterLast("/") },
                                    segments = segments
                                ), it)
                            }
                            val tracksToDelete = mutableListOf<String>()
                            oldPlaylist.segments.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            val playlists = oldVideoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != oldPlaylistFile.uri }
                            playlists.forEach { file ->
                                val p = applicationContext.contentResolver.openInputStream(file.uri)!!.use {
                                    PlaylistUtils.parseMediaPlaylist(it)
                                }
                                p.segments.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            }
                            if (oldPlaylist.initSegmentUri != null) {
                                val oldFile = oldVideoDirectory.findFile(oldPlaylist.initSegmentUri.substringAfterLast("%2F").substringAfterLast("/"))
                                if (oldFile != null) {
                                    val newFileUri = "$newVideoDirectoryUri${oldFile.name}"
                                    File(newFileUri).sink().buffer().use { sink ->
                                        sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                                    }
                                }
                            }
                            repository.updateVideo(video.apply {
                                maxProgress = tracksToDelete.count()
                            })
                            oldPlaylist.segments.forEach { track ->
                                val oldFile = oldVideoDirectory.findFile(track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                if (oldFile != null) {
                                    val newFileUri = "$newVideoDirectoryUri${oldFile.name}"
                                    File(newFileUri).sink().buffer().use { sink ->
                                        sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                                    }
                                    if (tracksToDelete.contains(oldFile.name)) {
                                        oldFile.delete()
                                    }
                                }
                                repository.updateVideo(video.apply {
                                    progress += 1
                                })
                            }
                            val oldChatFile = video.chatUrl?.let { DocumentFile.fromSingleUri(applicationContext, it.toUri()) }
                            val newChatFileUri = oldChatFile?.let { "$path${File.separator}${it.name}" }
                            if (newChatFileUri != null) {
                                File(newChatFileUri).sink().buffer().use { sink ->
                                    sink.writeAll(applicationContext.contentResolver.openInputStream(oldChatFile.uri)!!.source().buffer())
                                }
                            }
                            repository.updateVideo(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        thumbnail = newVideoDirectoryUri + oldPlaylist.segments.getOrNull(max(0,  (oldPlaylist.segments.size / 2) - 1))?.uri?.substringAfterLast("%2F")?.substringAfterLast("/")
                                    }
                                }
                                url = newPlaylistFileUri
                                chatUrl = newChatFileUri
                            })
                            if (playlists.isNotEmpty()) {
                                oldPlaylistFile.delete()
                            } else {
                                oldVideoDirectory.delete()
                            }
                            oldChatFile?.delete()
                        }
                    }
                } else {
                    val oldFile = DocumentFile.fromSingleUri(applicationContext, videoUrl.toUri())
                    if (oldFile != null) {
                        val newFileUri = "$path${File.separator}${oldFile.name}"
                        File(newFileUri).sink().buffer().use { sink ->
                            sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                        }
                        val oldChatFile = video.chatUrl?.let { DocumentFile.fromSingleUri(applicationContext, it.toUri()) }
                        val newChatFileUri = oldChatFile?.let { "$path${File.separator}${it.name}" }
                        if (newChatFileUri != null) {
                            File(newChatFileUri).sink().buffer().use { sink ->
                                sink.writeAll(applicationContext.contentResolver.openInputStream(oldChatFile.uri)!!.source().buffer())
                            }
                        }
                        repository.updateVideo(video.apply {
                            thumbnail.let {
                                if (it == null || it == url || !File(it).exists()) {
                                    thumbnail = newFileUri
                                }
                            }
                            url = newFileUri
                            chatUrl = newChatFileUri
                        })
                        oldFile.delete()
                        oldChatFile?.delete()
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.updateVideo(video.apply {
                        status = OfflineVideo.STATUS_DOWNLOADED
                    })
                }
            }
        }
    }

    fun updateChatUrl(newUri: Uri, video: OfflineVideo) {
        if (!videosInUse.contains(video)) {
            viewModelScope.launch(Dispatchers.IO) {
                var id: String? = null
                var title: String? = null
                var uploadDate: Long? = null
                var channelId: String? = null
                var channelLogin: String? = null
                var channelName: String? = null
                var gameId: String? = null
                var gameSlug: String? = null
                var gameName: String? = null
                try {
                    applicationContext.contentResolver.openInputStream(newUri)?.bufferedReader()?.use { fileReader ->
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
                repository.updateVideo(video.apply {
                    if (!title.isNullOrBlank()) this.name = title
                    if (!channelId.isNullOrBlank()) this.channelId = channelId
                    if (!channelLogin.isNullOrBlank()) this.channelLogin = channelLogin
                    if (!channelName.isNullOrBlank()) this.channelName = channelName
                    if (!gameId.isNullOrBlank()) this.gameId = gameId
                    if (!gameSlug.isNullOrBlank()) this.gameSlug = gameSlug
                    if (!gameName.isNullOrBlank()) this.gameName = gameName
                    if (uploadDate != null) this.uploadDate = uploadDate
                    if (!id.isNullOrBlank()) this.videoId = id
                    chatUrl = newUri.toString()
                })
            }
        }
    }

    fun delete(video: OfflineVideo, keepFiles: Boolean) {
        val videoUrl = video.url
        if (!videosInUse.contains(video)) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    progress = 0
                    maxProgress = 100
                    status = OfflineVideo.STATUS_DELETING
                })
                if (video.live) {
                    video.channelLogin?.let { WorkManager.getInstance(applicationContext).cancelUniqueWork(it) }
                } else {
                    WorkManager.getInstance(applicationContext).cancelAllWorkByTag(video.id.toString())
                }
                if (videoUrl != null && !keepFiles) {
                    if (videoUrl.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                        if (videoUrl.endsWith(".m3u8")) {
                            val directory = DocumentFile.fromTreeUri(applicationContext, videoUrl.substringBefore("/document/").toUri()) ?: return@launch
                            val videoDirectory = directory.findFile(videoUrl.substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A")) ?: return@launch
                            val playlistFile = videoDirectory.findFile(videoUrl.substringAfterLast("%2F")) ?: return@launch
                            val playlists = videoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != playlistFile.uri }
                            val playlist = applicationContext.contentResolver.openInputStream(videoUrl.toUri())!!.use {
                                PlaylistUtils.parseMediaPlaylist(it)
                            }
                            val tracksToDelete = playlist.segments.toMutableSet()
                            playlists.forEach { file ->
                                val p = applicationContext.contentResolver.openInputStream(file.uri)!!.use {
                                    PlaylistUtils.parseMediaPlaylist(it)
                                }
                                tracksToDelete.removeAll(p.segments.toSet())
                            }
                            repository.updateVideo(video.apply {
                                maxProgress = tracksToDelete.count()
                            })
                            tracksToDelete.forEach {
                                videoDirectory.findFile(it.uri.substringAfterLast("%2F"))?.delete()
                                repository.updateVideo(video.apply {
                                    progress += 1
                                })
                            }
                            playlistFile.delete()
                            if (playlists.isEmpty()) {
                                videoDirectory.delete()
                            }
                        } else {
                            DocumentFile.fromSingleUri(applicationContext, videoUrl.toUri())?.delete()
                        }
                        video.chatUrl?.let { DocumentFile.fromSingleUri(applicationContext, it.toUri())?.delete() }
                    } else {
                        val playlistFile = File(videoUrl)
                        if (!playlistFile.exists()) {
                            return@launch
                        }
                        if (videoUrl.endsWith(".m3u8")) {
                            val directory = playlistFile.parentFile
                            if (directory != null) {
                                val playlists = directory.listFiles(FileFilter { it.extension == "m3u8" && it != playlistFile })
                                if (playlists != null) {
                                    val playlist = PlaylistUtils.parseMediaPlaylist(playlistFile.inputStream())
                                    val tracksToDelete = playlist.segments.toMutableSet()
                                    playlists.forEach {
                                        val p = PlaylistUtils.parseMediaPlaylist(it.inputStream())
                                        tracksToDelete.removeAll(p.segments.toSet())
                                    }
                                    repository.updateVideo(video.apply {
                                        maxProgress = tracksToDelete.count()
                                    })
                                    tracksToDelete.forEach {
                                        File(it.uri).delete()
                                        repository.updateVideo(video.apply {
                                            progress += 1
                                        })
                                    }
                                    playlistFile.delete()
                                    if (playlists.isEmpty()) {
                                        directory.deleteRecursively()
                                    }
                                }
                            }
                        } else {
                            playlistFile.delete()
                        }
                        video.chatUrl?.let { File(it).delete() }
                    }
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.deleteVideo(video)
                }
            }
        }
    }
}