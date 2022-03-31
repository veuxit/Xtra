package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.video.BroadcastType
import com.github.andreyasadchy.xtra.model.helix.video.Period
import com.github.andreyasadchy.xtra.model.helix.video.Sort
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
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
    private val gqlType: String?,
    private val gqlSort: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Video>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Video>) {
        loadInitial(params, callback) {
            try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial(params) else throw Exception()
                    C.GQL -> if (helixPeriod == Period.ALL) gqlInitial(params) else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial(params) else throw Exception()
                        C.GQL -> if (helixPeriod == Period.ALL) gqlInitial(params) else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
        }
    }

    private suspend fun helixInitial(params: LoadInitialParams): List<Video> {
        api = C.HELIX
        val get = helixApi.getChannelVideos(helixClientId, helixToken, channelId, helixPeriod, helixBroadcastTypes, helixSort, params.requestedLoadSize, offset)
        return if (get.data != null) {
            offset = get.pagination?.cursor
            get.data
        } else mutableListOf()
    }

    private suspend fun gqlInitial(params: LoadInitialParams): List<Video> {
        api = C.GQL
        val get = gqlApi.loadChannelVideos(gqlClientId, channelLogin, gqlType, gqlSort, params.requestedLoadSize, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Video>) {
        loadRange(params, callback) {
            when (api) {
                C.HELIX -> helixRange(params)
                C.GQL -> gqlRange(params)
                else -> mutableListOf()
            }
        }
    }

    private suspend fun helixRange(params: LoadRangeParams): List<Video> {
        val get = helixApi.getChannelVideos(helixClientId, helixToken, channelId, helixPeriod, helixBroadcastTypes, helixSort, params.loadSize, offset)
        return if (offset != null && offset != "") {
            if (get.data != null) {
                offset = get.pagination?.cursor
                get.data
            } else mutableListOf()
        } else mutableListOf()
    }

    private suspend fun gqlRange(params: LoadRangeParams): List<Video> {
        val get = gqlApi.loadChannelVideos(gqlClientId, channelLogin, gqlType, gqlSort, params.loadSize, offset)
        return if (offset != null && offset != "") {
            offset = get.cursor
            get.data
        } else mutableListOf()
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
        private val gqlType: String?,
        private val gqlSort: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, ChannelVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                ChannelVideosDataSource(channelId, channelLogin, helixClientId, helixToken, helixPeriod, helixBroadcastTypes, helixSort, helixApi, gqlClientId, gqlType, gqlSort, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
