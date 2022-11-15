package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.video.BroadcastType
import com.github.andreyasadchy.xtra.model.helix.video.Period
import com.github.andreyasadchy.xtra.model.helix.video.Sort
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.util.C
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope

class ChannelVideosDataSource (
    private val channelId: String?,
    private val channelLogin: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixPeriod: Period,
    private val helixBroadcastTypes: BroadcastType,
    private val helixSort: Sort,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlQueryType: com.github.andreyasadchy.xtra.type.BroadcastType?,
    private val gqlQuerySort: VideoSort?,
    private val gqlType: String?,
    private val gqlSort: String?,
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
                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> if (helixPeriod == Period.ALL) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                    C.GQL -> if (helixPeriod == Period.ALL) { api = C.GQL; gqlLoad(params) } else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> if (helixPeriod == Period.ALL) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                        C.GQL -> if (helixPeriod == Period.ALL) { api = C.GQL; gqlLoad(params) } else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    try {
                        when (apiPref.elementAt(2)?.second) {
                            C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> if (helixPeriod == Period.ALL) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                            C.GQL -> if (helixPeriod == Period.ALL) { api = C.GQL; gqlLoad(params) } else throw Exception()
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        listOf()
                    }
                }
            }
        }
    }

    private suspend fun helixLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Video> {
        val get = helixApi.getVideos(
            clientId = helixClientId,
            token = helixToken,
            channelId = channelId,
            period = helixPeriod,
            broadcastType = helixBroadcastTypes,
            sort = helixSort,
            limit = 30 /*initialParams?.requestedLoadSize ?: rangeParams?.loadSize*/,
            offset = offset
        )
        return if (get.data != null) {
            offset = get.pagination?.cursor
            get.data
        } else listOf()
    }

    private suspend fun gqlQueryLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Video> {
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQueryUserVideos(
            clientId = gqlClientId,
            query = context.resources.openRawResource(R.raw.uservideos).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                addProperty("sort", gqlQuerySort.toString())
                val typeArray = JsonArray()
                gqlQueryType?.let {
                    typeArray.add(it.toString())
                }
                add("types", typeArray)
                addProperty("first", 30 /*initialParams?.requestedLoadSize ?: rangeParams?.loadSize*/)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Video> {
        val get = gqlApi.loadChannelVideos(gqlClientId, channelLogin, gqlType, gqlSort, 30 /*initialParams?.requestedLoadSize ?: rangeParams?.loadSize*/, offset)
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Video>) {
        loadRange(params, callback) {
            if (!offset.isNullOrBlank()) {
                when (api) {
                    C.HELIX -> helixLoad(rangeParams = params)
                    C.GQL_QUERY -> if (nextPage) gqlQueryLoad(rangeParams = params) else listOf()
                    C.GQL -> if (nextPage) gqlLoad(rangeParams = params) else listOf()
                    else -> listOf()
                }
            } else listOf()
        }
    }

    class Factory(
        private val channelId: String?,
        private val channelLogin: String?,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val helixPeriod: Period,
        private val helixBroadcastTypes: BroadcastType,
        private val helixSort: Sort,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val gqlQueryType: com.github.andreyasadchy.xtra.type.BroadcastType?,
        private val gqlQuerySort: VideoSort?,
        private val gqlType: String?,
        private val gqlSort: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, ChannelVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                ChannelVideosDataSource(channelId, channelLogin, helixClientId, helixToken, helixPeriod, helixBroadcastTypes, helixSort, helixApi, gqlClientId, gqlQueryType, gqlQuerySort, gqlType, gqlSort, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
