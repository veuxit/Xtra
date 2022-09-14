package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope

class SearchVideosDataSource private constructor(
    private val query: String,
    private val gqlClientId: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Video>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Video>) {
        loadInitial(params, callback) {
            try {
                when (apiPref?.elementAt(0)?.second) {
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad() }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref?.elementAt(1)?.second) {
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

    private suspend fun gqlQueryLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Video> {
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQuerySearchVideos(
            clientId = gqlClientId,
            query = context.resources.openRawResource(R.raw.searchvideos).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("query", query)
                addProperty("first", initialParams?.requestedLoadSize ?: rangeParams?.loadSize)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlLoad(): List<Video> {
        val get = gqlApi.loadSearchVideos(gqlClientId, query, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Video>) {
        loadRange(params, callback) {
            if (!offset.isNullOrBlank()) {
                when (api) {
                    C.GQL_QUERY -> if (nextPage) gqlQueryLoad(rangeParams = params) else listOf()
                    C.GQL -> gqlLoad()
                    else -> listOf()
                }
            } else listOf()
        }
    }

    class Factory(
        private val query: String,
        private val gqlClientId: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>?,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, SearchVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                SearchVideosDataSource(query, gqlClientId, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
