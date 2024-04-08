package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.work.WorkManager
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FetchProvider
import com.github.andreyasadchy.xtra.util.prefs
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
import okio.appendingSink
import okio.buffer
import okio.sink
import okio.source
import okio.use
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class DownloadsViewModel @Inject internal constructor(
    @ApplicationContext private val applicationContext: Context,
    private val repository: OfflineRepository,
    private val fetchProvider: FetchProvider) : ViewModel() {

    var selectedVideo: OfflineVideo? = null
    private val videosInUse = mutableListOf<OfflineVideo>()

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        repository.loadAllVideos()
    }.flow.cachedIn(viewModelScope)

    fun convertToFile(video: OfflineVideo) {
        if (!videosInUse.contains(video)) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    status = OfflineVideo.STATUS_CONVERTING
                })
                if (video.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                    val oldPlaylistFile = DocumentFile.fromSingleUri(applicationContext, video.url.toUri())
                    if (oldPlaylistFile != null) {
                        val oldDirectory = DocumentFile.fromTreeUri(applicationContext, video.url.substringBefore("/document/").toUri())
                        val oldVideoDirectory = oldDirectory?.findFile(video.url.substringAfter("/document/").substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))
                        if (oldVideoDirectory != null) {
                            val oldPlaylist = applicationContext.contentResolver.openInputStream(oldPlaylistFile.uri).use {
                                PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                            }
                            val videoFileName = "${video.videoId ?: ""}${video.quality ?: ""}${video.downloadDate}.${oldPlaylist.tracks.first().uri.substringAfterLast(".")}"
                            val newVideoFile = oldDirectory.findFile(videoFileName) ?: oldDirectory.createFile("", videoFileName)
                            if (newVideoFile != null) {
                                val tracksToDelete = mutableListOf<String>()
                                oldPlaylist.tracks.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                val playlists = oldVideoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != oldPlaylistFile.uri }
                                playlists.forEach { file ->
                                    val p = applicationContext.contentResolver.openInputStream(file.uri).use {
                                        PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                    }
                                    p.mediaPlaylist.tracks.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                }
                                oldPlaylist.tracks.forEach { track ->
                                    val oldFile = oldVideoDirectory.findFile(track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                    if (oldFile != null) {
                                        applicationContext.contentResolver.openOutputStream(newVideoFile.uri, "wa")!!.sink().buffer().use { sink ->
                                            sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                                        }
                                        if (tracksToDelete.contains(oldFile.name)) {
                                            oldFile.delete()
                                        }
                                    }
                                }
                                repository.updateVideo(video.apply {
                                    thumbnail.let {
                                        if (it == null || it == url || !File(it).exists()) {
                                            newVideoFile.uri.toString()
                                        }
                                    }
                                    url = newVideoFile.uri.toString()
                                    vod = false
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
                    val oldPlaylistFile = File(video.url)
                    if (oldPlaylistFile.exists()) {
                        val oldVideoDirectory = oldPlaylistFile.parentFile
                        val oldDirectory = oldVideoDirectory?.parentFile
                        if (oldVideoDirectory != null && oldDirectory != null) {
                            val oldPlaylist = FileInputStream(oldPlaylistFile).use {
                                PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                            }
                            val videoFileName = "${video.videoId ?: ""}${video.quality ?: ""}${video.downloadDate}.${oldPlaylist.tracks.first().uri.substringAfterLast(".")}"
                            val newVideoFileUri = "${oldDirectory.path}${File.separator}$videoFileName"
                            val tracksToDelete = mutableListOf<String>()
                            oldPlaylist.tracks.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            val playlists = oldVideoDirectory.listFiles(FileFilter { it.extension == "m3u8" && it != oldPlaylistFile })
                            playlists?.forEach { file ->
                                val p = PlaylistParser(file.inputStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                p.mediaPlaylist.tracks.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            }
                            oldPlaylist.tracks.forEach { track ->
                                val oldFile = File(oldVideoDirectory.path + File.separator + track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                if (oldFile.exists()) {
                                    File(newVideoFileUri).appendingSink().buffer().use { sink ->
                                        sink.writeAll(oldFile.source().buffer())
                                    }
                                    if (tracksToDelete.contains(oldFile.name)) {
                                        oldFile.delete()
                                    }
                                }
                            }
                            repository.updateVideo(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        thumbnail = newVideoFileUri
                                    }
                                }
                                url = newVideoFileUri
                                vod = false
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
        if (!videosInUse.contains(video)) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    status = OfflineVideo.STATUS_MOVING
                })
                if (video.vod) {
                    val oldPlaylistFile = File(video.url)
                    if (oldPlaylistFile.exists()) {
                        val oldVideoDirectory = oldPlaylistFile.parentFile
                        if (oldVideoDirectory != null) {
                            val newDirectory = DocumentFile.fromTreeUri(applicationContext, newUri)
                            val newVideoDirectory = newDirectory?.findFile(oldVideoDirectory.name) ?: newDirectory?.createDirectory(oldVideoDirectory.name)
                            val newPlaylistFile = newVideoDirectory?.createFile("", "${System.currentTimeMillis()}.m3u8")
                            if (newPlaylistFile != null) {
                                val oldPlaylist = FileInputStream(oldPlaylistFile).use {
                                    PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                                }
                                val tracks = ArrayList<TrackData>()
                                oldPlaylist.tracks.forEach { track ->
                                    tracks.add(
                                        track.buildUpon()
                                            .withUri(newVideoDirectory.uri.toString() + "%2F" + track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                            .build()
                                    )
                                }
                                applicationContext.contentResolver.openOutputStream(newPlaylistFile.uri).use {
                                    PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(oldPlaylist.buildUpon().withTracks(tracks).build()).build())
                                }
                                val tracksToDelete = mutableListOf<String>()
                                oldPlaylist.tracks.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                val playlists = oldVideoDirectory.listFiles(FileFilter { it.extension == "m3u8" && it != oldPlaylistFile })
                                playlists?.forEach { file ->
                                    val p = PlaylistParser(file.inputStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                    p.mediaPlaylist.tracks.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                                }
                                oldPlaylist.tracks.forEach { track ->
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
                                }
                                repository.updateVideo(video.apply {
                                    thumbnail.let {
                                        if (it == null || it == url || !File(it).exists()) {
                                            oldPlaylist.tracks.getOrNull(max(0,  (oldPlaylist.tracks.size / 2) - 1))?.uri?.substringAfterLast("%2F")?.substringAfterLast("/")?.let { track ->
                                                newVideoDirectory.findFile(track)?.uri?.let { trackUri ->
                                                    thumbnail = trackUri.toString()
                                                }
                                            }
                                        }
                                    }
                                    url = newPlaylistFile.uri.toString()
                                })
                                if (playlists?.isNotEmpty() == true) {
                                    oldPlaylistFile.delete()
                                } else {
                                    oldVideoDirectory.deleteRecursively()
                                }
                            }
                        }
                    }
                } else {
                    val oldFile = File(video.url)
                    if (oldFile.exists()) {
                        applicationContext.contentResolver.openOutputStream(newUri)!!.sink().buffer().use { sink ->
                            sink.writeAll(oldFile.source().buffer())
                        }
                        repository.updateVideo(video.apply {
                            thumbnail.let {
                                if (it == null || it == url || !File(it).exists()) {
                                    thumbnail = newUri.toString()
                                }
                            }
                            url = newUri.toString()
                        })
                        oldFile.delete()
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
        if (!videosInUse.contains(video)) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    status = OfflineVideo.STATUS_MOVING
                })
                if (video.vod) {
                    val oldPlaylistFile = DocumentFile.fromSingleUri(applicationContext, video.url.toUri())
                    if (oldPlaylistFile != null) {
                        val oldDirectory = DocumentFile.fromTreeUri(applicationContext, video.url.substringBefore("/document/").toUri())
                        val oldVideoDirectory = oldDirectory?.findFile(video.url.substringAfter("/document/").substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A"))
                        if (oldVideoDirectory != null) {
                            val newVideoDirectoryUri = "$path${File.separator}${oldVideoDirectory.name}${File.separator}"
                            File(newVideoDirectoryUri).mkdir()
                            val newPlaylistFileUri = "$newVideoDirectoryUri${System.currentTimeMillis()}.m3u8"
                            val oldPlaylist = applicationContext.contentResolver.openInputStream(oldPlaylistFile.uri).use {
                                PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse().mediaPlaylist
                            }
                            val tracks = ArrayList<TrackData>()
                            oldPlaylist.tracks.forEach { track ->
                                tracks.add(
                                    track.buildUpon()
                                        .withUri(newVideoDirectoryUri + track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                                        .build()
                                )
                            }
                            FileOutputStream(newPlaylistFileUri).use {
                                PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(oldPlaylist.buildUpon().withTracks(tracks).build()).build())
                            }
                            val tracksToDelete = mutableListOf<String>()
                            oldPlaylist.tracks.forEach { tracksToDelete.add(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            val playlists = oldVideoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != oldPlaylistFile.uri }
                            playlists.forEach { file ->
                                val p = applicationContext.contentResolver.openInputStream(file.uri).use {
                                    PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                }
                                p.mediaPlaylist.tracks.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                            }
                            oldPlaylist.tracks.forEach { track ->
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
                            }
                            repository.updateVideo(video.apply {
                                thumbnail.let {
                                    if (it == null || it == url || !File(it).exists()) {
                                        thumbnail = newVideoDirectoryUri + oldPlaylist.tracks.getOrNull(max(0,  (oldPlaylist.tracks.size / 2) - 1))?.uri?.substringAfterLast("%2F")?.substringAfterLast("/")
                                    }
                                }
                                url = newPlaylistFileUri
                            })
                            if (playlists.isNotEmpty()) {
                                oldPlaylistFile.delete()
                            } else {
                                oldVideoDirectory.delete()
                            }
                        }
                    }
                } else {
                    val oldFile = DocumentFile.fromSingleUri(applicationContext, video.url.toUri())
                    if (oldFile != null) {
                        val newFileUri = "$path${File.separator}${oldFile.name}"
                        File(newFileUri).sink().buffer().use { sink ->
                            sink.writeAll(applicationContext.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
                        }
                        repository.updateVideo(video.apply {
                            thumbnail.let {
                                if (it == null || it == url || !File(it).exists()) {
                                    thumbnail = newFileUri
                                }
                            }
                            url = newFileUri
                        })
                        oldFile.delete()
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

    fun delete(video: OfflineVideo, keepFiles: Boolean) {
        if (!videosInUse.contains(video)) {
            videosInUse.add(video)
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateVideo(video.apply {
                    status = OfflineVideo.STATUS_DELETING
                })
                val useWorkManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || applicationContext.prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false)
                if (video.status == OfflineVideo.STATUS_DOWNLOADED || video.status == OfflineVideo.STATUS_MOVING || video.status == OfflineVideo.STATUS_DELETING || video.status == OfflineVideo.STATUS_CONVERTING || useWorkManager) {
                    if (useWorkManager) {
                        WorkManager.getInstance(applicationContext).cancelUniqueWork(video.id.toString())
                    }
                    if (!keepFiles) {
                        if (video.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                            if (video.vod) {
                                val directory = DocumentFile.fromTreeUri(applicationContext, video.url.substringBefore("/document/").toUri()) ?: return@launch
                                val videoDirectory = directory.findFile(video.url.substringBeforeLast("%2F").substringAfterLast("%2F").substringAfterLast("%3A")) ?: return@launch
                                val playlistFile = videoDirectory.findFile(video.url.substringAfterLast("%2F")) ?: return@launch
                                val playlists = videoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != playlistFile.uri }
                                if (playlists.isEmpty()) {
                                    videoDirectory.delete()
                                } else {
                                    val playlist = applicationContext.contentResolver.openInputStream(video.url.toUri()).use {
                                        PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                    }
                                    val tracksToDelete = playlist.mediaPlaylist.tracks.toMutableSet()
                                    playlists.forEach { file ->
                                        val p = applicationContext.contentResolver.openInputStream(file.uri).use {
                                            PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                        }
                                        tracksToDelete.removeAll(p.mediaPlaylist.tracks.toSet())
                                    }
                                    tracksToDelete.forEach { videoDirectory.findFile(it.uri.substringAfterLast("%2F"))?.delete() }
                                    playlistFile.delete()
                                }
                            } else {
                                DocumentFile.fromSingleUri(applicationContext, video.url.toUri())?.delete()
                            }
                        } else {
                            val playlistFile = File(video.url)
                            if (!playlistFile.exists()) {
                                return@launch
                            }
                            if (video.vod) {
                                val directory = playlistFile.parentFile
                                if (directory != null) {
                                    val playlists = directory.listFiles(FileFilter { it.extension == "m3u8" && it != playlistFile })
                                    if (playlists != null) {
                                        if (playlists.isEmpty()) {
                                            directory.deleteRecursively()
                                        } else {
                                            val playlist = PlaylistParser(playlistFile.inputStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                            val tracksToDelete = playlist.mediaPlaylist.tracks.toMutableSet()
                                            playlists.forEach {
                                                val p = PlaylistParser(it.inputStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                                tracksToDelete.removeAll(p.mediaPlaylist.tracks.toSet())
                                            }
                                            tracksToDelete.forEach { File(it.uri).delete() }
                                            playlistFile.delete()
                                        }
                                    }
                                }
                            } else {
                                playlistFile.delete()
                            }
                        }
                    }
                } else {
                    fetchProvider.get(video.id).deleteGroup(video.id)
                }
            }.invokeOnCompletion {
                videosInUse.remove(video)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.deleteVideo(applicationContext, video)
                }
            }
        }
    }
}