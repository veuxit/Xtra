package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File

class FollowedChannelsDataSource(
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val okHttpClient: OkHttpClient,
    private val coroutineScope: CoroutineScope,
    private val filesDir: String,
    private val userId: String?,
    private val helixHeaders: Map<String, String>,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: List<String>,
    private val sort: String,
    private val order: String) : PagingSource<Int, User>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
        return try {
            val response = try {
                if (!offset.isNullOrBlank()) {
                    val list = when (api) {
                        C.HELIX -> helixLoad()
                        C.GQL -> if (nextPage) gqlQueryLoad() else listOf()
                        C.GQL_PERSISTED_QUERY -> if (nextPage) gqlLoad() else listOf()
                        else -> listOf()
                    }
                    list.filter { it.lastBroadcast == null || it.profileImageUrl == null }.mapNotNull { it.channelId }.chunked(100).forEach { ids ->
                        val response = gqlApi.loadQueryUsersLastBroadcast(
                            headers = gqlHeaders,
                            ids = ids
                        )
                        if (checkIntegrity) {
                            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                        }
                        response.data?.users?.forEach { user ->
                            list.find { it.channelId == user?.id }?.let { item ->
                                if (item.profileImageUrl == null) {
                                    item.profileImageUrl = user?.profileImageURL
                                }
                                item.lastBroadcast = user?.lastBroadcast?.startedAt?.toString()
                            }
                        }
                    }
                    list
                } else {
                    val list = mutableListOf<User>()
                    (localFollowsChannel.loadFollows().let { if (order == "asc") it.asReversed() else it }).forEach {
                        list.add(User(
                            channelId = it.userId,
                            channelLogin = it.userLogin,
                            channelName = it.userName,
                            profileImageUrl = it.channelLogo,
                            followLocal = true
                        ))
                    }
                    try {
                        when (apiPref.getOrNull(0)) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                            C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlQueryLoad() } else throw Exception()
                            C.GQL_PERSISTED_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_PERSISTED_QUERY; gqlLoad() } else throw Exception()
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") return LoadResult.Error(e)
                        try {
                            when (apiPref.getOrNull(1)) {
                                C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                                C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlQueryLoad() } else throw Exception()
                                C.GQL_PERSISTED_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_PERSISTED_QUERY; gqlLoad() } else throw Exception()
                                else -> throw Exception()
                            }
                        } catch (e: Exception) {
                            if (e.message == "failed integrity check") return LoadResult.Error(e)
                            try {
                                when (apiPref.getOrNull(2)) {
                                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                                    C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlQueryLoad() } else throw Exception()
                                    C.GQL_PERSISTED_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_PERSISTED_QUERY; gqlLoad() } else throw Exception()
                                    else -> throw Exception()
                                }
                            } catch (e: Exception) {
                                if (e.message == "failed integrity check") return LoadResult.Error(e)
                                listOf()
                            }
                        }
                    }.forEach { user ->
                        val item = list.find { it.channelId == user.channelId }
                        if (item == null) {
                            user.followAccount = true
                            list.add(user)
                        } else {
                            item.followAccount = true
                            item.followedAt = user.followedAt
                            item.lastBroadcast = user.lastBroadcast
                        }
                    }
                    list.filter {
                        it.lastBroadcast == null || it.profileImageUrl == null || it.profileImageUrl?.contains("image_manager_disk_cache") == true
                    }.mapNotNull { it.channelId }.chunked(100).forEach { ids ->
                        val response = gqlApi.loadQueryUsersLastBroadcast(
                            headers = gqlHeaders,
                            ids = ids
                        )
                        if (checkIntegrity) {
                            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
                        }
                        response.data?.users?.forEach { user ->
                            list.find { it.channelId == user?.id }?.let { item ->
                                if (item.followLocal) {
                                    if (item.profileImageUrl == null || item.profileImageUrl?.contains("image_manager_disk_cache") == true) {
                                        updateLocalUser(item.channelId, user?.profileImageURL)
                                    }
                                } else {
                                    if (item.profileImageUrl == null) {
                                        item.profileImageUrl = user?.profileImageURL
                                    }
                                }
                                item.lastBroadcast = user?.lastBroadcast?.startedAt?.toString()
                            }
                        }
                    }
                    if (order == "asc") {
                        when (sort) {
                            "created_at" -> list.sortedWith(compareBy(nullsLast()) { it.followedAt })
                            "login" -> list.sortedWith(compareBy(nullsLast()) { it.channelLogin })
                            else -> list.sortedWith(compareBy(nullsLast()) { it.lastBroadcast })
                        }
                    } else {
                        when (sort) {
                            "created_at" -> list.sortedWith(compareByDescending(nullsFirst()) { it.followedAt })
                            "login" -> list.sortedWith(compareByDescending(nullsFirst()) { it.channelLogin })
                            else -> list.sortedWith(compareByDescending(nullsFirst()) { it.lastBroadcast })
                        }
                    }
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                listOf()
            }
            LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = if (!offset.isNullOrBlank() && (api == C.HELIX || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun helixLoad(): List<User> {
        val response = helixApi.getUserFollows(
            headers = helixHeaders,
            userId = userId,
            limit = 100,
            offset = offset
        )
        val list = response.data.map {
            User(
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                followedAt = it.followedAt,
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(): List<User> {
        val response = gqlApi.loadQueryUserFollowedUsers(
            headers = gqlHeaders,
            first = 100,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user!!.follows!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                User(
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    followedAt = item.followedAt?.toString(),
                    lastBroadcast = it.lastBroadcast?.startedAt?.toString(),
                    profileImageUrl = it.profileImageURL,
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(): List<User> {
        val response = gqlApi.loadFollowedChannels(gqlHeaders, 100, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user.follows
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                User(
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    followedAt = it.self?.follower?.followedAt,
                    profileImageUrl = it.profileImageURL,
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private fun updateLocalUser(userId: String?, profileImageURL: String?) {
        if (!userId.isNullOrBlank()) {
            coroutineScope.launch {
                profileImageURL.takeIf { !it.isNullOrBlank() }?.let { TwitchApiHelper.getTemplateUrl(it, "profileimage") }?.let {
                    File(filesDir, "profile_pics").mkdir()
                    val path = filesDir + File.separator + "profile_pics" + File.separator + userId
                    coroutineScope.launch(Dispatchers.IO) {
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
                }?.let { downloadedLogo ->
                    localFollowsChannel.getFollowByUserId(userId)?.let {
                        localFollowsChannel.updateFollow(it.apply {
                            channelLogo = downloadedLogo
                        })
                    }
                    offlineRepository.getVideosByUserId(userId).forEach {
                        offlineRepository.updateVideo(it.apply {
                            channelLogo = downloadedLogo
                        })
                    }
                    bookmarksRepository.getBookmarksByUserId(userId).forEach {
                        bookmarksRepository.updateBookmark(it.apply {
                            userLogo = downloadedLogo
                        })
                    }
                }
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
