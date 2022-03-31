package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class GameStreamsDataSource private constructor(
    private val gameId: String?,
    private val gameName: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val tags: List<String>?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Stream>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Stream>) {
        loadInitial(params, callback) {
            try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank() && tags.isNullOrEmpty()) helixInitial(params) else throw Exception()
                    C.GQL -> gqlInitial(params)
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank() && tags.isNullOrEmpty()) helixInitial(params) else throw Exception()
                        C.GQL -> gqlInitial(params)
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
        }
    }

    private suspend fun helixInitial(params: LoadInitialParams): List<Stream> {
        api = C.HELIX
        val get = helixApi.getTopStreams(helixClientId, helixToken, gameId, null, params.requestedLoadSize, offset)
        val list = mutableListOf<Stream>()
        get.data?.let { list.addAll(it) }
        val ids = mutableListOf<String>()
        for (i in list) {
            i.user_id?.let { ids.add(it) }
        }
        if (ids.isNotEmpty()) {
            val users = helixApi.getUserById(helixClientId, helixToken, ids).data
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

    private suspend fun gqlInitial(params: LoadInitialParams): List<Stream> {
        api = C.GQL
        val get = gqlApi.loadGameStreams(gqlClientId, gameName, tags, params.requestedLoadSize, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Stream>) {
        loadRange(params, callback) {
            when (api) {
                C.HELIX -> helixRange(params)
                C.GQL -> gqlRange(params)
                else -> mutableListOf()
            }
        }
    }

    private suspend fun helixRange(params: LoadRangeParams): List<Stream> {
        val get = helixApi.getTopStreams(helixClientId, helixToken, gameId, null, params.loadSize, offset)
        val list = mutableListOf<Stream>()
        if (offset != null && offset != "") {
            get.data?.let { list.addAll(it) }
            val ids = mutableListOf<String>()
            for (i in list) {
                i.user_id?.let { ids.add(it) }
            }
            if (ids.isNotEmpty()) {
                val users = helixApi.getUserById(helixClientId, helixToken, ids).data
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

    private suspend fun gqlRange(params: LoadRangeParams): List<Stream> {
        val get = gqlApi.loadGameStreams(gqlClientId, gameName, tags, params.loadSize, offset)
        return if (offset != null && offset != "") {
            offset = get.cursor
            get.data
        } else mutableListOf()
    }

    class Factory(
        private val gameId: String?,
        private val gameName: String?,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val tags: List<String>?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Stream, GameStreamsDataSource>() {

        override fun create(): DataSource<Int, Stream> =
            GameStreamsDataSource(gameId, gameName, helixClientId, helixToken, helixApi, gqlClientId, tags, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
