package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C

class SearchChannelsDataSource(
    private val query: String,
    private val helixHeaders: Map<String, String>,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: List<String>,
) : PagingSource<Int, User>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
        return try {
            val response = if (query.isBlank()) listOf() else try {
                when (apiPref.getOrNull(0)) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                    C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad() }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.getOrNull(1)) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                        C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad() }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.getOrNull(2)) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                            C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad() }
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
                nextKey = if (!offset.isNullOrBlank() && (api != C.GQL || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun helixLoad(params: LoadParams<Int>): List<User> {
        val response = helixApi.getSearchChannels(
            headers = helixHeaders,
            query = query,
            limit = params.loadSize,
            offset = offset
        )
        val list = response.data.map {
            User(
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                profileImageUrl = it.profileImageUrl,
                isLive = it.isLive
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<User> {
        val response = gqlApi.loadQuerySearchChannels(
            headers = gqlHeaders,
            query = query,
            first = params.loadSize,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.searchUsers!!
        val list = data.edges!!.mapNotNull { item ->
            item.node?.let {
                User(
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    profileImageUrl = it.profileImageURL,
                    followersCount = it.followers?.totalCount,
                    type = it.stream?.type
                )
            }
        }
        offset = data.edges.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage != false
        return list
    }

    private suspend fun gqlLoad(): List<User> {
        val response = gqlApi.loadSearchChannels(gqlHeaders, query, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.searchFor.channels
        val list = data.edges.map { item ->
            item.item.let {
                User(
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    profileImageUrl = it.profileImageURL,
                    followersCount = it.followers?.totalCount,
                    type = it.stream?.type
                )
            }
        }
        offset = data.cursor
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
