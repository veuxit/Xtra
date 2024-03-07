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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    private val repository: OfflineRepository,
    private val fetchProvider: FetchProvider) : ViewModel() {

    var selectedVideo: OfflineVideo? = null

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        repository.loadAllVideos()
    }.flow.cachedIn(viewModelScope)

    fun moveToSharedStorage(context: Context, newUri: Uri, video: OfflineVideo) {
        if (video.vod) {
            val oldPlaylistFile = File(video.url)
            if (oldPlaylistFile.exists()) {
                val oldVideoDirectory = oldPlaylistFile.parentFile
                if (oldVideoDirectory != null) {
                    val newDirectory = DocumentFile.fromTreeUri(context, newUri)
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
                        context.contentResolver.openOutputStream(newPlaylistFile.uri).use {
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
                                    context.contentResolver.openOutputStream(newFile.uri)!!.sink().buffer().use { sink ->
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
                context.contentResolver.openOutputStream(newUri)!!.sink().buffer().use { sink ->
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
    }

    fun moveToAppStorage(context: Context, path: String, video: OfflineVideo) {
        if (video.vod) {
            val oldPlaylistFile = DocumentFile.fromSingleUri(context, video.url.toUri())
            if (oldPlaylistFile != null) {
                val oldDirectory = DocumentFile.fromTreeUri(context, video.url.substringBefore("/document/").toUri())
                val oldVideoDirectory = oldDirectory?.findFile(video.url.substringAfter("/document/").substringBeforeLast("%2F").substringAfterLast("%2F"))
                if (oldVideoDirectory != null) {
                    val newVideoDirectoryUri = "$path${File.separator}${oldVideoDirectory.name}${File.separator}"
                    File(newVideoDirectoryUri).mkdir()
                    val newPlaylistFileUri = "$newVideoDirectoryUri${System.currentTimeMillis()}.m3u8"
                    val oldPlaylist = context.contentResolver.openInputStream(oldPlaylistFile.uri).use {
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
                        val p = context.contentResolver.openInputStream(file.uri).use {
                            PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                        }
                        p.mediaPlaylist.tracks.forEach { tracksToDelete.remove(it.uri.substringAfterLast("%2F").substringAfterLast("/")) }
                    }
                    oldPlaylist.tracks.forEach { track ->
                        val oldFile = oldVideoDirectory.findFile(track.uri.substringAfterLast("%2F").substringAfterLast("/"))
                        if (oldFile != null) {
                            val newFileUri = "$newVideoDirectoryUri${oldFile.name}"
                            File(newFileUri).sink().buffer().use { sink ->
                                sink.writeAll(context.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
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
            val oldFile = DocumentFile.fromSingleUri(context, video.url.toUri())
            if (oldFile != null) {
                val newFileUri = "$path${File.separator}${oldFile.name}"
                File(newFileUri).sink().buffer().use { sink ->
                    sink.writeAll(context.contentResolver.openInputStream(oldFile.uri)!!.source().buffer())
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
    }

    fun delete(context: Context, video: OfflineVideo, keepFiles: Boolean) {
        repository.deleteVideo(context, video)
        GlobalScope.launch {
            val useWorkManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false)
            if (video.status == OfflineVideo.STATUS_DOWNLOADED || useWorkManager) {
                if (useWorkManager) {
                    WorkManager.getInstance(context).cancelUniqueWork(video.id.toString())
                }
                if (!keepFiles) {
                    if (video.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                        if (video.vod) {
                            val directory = DocumentFile.fromTreeUri(context, video.url.substringBefore("/document/").toUri()) ?: return@launch
                            val videoDirectory = directory.findFile(video.url.substringBeforeLast("%2F").substringAfterLast("%2F")) ?: return@launch
                            val playlistFile = videoDirectory.findFile(video.url.substringAfterLast("%2F")) ?: return@launch
                            val playlists = videoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true && it.uri != playlistFile.uri }
                            if (playlists.isEmpty()) {
                                videoDirectory.delete()
                            } else {
                                val playlist = context.contentResolver.openInputStream(video.url.toUri()).use {
                                    PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                }
                                val tracksToDelete = playlist.mediaPlaylist.tracks.toMutableSet()
                                playlists.forEach { file ->
                                    val p = context.contentResolver.openInputStream(file.uri).use {
                                        PlaylistParser(it, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                    }
                                    tracksToDelete.removeAll(p.mediaPlaylist.tracks.toSet())
                                }
                                playlistFile.delete()
                                tracksToDelete.forEach { videoDirectory.findFile(it.uri.substringAfterLast("%2F"))?.delete() }
                            }
                        } else {
                            DocumentFile.fromSingleUri(context, video.url.toUri())?.delete()
                        }
                    } else {
                        val playlistFile = File(video.url)
                        if (!playlistFile.exists()) {
                            return@launch
                        }
                        if (video.vod) {
                            val directory = playlistFile.parentFile
                            val playlists = directory.listFiles(FileFilter { it.extension == "m3u8" && it != playlistFile })
                            if (playlists.isEmpty()) {
                                directory.deleteRecursively()
                            } else {
                                val playlist = PlaylistParser(playlistFile.inputStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                val tracksToDelete = playlist.mediaPlaylist.tracks.toMutableSet()
                                playlists.forEach {
                                    val p = PlaylistParser(it.inputStream(), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT).parse()
                                    tracksToDelete.removeAll(p.mediaPlaylist.tracks.toSet())
                                }
                                playlistFile.delete()
                                tracksToDelete.forEach { File(it.uri).delete() }
                            }
                        } else {
                            playlistFile.delete()
                        }
                    }
                }
            } else {
                fetchProvider.get(video.id).deleteGroup(video.id)
            }
        }
    }
}