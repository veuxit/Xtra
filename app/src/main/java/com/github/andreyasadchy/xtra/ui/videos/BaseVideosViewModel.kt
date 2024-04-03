package com.github.andreyasadchy.xtra.ui.videos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.launch

abstract class BaseVideosViewModel(
    playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val repository: ApiRepository) : ViewModel() {

    val positions = playerRepository.loadVideoPositions()
    val bookmarks = bookmarksRepository.loadBookmarksLiveData()

    fun saveBookmark(context: Context, video: Video) {
        viewModelScope.launch {
            val item = video.id?.let { bookmarksRepository.getBookmarkByVideoId(it) }
            if (item != null) {
                bookmarksRepository.deleteBookmark(context, item)
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