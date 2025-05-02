package com.github.andreyasadchy.xtra.ui.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.Bookmark
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.chromium.net.CronetEngine
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService

abstract class BaseVideosViewModel(
    playerRepository: PlayerRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    val positions = playerRepository.loadVideoPositions()
    val bookmarks = bookmarksRepository.loadBookmarksFlow()

    fun saveBookmark(filesDir: String, video: Video, useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
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
                                if (useCronet && cronetEngine != null) {
                                    val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                    cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                    val response = request.future.get()
                                    if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                        FileOutputStream(path).use {
                                            it.write(response.responseBody as ByteArray)
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
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
                                if (useCronet && cronetEngine != null) {
                                    val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                    cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                    val response = request.future.get()
                                    if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                        FileOutputStream(path).use {
                                            it.write(response.responseBody as ByteArray)
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                }
                val userTypes = video.channelId?.let {
                    try {
                        val response = graphQLRepository.loadQueryUsersType(useCronet, gqlHeaders, listOf(it))
                        response.data!!.users?.firstOrNull()?.let {
                            User(
                                channelId = it.id,
                                broadcasterType = when {
                                    it.roles?.isPartner == true -> "partner"
                                    it.roles?.isAffiliate == true -> "affiliate"
                                    else -> null
                                },
                                type = when {
                                    it.roles?.isStaff == true -> "staff"
                                    else -> null
                                }
                            )
                        }
                    } catch (e: Exception) {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                            try {
                                helixRepository.getUsers(
                                    useCronet = useCronet,
                                    headers = helixHeaders,
                                    ids = listOf(it)
                                ).data.firstOrNull()?.let {
                                    User(
                                        channelId = it.channelId,
                                        channelLogin = it.channelLogin,
                                        channelName = it.channelName,
                                        type = it.type,
                                        broadcasterType = it.broadcasterType,
                                        profileImageUrl = it.profileImageUrl,
                                        createdAt = it.createdAt,
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }
                }
                bookmarksRepository.saveBookmark(
                    Bookmark(
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
                    )
                )
            }
        }
    }
}