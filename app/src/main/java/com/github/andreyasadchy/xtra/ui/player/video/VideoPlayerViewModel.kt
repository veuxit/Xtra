package com.github.andreyasadchy.xtra.ui.player.video

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.VideoPosition
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val playerRepository: PlayerRepository,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    private val bookmarksRepository: BookmarksRepository) : PlayerViewModel(repository, localFollowsChannel) {

    var result = MutableLiveData<Uri>()

    val bookmarkItem = MutableLiveData<Bookmark>()
    val gamesList = MutableLiveData<List<Game>>()
    var shouldRetry = true

    fun load(gqlHeaders: Map<String, String>, videoId: String?, playerType: String?, supportedCodecs: String?, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                playerRepository.loadVideoPlaylistUrl(gqlHeaders, videoId, playerType, supportedCodecs, enableIntegrity)
            } catch (e: Exception) {
                if (e.message == "failed integrity check") {
                    _integrity.postValue(true)
                }
                null
            }.let { result.postValue(it) }
        }
    }

    fun savePosition(id: Long, position: Long) {
        if (loaded.value == true) {
            playerRepository.saveVideoPosition(VideoPosition(id, position))
        }
    }

    fun loadGamesList(gqlHeaders: Map<String, String>, videoId: String?) {
        if (!gamesList.isInitialized) {
            viewModelScope.launch {
                try {
                    val get = repository.loadVideoGames(gqlHeaders, videoId)
                    gamesList.postValue(get)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                }
            }
        }
    }

    fun checkBookmark(id: String) {
        viewModelScope.launch {
            bookmarkItem.postValue(bookmarksRepository.getBookmarkByVideoId(id))
        }
    }

    fun saveBookmark(context: Context, video: Video) {
        viewModelScope.launch {
            if (bookmarkItem.value != null) {
                bookmarksRepository.deleteBookmark(context, bookmarkItem.value!!)
            } else {
                val downloadedThumbnail = video.id.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(context, video.thumbnail, "thumbnails", it)
                }
                val downloadedLogo = video.channelId.takeIf { !it.isNullOrBlank() }?.let {
                    DownloadUtils.savePng(context, video.channelLogo, "profile_pics", it)
                }
                val userTypes = try {
                    video.channelId?.let { repository.loadUserTypes(listOf(it), context.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"), Account.get(context).helixToken, TwitchApiHelper.getGQLHeaders(context)) }?.firstOrNull()
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