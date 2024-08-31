package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C

class SearchGamesDataSource(
    private val query: String,
    private val helixHeaders: Map<String, String>,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?) : PagingSource<Int, Game>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Game> {
        return try {
            val response = if (query.isBlank()) listOf() else try {
                when (apiPref?.elementAt(0)?.second) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad() }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref?.elementAt(1)?.second) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> { api = C.GQL; gqlLoad() }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref?.elementAt(2)?.second) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                            C.GQL -> { api = C.GQL; gqlLoad() }
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
                nextKey = if (!offset.isNullOrBlank() && (api != C.GQL_QUERY || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun helixLoad(params: LoadParams<Int>): List<Game> {
        val response = helixApi.getSearchGames(
            headers = helixHeaders,
            query = query,
            limit = params.loadSize,
            offset = offset
        )
        val list = response.data.map {
            Game(
                gameId = it.id,
                gameName = it.name,
                boxArtUrl = it.boxArtUrl,
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Game> {
        val response = gqlApi.loadQuerySearchGames(
            headers = gqlHeaders,
            query = query,
            first = params.loadSize,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.searchCategories!!
        val list = data.edges!!.mapNotNull { item ->
            item.node?.let {
                Game(
                    gameId = it.id,
                    gameSlug = it.slug,
                    gameName = it.displayName,
                    boxArtUrl = it.boxArtURL,
                    viewersCount = it.viewersCount ?: 0,
                    broadcastersCount = it.broadcastersCount ?: 0,
                    tags = it.tags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    }
                )
            }
        }
        offset = data.edges.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(): List<Game> {
        val response = gqlApi.loadSearchGames(gqlHeaders, query, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.searchFor.games
        val list = data.edges.map { item ->
            item.item.let {
                Game(
                    gameId = it.id,
                    gameSlug = it.slug,
                    gameName = it.displayName,
                    boxArtUrl = it.boxArtURL,
                    viewersCount = it.viewersCount ?: 0,
                    tags = it.tags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    }
                )
            }
        }
        offset = data.cursor
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Game>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
