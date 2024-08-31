package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.StreamSortEnum
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.StreamSort
import com.github.andreyasadchy.xtra.util.C

class GameStreamsDataSource(
    private val gameId: String?,
    private val gameSlug: String?,
    private val gameName: String?,
    private val helixHeaders: Map<String, String>,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlQuerySort: StreamSort?,
    private val gqlSort: StreamSortEnum?,
    private val tags: List<String>?,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Stream>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return try {
            val response = try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && (gqlSort == StreamSortEnum.VIEWERS_HIGH || gqlSort == null) && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && (gqlSort == StreamSortEnum.VIEWERS_HIGH || gqlSort == null) && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> { api = C.GQL; gqlLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.elementAt(2)?.second) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && (gqlSort == StreamSortEnum.VIEWERS_HIGH || gqlSort == null) && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                            C.GQL -> { api = C.GQL; gqlLoad(params) }
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
            gameId = gameId,
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
                gameId = gameId,
                gameName = gameName,
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
        val response = gqlApi.loadQueryGameStreams(
            headers = gqlHeaders,
            id = if (!gameId.isNullOrBlank()) gameId else null,
            slug = if (gameId.isNullOrBlank() && !gameSlug.isNullOrBlank()) gameSlug else null,
            name = if (gameId.isNullOrBlank() && gameSlug.isNullOrBlank() && !gameName.isNullOrBlank()) gameName else null,
            sort = gqlQuerySort?.toString(),
            tags = tags,
            first = params.loadSize,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.game!!.streams!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Stream(
                    id = it.id,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
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
        val response = gqlApi.loadGameStreams(gqlHeaders, gameSlug, gqlSort?.value, tags, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.game.streams
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                Stream(
                    id = it.id,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
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
