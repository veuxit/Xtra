package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.video.Period
import com.github.andreyasadchy.xtra.model.helix.video.Sort
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class GameVideosDataSource private constructor(
    private val gameId: String?,
    private val gameName: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixPeriod: Period,
    private val helixBroadcastTypes: com.github.andreyasadchy.xtra.model.helix.video.BroadcastType,
    private val helixLanguage: String?,
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
                    C.GQL -> if (helixLanguage.isNullOrBlank() && helixPeriod == Period.WEEK) gqlInitial(params) else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial(params) else throw Exception()
                        C.GQL -> if (helixLanguage.isNullOrBlank() && helixPeriod == Period.WEEK) gqlInitial(params) else throw Exception()
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
        val get = helixApi.getTopVideos(helixClientId, helixToken, gameId, helixPeriod, helixBroadcastTypes, helixLanguage, helixSort, params.requestedLoadSize, offset)
        val list = mutableListOf<Video>()
        get.data?.let { list.addAll(it) }
        val ids = mutableListOf<String>()
        for (i in list) {
            i.user_id?.let { ids.add(it) }
        }
        if (ids.isNotEmpty()) {
            val users = helixApi.getUsersById(helixClientId, helixToken, ids).data
            if (users != null) {
                for (i in users) {
                    val items = list.filter { it.user_id == i.id }
                    for (item in items) {
                        item.profileImageURL = i.profile_image_url
                    }
                }
            }
        }
        offset = get.pagination?.cursor
        return list
    }

    private suspend fun gqlInitial(params: LoadInitialParams): List<Video> {
        api = C.GQL
        val get = gqlApi.loadGameVideos(gqlClientId, gameName, gqlType, gqlSort, params.requestedLoadSize, offset)
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
        val get = helixApi.getTopVideos(helixClientId, helixToken, gameId, helixPeriod, helixBroadcastTypes, helixLanguage, helixSort, params.loadSize, offset)
        val list = mutableListOf<Video>()
        if (offset != null && offset != "") {
            get.data?.let { list.addAll(it) }
            val ids = mutableListOf<String>()
            for (i in list) {
                i.user_id?.let { ids.add(it) }
            }
            if (ids.isNotEmpty()) {
                val users = helixApi.getUsersById(helixClientId, helixToken, ids).data
                if (users != null) {
                    for (i in users) {
                        val items = list.filter { it.user_id == i.id }
                        for (item in items) {
                            item.profileImageURL = i.profile_image_url
                        }
                    }
                }
            }
            offset = get.pagination?.cursor
        }
        return list
    }

    private suspend fun gqlRange(params: LoadRangeParams): List<Video> {
        val get = gqlApi.loadGameVideos(gqlClientId, gameName, gqlType, gqlSort, params.loadSize, offset)
        return if (offset != null && offset != "") {
            offset = get.cursor
            get.data
        } else mutableListOf()
    }

    class Factory (
        private val gameId: String?,
        private val gameName: String?,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val helixPeriod: Period,
        private val helixBroadcastTypes: com.github.andreyasadchy.xtra.model.helix.video.BroadcastType,
        private val helixLanguage: String?,
        private val helixSort: Sort,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val gqlType: String?,
        private val gqlSort: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, GameVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                GameVideosDataSource(gameId, gameName, helixClientId, helixToken, helixPeriod, helixBroadcastTypes, helixLanguage, helixSort, helixApi, gqlClientId, gqlType, gqlSort, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
