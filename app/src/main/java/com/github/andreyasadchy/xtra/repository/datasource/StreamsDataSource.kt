package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.github.andreyasadchy.xtra.TopStreamsQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C

class StreamsDataSource(
    private val helixHeaders: Map<String, String>,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val tags: List<String>?,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: List<String>) : PagingSource<Int, Stream>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return try {
            val response = try {
                when (apiPref.getOrNull(0)) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                    C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.getOrNull(1)) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                        C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.getOrNull(2)) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                            C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) }
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") return LoadResult.Error(e)
                        listOf()
                    }
                }
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

    private suspend fun helixLoad(params: LoadParams<Int>): List<Stream> {
        val response = helixApi.getStreams(
            headers = helixHeaders,
            limit = params.loadSize,
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

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Stream> {
        val response = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(TopStreamsQuery(
            tags = Optional.Present(tags),
            first = Optional.Present(params.loadSize),
            after = Optional.Present(offset)
        )).execute()
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.streams!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Stream(
                    id = it.id,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    type = it.type,
                    title = it.broadcaster?.broadcastSettings?.title,
                    viewerCount = it.viewersCount,
                    startedAt = it.createdAt?.toString(),
                    thumbnailUrl = it.previewImageURL,
                    profileImageUrl = it.broadcaster?.profileImageURL,
                    tags = it.freeformTags?.mapNotNull { tag -> tag.name }
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Stream> {
        val response = gqlApi.loadTopStreams(gqlHeaders, tags, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.streams
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                Stream(
                    id = it.id,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    type = it.type,
                    title = it.title,
                    viewerCount = it.viewersCount,
                    thumbnailUrl = it.previewImageURL,
                    profileImageUrl = it.broadcaster?.profileImageURL,
                    tags = it.freeformTags?.mapNotNull { tag -> tag.name }
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Stream>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
