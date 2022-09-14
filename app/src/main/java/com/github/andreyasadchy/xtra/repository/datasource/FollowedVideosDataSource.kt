package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.util.C
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope

class FollowedVideosDataSource(
    private val userId: String?,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val gqlQueryType: BroadcastType?,
    private val gqlQuerySort: VideoSort?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Video>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Video>) {
        loadInitial(params, callback) {
            try {
                when (apiPref.elementAt(0)?.second) {
                    C.GQL_QUERY -> if (!gqlToken.isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                    C.GQL -> if (!gqlToken.isNullOrBlank() && gqlQueryType == BroadcastType.ARCHIVE && gqlQuerySort == VideoSort.TIME) { api = C.GQL; gqlLoad(params) } else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.GQL_QUERY -> if (!gqlToken.isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                        C.GQL -> if (!gqlToken.isNullOrBlank() && gqlQueryType == BroadcastType.ARCHIVE && gqlQuerySort == VideoSort.TIME) { api = C.GQL; gqlLoad(params) } else throw Exception()
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
        val get = gqlApi.loadQueryFollowedVideos(
            clientId = gqlClientId,
            token = gqlToken,
            query = context.resources.openRawResource(R.raw.followedvideos).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("id", userId)
                addProperty("sort", gqlQuerySort.toString())
                val typeArray = JsonArray()
                gqlQueryType?.let {
                    typeArray.add(it.toString())
                }
                add("type", typeArray)
                addProperty("first", 50 /*initialParams?.requestedLoadSize ?: rangeParams?.loadSize*/)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Video> {
        val get = gqlApi.loadFollowedVideos(gqlClientId, gqlToken, 50 /*initialParams?.requestedLoadSize ?: rangeParams?.loadSize*/, offset)
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Video>) {
        loadRange(params, callback) {
            if (!offset.isNullOrBlank()) {
                when (api) {
                    C.GQL_QUERY -> if (nextPage) gqlQueryLoad(rangeParams = params) else listOf()
                    C.GQL -> if (nextPage) gqlLoad(rangeParams = params) else listOf()
                    else -> listOf()
                }
            } else listOf()
        }
    }

    class Factory(
        private val userId: String?,
        private val gqlClientId: String?,
        private val gqlToken: String?,
        private val gqlQueryType: BroadcastType?,
        private val gqlQuerySort: VideoSort?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, FollowedVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                FollowedVideosDataSource(userId, gqlClientId, gqlToken, gqlQueryType, gqlQuerySort, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
