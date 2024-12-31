package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.github.andreyasadchy.xtra.UserFollowedStreamsQuery
import com.github.andreyasadchy.xtra.UsersStreamQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C

class FollowedStreamsDataSource(
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val userId: String?,
    private val helixHeaders: Map<String, String>,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: List<String>,
) : PagingSource<Int, Stream>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return try {
            val response = try {
                if (!offset.isNullOrBlank()) {
                    when (api) {
                        C.HELIX -> helixLoad()
                        C.GQL -> if (nextPage) gqlQueryLoad() else listOf()
                        C.GQL_PERSISTED_QUERY -> gqlLoad()
                        else -> listOf()
                    }
                } else {
                    val list = mutableListOf<Stream>()
                    localFollowsChannel.loadFollows().mapNotNull { it.userId }.let {
                        if (it.isNotEmpty()) {
                            try {
                                gqlQueryLocal(it)
                            } catch (e: Exception) {
                                if (e.message == "failed integrity check") return LoadResult.Error(e)
                                try {
                                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLocal(it) else throw Exception()
                                } catch (e: Exception) {
                                    listOf()
                                }
                            }
                        } else listOf()
                    }.let { list.addAll(it) }
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
                    }.forEach { stream ->
                        val item = list.find { it.channelId == stream.channelId }
                        if (item == null) {
                            list.add(stream)
                        }
                    }
                    list.sortByDescending { it.viewerCount }
                    list
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

    private suspend fun helixLoad(): List<Stream> {
        val response = helixApi.getFollowedStreams(
            headers = helixHeaders,
            userId = userId,
            limit = 100,
            offset = offset
        )
        val users = response.data.mapNotNull { it.channelId }.let {
            helixApi.getUsers(
                headers = helixHeaders,
                ids = it
            ).data
        }
        val list = response.data.map {
            Stream(
                id = it.id,
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                gameId = it.gameId,
                gameName = it.gameName,
                type = it.type,
                title = it.title,
                viewerCount = it.viewerCount,
                startedAt = it.startedAt,
                thumbnailUrl = it.thumbnailUrl,
                profileImageUrl = it.channelId?.let { id ->
                    users.find { user -> user.channelId == id }?.profileImageUrl
                },
                tags = it.tags
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(): List<Stream> {
        val response = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(UserFollowedStreamsQuery(
            first = Optional.Present(100),
            after = Optional.Present(offset)
        )).execute()
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user!!.followedLiveUsers!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Stream(
                    id = it.stream?.id,
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    gameId = it.stream?.game?.id,
                    gameSlug = it.stream?.game?.slug,
                    gameName = it.stream?.game?.displayName,
                    type = it.stream?.type,
                    title = it.stream?.broadcaster?.broadcastSettings?.title,
                    viewerCount = it.stream?.viewersCount,
                    startedAt = it.stream?.createdAt?.toString(),
                    thumbnailUrl = it.stream?.previewImageURL,
                    profileImageUrl = it.profileImageURL,
                    tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name }
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage != false
        return list
    }

    private suspend fun gqlLoad(): List<Stream> {
        val response = gqlApi.loadFollowedStreams(gqlHeaders, 100, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.currentUser.followedLiveUsers
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                Stream(
                    id = it.stream?.id,
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    gameId = it.stream?.game?.id,
                    gameName = it.stream?.game?.displayName,
                    type = it.stream?.type,
                    title = it.stream?.title,
                    viewerCount = it.stream?.viewersCount,
                    thumbnailUrl = it.stream?.previewImageURL,
                    profileImageUrl = it.profileImageURL,
                    tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name }
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        nextPage = data.pageInfo?.hasNextPage != false
        return list
    }

    private suspend fun gqlQueryLocal(ids: List<String>): List<Stream> {
        val items = ids.chunked(100).map { list ->
            apolloClient.newBuilder().apply {
                gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
            }.build().query(UsersStreamQuery(Optional.Present(list))).execute().also { response ->
                response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
            }
        }.flatMap { it.data!!.users!! }
        val list = items.mapNotNull { item ->
            item?.let {
                if (it.stream?.viewersCount != null) {
                    Stream(
                        id = it.stream.id,
                        channelId = it.id,
                        channelLogin = it.login,
                        channelName = it.displayName,
                        gameId = it.stream.game?.id,
                        gameSlug = it.stream.game?.slug,
                        gameName = it.stream.game?.displayName,
                        type = it.stream.type,
                        title = it.stream.broadcaster?.broadcastSettings?.title,
                        viewerCount = it.stream.viewersCount,
                        startedAt = it.stream.createdAt?.toString(),
                        thumbnailUrl = it.stream.previewImageURL,
                        profileImageUrl = it.profileImageURL,
                        tags = it.stream.freeformTags?.mapNotNull { tag -> tag.name }
                    )
                } else null
            }
        }
        return list
    }

    private suspend fun helixLocal(ids: List<String>): List<Stream> {
        val items = ids.chunked(100).map {
            helixApi.getStreams(
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val users = items.mapNotNull { it.channelId }.chunked(100).map {
            helixApi.getUsers(
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val list = items.mapNotNull {
            if (it.viewerCount != null) {
                Stream(
                    id = it.id,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    gameId = it.gameId,
                    gameName = it.gameName,
                    type = it.type,
                    title = it.title,
                    viewerCount = it.viewerCount,
                    startedAt = it.startedAt,
                    thumbnailUrl = it.thumbnailUrl,
                    profileImageUrl = it.channelId?.let { id ->
                        users.find { user -> user.channelId == id }?.profileImageUrl
                    },
                    tags = it.tags
                )
            } else null
        }
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Stream>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}