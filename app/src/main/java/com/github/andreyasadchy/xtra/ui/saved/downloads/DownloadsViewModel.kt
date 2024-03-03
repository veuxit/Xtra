package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver.SCHEME_CONTENT
import android.content.Context
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okio.use
import java.io.File
import java.io.FileFilter
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject internal constructor(
    private val repository: OfflineRepository,
    private val fetchProvider: FetchProvider) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        repository.loadAllVideos()
    }.flow.cachedIn(viewModelScope)

    fun delete(context: Context, video: OfflineVideo) {
        repository.deleteVideo(context, video)
        GlobalScope.launch {
            val useWorkManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false)
            if (video.status == OfflineVideo.STATUS_DOWNLOADED || useWorkManager) {
                if (useWorkManager) {
                    WorkManager.getInstance(context).cancelUniqueWork(video.id.toString())
                }
                if (video.url.toUri().scheme == SCHEME_CONTENT) {
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
            } else {
                fetchProvider.get(video.id).deleteGroup(video.id)
            }
        }
    }
}