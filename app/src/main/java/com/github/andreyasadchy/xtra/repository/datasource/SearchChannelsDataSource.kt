package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.github.andreyasadchy.xtra.SearchChannelsQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class SearchChannelsDataSource private constructor(
    private val query: String,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<User>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<User>) {
        loadInitial(params, callback) {
            try {
                when (apiPref?.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad() }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref?.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> { api = C.GQL; gqlLoad() }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    try {
                        when (apiPref?.elementAt(2)?.second) {
                            C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                            C.GQL -> { api = C.GQL; gqlLoad() }
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        listOf()
                    }
                }
            }
        }
    }

    private suspend fun helixLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<User> {
        val get = helixApi.getSearchChannels(
            clientId = helixClientId,
            token = helixToken,
            query = query,
            limit = initialParams?.requestedLoadSize ?: rangeParams?.loadSize,
            offset = offset
        )
        offset = get.cursor
        return get.data
    }

    private suspend fun gqlQueryLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<User> {
        val get1 = apolloClient.newBuilder().apply { gqlClientId?.let { addHttpHeader("Client-ID", it) } }.build().query(SearchChannelsQuery(
            query = query,
            first = Optional.Present(initialParams?.requestedLoadSize ?: rangeParams?.loadSize),
            after = Optional.Present(offset)
        )).execute().data?.searchUsers
        val get = get1?.edges
        val list = mutableListOf<User>()
        if (get != null) {
            for (edge in get) {
                edge.node?.let { i ->
                    list.add(User(
                        channelId = i.id,
                        channelLogin = i.login,
                        channelName = i.displayName,
                        profileImageUrl = i.profileImageURL,
                        followersCount = i.followers?.totalCount,
                        type = i.stream?.type
                    ))
                }
            }
            offset = get1.edges.lastOrNull()?.cursor?.toString()
            nextPage = get1.pageInfo?.hasNextPage ?: true
        }
        return list
    }

    private suspend fun gqlLoad(): List<User> {
        val get = gqlApi.loadSearchChannels(gqlClientId, query, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<User>) {
        loadRange(params, callback) {
            if (!offset.isNullOrBlank()) {
                when (api) {
                    C.HELIX -> helixLoad(rangeParams = params)
                    C.GQL_QUERY -> if (nextPage) gqlQueryLoad(rangeParams = params) else listOf()
                    C.GQL -> gqlLoad()
                    else -> listOf()
                }
            } else listOf()
        }
    }

    class Factory(
        private val query: String,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val gqlApi: GraphQLRepository,
        private val apolloClient: ApolloClient,
        private val apiPref: ArrayList<Pair<Long?, String?>?>?,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, User, SearchChannelsDataSource>() {

        override fun create(): DataSource<Int, User> =
                SearchChannelsDataSource(query, helixClientId, helixToken, helixApi, gqlClientId, gqlApi, apolloClient, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
