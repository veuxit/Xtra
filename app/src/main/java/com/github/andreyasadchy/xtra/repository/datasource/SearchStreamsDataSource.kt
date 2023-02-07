package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.github.andreyasadchy.xtra.SearchStreamsQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class SearchStreamsDataSource private constructor(
    private val query: String,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val apolloClient: ApolloClient,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Stream>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Stream>) {
        loadInitial(params, callback) {
            try {
                when (apiPref?.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref?.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    listOf()
                }
            }
        }
    }

    private suspend fun helixLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Stream> {
        val get = helixApi.getSearchChannels(
            clientId = helixClientId,
            token = helixToken,
            query = query,
            limit = initialParams?.requestedLoadSize ?: rangeParams?.loadSize,
            offset = offset,
            live = true
        )
        val list = mutableListOf<Stream>()
        get.data.forEach {
            list.add(Stream(
                channelId = it.channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                gameId = it.stream?.gameId,
                gameName = it.stream?.gameName,
                title = it.stream?.title,
                startedAt = it.stream?.startedAt,
                profileImageUrl = it.profileImageUrl,
                tags = it.stream?.tags
            ))
        }
        offset = get.cursor
        return list
    }

    private suspend fun gqlQueryLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Stream> {
        val get1 = apolloClient.newBuilder().apply { gqlClientId?.let { addHttpHeader("Client-ID", it) } }.build().query(SearchStreamsQuery(
            query = query,
            first = Optional.Present(initialParams?.requestedLoadSize ?: rangeParams?.loadSize),
            after = Optional.Present(offset)
        )).execute().data?.searchStreams
        val get = get1?.edges
        val list = mutableListOf<Stream>()
        if (get != null) {
            for (edge in get) {
                edge.node?.let { i ->
                    list.add(Stream(
                        id = i.id,
                        channelId = i.broadcaster?.id,
                        channelLogin = i.broadcaster?.login,
                        channelName = i.broadcaster?.displayName,
                        gameId = i.game?.id,
                        gameName = i.game?.displayName,
                        type = i.type,
                        title = i.broadcaster?.broadcastSettings?.title,
                        viewerCount = i.viewersCount,
                        startedAt = i.createdAt?.toString(),
                        thumbnailUrl = i.previewImageURL,
                        profileImageUrl = i.broadcaster?.profileImageURL,
                        tags = i.freeformTags?.mapNotNull { it.name }
                    ))
                }
            }
            offset = get1.edges.lastOrNull()?.cursor?.toString()
            nextPage = get1.pageInfo?.hasNextPage ?: true
        }
        return list
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Stream>) {
        loadRange(params, callback) {
            if (!offset.isNullOrBlank()) {
                when (api) {
                    C.HELIX -> helixLoad(rangeParams = params)
                    C.GQL_QUERY -> if (nextPage) gqlQueryLoad(rangeParams = params) else listOf()
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
        private val apolloClient: ApolloClient,
        private val apiPref: ArrayList<Pair<Long?, String?>?>?,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Stream, SearchStreamsDataSource>() {

        override fun create(): DataSource<Int, Stream> =
                SearchStreamsDataSource(query, helixClientId, helixToken, helixApi, gqlClientId, apolloClient, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
