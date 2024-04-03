package com.github.andreyasadchy.xtra.ui.saved

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.use
import javax.inject.Inject
import kotlin.math.max

@HiltViewModel
class SavedPagerViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository) : ViewModel() {

    fun saveFolders(context: Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val directory = DocumentFile.fromTreeUri(context, url.substringBefore("/document/").toUri())
            directory?.listFiles()?.filter { it.isDirectory }?.forEach { videoDirectory ->
                videoDirectory.listFiles().filter { it.name?.endsWith(".m3u8") == true }.forEach { playlistFile ->
                    val existingVideo = offlineRepository.getVideoByUrl(playlistFile.uri.toString())
                    if (existingVideo == null) {
                        val playlist = context.contentResolver.openInputStream(playlistFile.uri).use {
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
                        context.contentResolver.openOutputStream(playlistFile.uri).use {
                            PlaylistWriter(it, Format.EXT_M3U, Encoding.UTF_8).write(Playlist.Builder().withMediaPlaylist(playlist.buildUpon().withTracks(tracks).build()).build())
                        }
                        offlineRepository.saveVideo(OfflineVideo(
                            url = playlistFile.uri.toString(),
                            name = videoDirectory.name,
                            thumbnail = tracks.getOrNull(max(0,  (tracks.size / 2) - 1))?.uri,
                            duration = totalDuration,
                            progress = 100,
                            maxProgress = 100,
                            status = OfflineVideo.STATUS_DOWNLOADED
                        ))
                    }
                }
            }
        }
    }

    fun saveVideo(url: String) {
        viewModelScope.launch {
            val existingVideo = offlineRepository.getVideoByUrl(url)
            if (existingVideo == null) {
                offlineRepository.saveVideo(OfflineVideo(
                    url = url,
                    name = url.substringAfterLast("%2F").removeSuffix(".mp4"),
                    thumbnail = url,
                    progress = 100,
                    maxProgress = 100,
                    status = OfflineVideo.STATUS_DOWNLOADED
                ))
            }
        }
    }
}
