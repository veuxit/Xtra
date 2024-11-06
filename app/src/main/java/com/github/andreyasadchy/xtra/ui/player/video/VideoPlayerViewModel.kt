package com.github.andreyasadchy.xtra.ui.player.video

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    private val okHttpClient: OkHttpClient,
    private val playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository) : PlayerViewModel(repository, localFollowsChannel, okHttpClient) {

    val result = MutableStateFlow<Uri?>(null)

    val savedPosition = MutableSharedFlow<VideoPosition?>()
    val isBookmarked = MutableStateFlow<Boolean?>(null)
    val gamesList = MutableStateFlow<List<Game>?>(null)
    var shouldRetry = true

    fun load(gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean) {
        if (result.value == null) {
            viewModelScope.launch {
                try {
                    result.value = playerRepository.loadVideoPlaylistUrl(gqlHeaders, videoId, playerType, supportedCodecs, enableIntegrity)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun getPosition(id: Long) {
        viewModelScope.launch {
            savedPosition.emit(playerRepository.getVideoPosition(id))
        }
    }

    fun savePosition(id: Long, position: Long) {
        if (loaded.value) {
            playerRepository.saveVideoPosition(VideoPosition(id, position))
        }
    }

    fun loadGamesList(gqlHeaders: Map<String, String>, videoId: String?) {
        if (gamesList.value == null) {
            viewModelScope.launch {
                try {
                    gamesList.value = repository.loadVideoGames(gqlHeaders, videoId)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }

    fun checkBookmark(id: String) {
        viewModelScope.launch {
            isBookmarked.value = bookmarksRepository.getBookmarkByVideoId(id) != null
        }
    }

    fun saveBookmark(filesDir: String, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, video: Video) {
        viewModelScope.launch {
            val item = video.id?.let { bookmarksRepository.getBookmarkByVideoId(it) }
            if (item != null) {
                bookmarksRepository.deleteBookmark(item)
            } else {
                val downloadedThumbnail = video.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                    video.thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "thumbnails").mkdir()
                        val path = filesDir + File.separator + "thumbnails" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body()!!.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val downloadedLogo = video.channelId.takeIf { !it.isNullOrBlank() }?.let { id ->
                    video.channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + id
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body()!!.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val userTypes = try {
                    video.channelId?.let { repository.loadUserTypes(listOf(it), helixHeaders, gqlHeaders) }?.firstOrNull()
                } catch (e: Exception) {
                    null
                }
                bookmarksRepository.saveBookmark(Bookmark(
                    videoId = video.id,
                    userId = video.channelId,
                    userLogin = video.channelLogin,
                    userName = video.channelName,
                    userType = userTypes?.type,
                    userBroadcasterType = userTypes?.broadcasterType,
                    userLogo = downloadedLogo,
                    gameId = video.gameId,
                    gameSlug = video.gameSlug,
                    gameName = video.gameName,
                    title = video.title,
                    createdAt = video.uploadDate,
                    thumbnail = downloadedThumbnail,
                    type = video.type,
                    duration = video.duration,
                    animatedPreviewURL = video.animatedPreviewURL
                ))
            }
        }
    }
}