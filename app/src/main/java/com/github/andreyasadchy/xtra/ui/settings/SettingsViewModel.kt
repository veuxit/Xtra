package com.github.andreyasadchy.xtra.ui.settings

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.iheartradio.m3u8.Encoding
import com.iheartradio.m3u8.Format
import com.iheartradio.m3u8.ParsingMode
import com.iheartradio.m3u8.PlaylistParser
import com.iheartradio.m3u8.PlaylistWriter
import com.iheartradio.m3u8.data.Playlist
import com.iheartradio.m3u8.data.TrackData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    private val offlineRepository: OfflineRepository) : ViewModel() {

    fun deletePositions() {
        viewModelScope.launch {
            playerRepository.deleteVideoPositions()
            offlineRepository.deletePositions()
        }
    }

    fun importDownloads(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ContextCompat.getExternalFilesDirs(context, ".downloads").forEach { storage ->
                storage?.absolutePath?.let { directory ->
                    File(directory).listFiles()?.let { files ->
                        files.forEach { file ->
                            if (file.isDirectory) {
                                file.listFiles()?.filter { it.name.endsWith(".m3u8") }?.forEach { playlistFile ->
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
                                        offlineRepository.saveVideo(OfflineVideo(
                                            url = playlistFile.path,
                                            name = file.name,
                                            thumbnail = file.path + File.separator + tracks.getOrNull(max(0,  (tracks.size / 2) - 1))?.uri,
                                            duration = totalDuration,
                                            progress = 100,
                                            maxProgress = 100,
                                            status = OfflineVideo.STATUS_DOWNLOADED
                                        ))
                                    }
                                }
                            } else if (file.isFile && file.name.endsWith(".mp4")) {
                                val existingVideo = offlineRepository.getVideoByUrl(file.path)
                                if (existingVideo == null) {
                                    offlineRepository.saveVideo(OfflineVideo(
                                        url = file.path,
                                        name = file.name.removeSuffix(".mp4"),
                                        thumbnail = file.path,
                                        progress = 100,
                                        maxProgress = 100,
                                        status = OfflineVideo.STATUS_DOWNLOADED
                                    ))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}