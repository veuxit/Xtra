package com.github.andreyasadchy.xtra.ui.saved.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.model.offline.Bookmark
import com.github.andreyasadchy.xtra.model.offline.VodBookmarkIgnoredUser
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.VodBookmarkIgnoredUsersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject internal constructor(
    private val repository: ApiRepository,
    private val bookmarksRepository: BookmarksRepository,
    playerRepository: PlayerRepository,
    private val vodBookmarkIgnoredUsersRepository: VodBookmarkIgnoredUsersRepository,
    private val okHttpClient: OkHttpClient) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val positions = playerRepository.loadVideoPositions()
    val ignoredUsers = vodBookmarkIgnoredUsersRepository.loadUsersFlow()
    private var updatedUsers = false
    private var updatedVideos = false

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        bookmarksRepository.loadBookmarksPagingSource()
    }.flow.cachedIn(viewModelScope)

    fun delete(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarksRepository.deleteBookmark(bookmark)
        }
    }

    fun vodIgnoreUser(userId: String) {
        viewModelScope.launch {
            if (vodBookmarkIgnoredUsersRepository.getUserById(userId) != null) {
                vodBookmarkIgnoredUsersRepository.deleteUser(VodBookmarkIgnoredUser(userId))
            } else {
                vodBookmarkIgnoredUsersRepository.saveUser(VodBookmarkIgnoredUser(userId))
            }
        }
    }

    fun updateUsers(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>) {
        if (!updatedUsers) {
            viewModelScope.launch {
                try {
                    val allIds = bookmarksRepository.loadBookmarks().mapNotNull { bookmark -> bookmark.userId.takeUnless { it == null || vodBookmarkIgnoredUsersRepository.loadUsers().contains(VodBookmarkIgnoredUser(it)) } }
                    if (allIds.isNotEmpty()) {
                        for (ids in allIds.chunked(100)) {
                            val users = repository.loadUserTypes(ids, helixHeaders, gqlHeaders)
                            if (users != null) {
                                for (user in users) {
                                    val bookmarks = user.channelId?.let { bookmarksRepository.getBookmarksByUserId(it) }
                                    if (bookmarks != null) {
                                        for (bookmark in bookmarks) {
                                            if (user.type != bookmark.userType || user.broadcasterType != bookmark.userBroadcasterType) {
                                                bookmarksRepository.updateBookmark(bookmark.apply {
                                                    userType = user.type
                                                    userBroadcasterType = user.broadcasterType
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }}
                    updatedUsers = true
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "users"
                    } else {
                        updatedUsers = true
                    }
                }
            }
        }
    }

    fun updateVideo(filesDir: String, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, videoId: String? = null) {
        viewModelScope.launch {
            try {
                val video = videoId?.let { repository.loadVideo(it, helixHeaders, gqlHeaders) }
                val bookmark = videoId?.let { bookmarksRepository.getBookmarkByVideoId(it) }
                if (video != null && bookmark != null) {
                    val downloadedThumbnail = video.id.takeIf { !it.isNullOrBlank() }?.let { id ->
                        video.thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "thumbnails").mkdir()
                            val path = filesDir + File.separator + "thumbnails" + File.separator + id
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
                                        }
                                    }
                                } catch (e: Exception) {

                                }
                            }
                            path
                        }
                    }
                    bookmarksRepository.updateBookmark(Bookmark(
                        videoId = bookmark.videoId,
                        userId = video.channelId ?: bookmark.userId,
                        userLogin = video.channelLogin ?: bookmark.userLogin,
                        userName = video.channelName ?: bookmark.userName,
                        userType = bookmark.userType,
                        userBroadcasterType = bookmark.userBroadcasterType,
                        userLogo = bookmark.userLogo,
                        gameId = video.gameId ?: bookmark.gameId,
                        gameSlug = video.gameSlug ?: bookmark.gameSlug,
                        gameName = video.gameName ?: bookmark.gameName,
                        title = video.title ?: bookmark.title,
                        createdAt = video.uploadDate ?: bookmark.createdAt,
                        thumbnail = downloadedThumbnail,
                        type = video.type ?: bookmark.type,
                        duration = video.duration ?: bookmark.duration,
                        animatedPreviewURL = video.animatedPreviewURL ?: bookmark.animatedPreviewURL
                    ))
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check" && integrity.value == null) {
                    integrity.value = "video"
                }
            }
        }
    }

    fun updateVideos(filesDir: String, helixHeaders: Map<String, String>) {
        if (!updatedVideos) {
            viewModelScope.launch {
                try {
                    val allIds = bookmarksRepository.loadBookmarks().mapNotNull { it.videoId }
                    if (allIds.isNotEmpty()) {
                        for (ids in allIds.chunked(100)) {
                            val videos = repository.loadVideos(ids, helixHeaders)
                            for (video in videos) {
                                val bookmark = video.id.takeIf { !it.isNullOrBlank() }?.let { bookmarksRepository.getBookmarkByVideoId(it) }
                                if (bookmark != null && (bookmark.userId != video.channelId ||
                                            bookmark.userLogin != video.channelLogin ||
                                            bookmark.userName != video.channelName ||
                                            bookmark.title != video.title ||
                                            bookmark.createdAt != video.uploadDate ||
                                            bookmark.type != video.type ||
                                            bookmark.duration != video.duration)) {
                                    val downloadedThumbnail = video.thumbnail.takeIf { !it.isNullOrBlank() }?.let {
                                        File(filesDir, "thumbnails").mkdir()
                                        val path = filesDir + File.separator + "thumbnails" + File.separator + video.id
                                        viewModelScope.launch(Dispatchers.IO) {
                                            try {
                                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                                    if (response.isSuccessful) {
                                                        File(path).sink().buffer().use { sink ->
                                                            sink.writeAll(response.body.source())
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {

                                            }
                                        }
                                        path
                                    }
                                    bookmarksRepository.updateBookmark(Bookmark(
                                        videoId = bookmark.videoId,
                                        userId = video.channelId ?: bookmark.userId,
                                        userLogin = video.channelLogin ?: bookmark.userLogin,
                                        userName = video.channelName ?: bookmark.userName,
                                        userType = bookmark.userType,
                                        userBroadcasterType = bookmark.userBroadcasterType,
                                        userLogo = bookmark.userLogo,
                                        gameId = bookmark.gameId,
                                        gameSlug = bookmark.gameSlug,
                                        gameName = bookmark.gameName,
                                        title = video.title ?: bookmark.title,
                                        createdAt = video.uploadDate ?: bookmark.createdAt,
                                        thumbnail = downloadedThumbnail,
                                        type = video.type ?: bookmark.type,
                                        duration = video.duration ?: bookmark.duration,
                                        animatedPreviewURL = video.animatedPreviewURL ?: bookmark.animatedPreviewURL
                                    ))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }
}