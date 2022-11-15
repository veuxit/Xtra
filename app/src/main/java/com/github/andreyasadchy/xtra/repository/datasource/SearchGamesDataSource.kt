package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.game.Game
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope

class SearchGamesDataSource private constructor(
    private val query: String,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Game>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Game>) {
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

    private suspend fun helixLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Game> {
        val get = helixApi.getSearchGames(
            clientId = helixClientId,
            token = helixToken,
            query = query,
            limit = initialParams?.requestedLoadSize ?: rangeParams?.loadSize,
            offset = offset
        )
        return if (get.data != null) {
            offset = get.pagination?.cursor
            get.data
        } else listOf()
    }

    private suspend fun gqlQueryLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Game> {
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQuerySearchGames(
            clientId = gqlClientId,
            query = context.resources.openRawResource(R.raw.searchgames).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("query", query)
                addProperty("first", initialParams?.requestedLoadSize ?: rangeParams?.loadSize)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlLoad(): List<Game> {
        val get = gqlApi.loadSearchGames(gqlClientId, query, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Game>) {
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
        private val apiPref: ArrayList<Pair<Long?, String?>?>?,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Game, SearchGamesDataSource>() {

        override fun create(): DataSource<Int, Game> =
                SearchGamesDataSource(query, helixClientId, helixToken, helixApi, gqlClientId, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
