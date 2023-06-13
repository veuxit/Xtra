package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.StreamSortEnum
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.StreamSort
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class GameStreamsDataSource(
    private val gameId: String?,
    private val gameName: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
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
                    C.HELIX -> if (!helixToken.isNullOrBlank() && (gqlSort == StreamSortEnum.VIEWERS_HIGH || gqlSort == null) && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank() && (gqlSort == StreamSortEnum.VIEWERS_HIGH || gqlSort == null) && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> { api = C.GQL; gqlLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.elementAt(2)?.second) {
                            C.HELIX -> if (!helixToken.isNullOrBlank() && (gqlSort == StreamSortEnum.VIEWERS_HIGH || gqlSort == null) && tags.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                            C.GQL -> { api = C.GQL; gqlLoad(params) }
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
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
        val get = helixApi.getStreams(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            gameId = gameId,
            limit = params.loadSize,
            offset = offset
        )
        val list = mutableListOf<Stream>()
        get.data.let { list.addAll(it) }
        val ids = mutableListOf<String>()
        for (i in list) {
            i.channelId?.let { ids.add(it) }
        }
        if (ids.isNotEmpty()) {
            val users = helixApi.getUsers(clientId = helixClientId, token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, ids = ids).data
            for (i in users) {
                val items = list.filter { it.channelId == i.channelId }
                for (item in items) {
                    item.profileImageUrl = i.profileImageUrl
                }
            }
        }
        offset = get.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Stream> {
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQueryGameStreams(
            headers = gqlHeaders,
            query = context.resources.openRawResource(R.raw.gamestreams).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("id", if (!gameId.isNullOrBlank()) gameId else null)
                addProperty("name", if (gameId.isNullOrBlank() && !gameName.isNullOrBlank()) gameName else null)
                addProperty("sort", gqlQuerySort.toString())
                val tagsArray = JsonArray()
                tags?.forEach {
                    tagsArray.add(it)
                }
                add("tags", tagsArray)
                addProperty("first", params.loadSize)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data.onEach {
            it.gameId = gameId
            it.gameName = gameName
        }
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Stream> {
        val get = gqlApi.loadGameStreams(gqlHeaders, gameName, gqlSort?.value, tags, params.loadSize, offset)
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data.onEach {
            it.gameId = gameId
            it.gameName = gameName
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Stream>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
