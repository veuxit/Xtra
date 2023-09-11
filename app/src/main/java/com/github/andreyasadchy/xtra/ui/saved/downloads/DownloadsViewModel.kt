package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.work.WorkManager
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.FetchProvider
import com.github.andreyasadchy.xtra.util.prefs
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.PlaylistParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileFilter
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject internal constructor(
    private val repository: OfflineRepository,
    private val fetchProvider: FetchProvider) : ViewModel() {

    val list = repository.loadAllVideos()

    fun delete(context: Context, video: OfflineVideo) {
        repository.deleteVideo(context, video)
        GlobalScope.launch {
            val useWorkManager = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false)
            if (video.status == OfflineVideo.STATUS_DOWNLOADED || useWorkManager) {
                if (useWorkManager) {
                    WorkManager.getInstance(context).cancelUniqueWork(video.id.toString())
                }
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
                        val playlist = PlaylistParser(playlistFile.inputStream(), Format.EXT_M3U, Encoding.UTF_8).parse()
                        val tracksToDelete = playlist.mediaPlaylist.tracks.toMutableSet()
                        playlists.forEach {
                            val p = PlaylistParser(it.inputStream(), Format.EXT_M3U, Encoding.UTF_8).parse()
                            tracksToDelete.removeAll(p.mediaPlaylist.tracks.toSet())
                        }
                        playlistFile.delete()
                        tracksToDelete.forEach { File(it.uri).delete() }
                    }
                } else {
                    playlistFile.delete()
                }
            } else {
                fetchProvider.get(video.id).deleteGroup(video.id)
            }
        }
    }
}