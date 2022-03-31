package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.clip.Clip
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class GameClipsDataSource(
    private val gameId: String?,
    private val gameName: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val started_at: String?,
    private val ended_at: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlPeriod: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Clip>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Clip>) {
        loadInitial(params, callback) {
            try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial(params) else throw Exception()
                    C.GQL -> gqlInitial(params)
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial(params) else throw Exception()
                        C.GQL -> gqlInitial(params)
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
        }
    }

    private suspend fun helixInitial(params: LoadInitialParams): List<Clip> {
        api = C.HELIX
        val get = helixApi.getClips(helixClientId, helixToken, null, gameId, started_at, ended_at, params.requestedLoadSize, offset)
        val list = mutableListOf<Clip>()
        get.data?.let { list.addAll(it) }
        val userIds = mutableListOf<String>()
        for (i in list) {
            i.broadcaster_id?.let { userIds.add(it) }
        }
        if (userIds.isNotEmpty()) {
            val users = helixApi.getUserById(helixClientId, helixToken, userIds).data
            if (users != null) {
                for (i in users) {
                    val items = list.filter { it.broadcaster_id == i.id }
                    for (item in items) {
                        item.broadcaster_login = i.login
                        item.profileImageURL = i.profile_image_url
                    }
                }
            }
        }
        offset = get.pagination?.cursor
        return list
    }

    private suspend fun gqlInitial(params: LoadInitialParams): List<Clip> {
        api = C.GQL
        val get = gqlApi.loadGameClips(gqlClientId, gameName, gqlPeriod, params.requestedLoadSize, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Clip>) {
        loadRange(params, callback) {
            when (api) {
                C.HELIX -> helixRange(params)
                C.GQL -> gqlRange(params)
                else -> mutableListOf()
            }
        }
    }

    private suspend fun helixRange(params: LoadRangeParams): List<Clip> {
        val get = helixApi.getClips(helixClientId, helixToken, null, gameId, started_at, ended_at, params.loadSize, offset)
        val list = mutableListOf<Clip>()
        if (offset != null && offset != "") {
            get.data?.let { list.addAll(it) }
            val userIds = mutableListOf<String>()
            for (i in list) {
                i.broadcaster_id?.let { userIds.add(it) }
            }
            if (userIds.isNotEmpty()) {
                val users = helixApi.getUserById(helixClientId, helixToken, userIds).data
                if (users != null) {
                    for (i in users) {
                        val items = list.filter { it.broadcaster_id == i.id }
                        for (item in items) {
                            item.broadcaster_login = i.login
                            item.profileImageURL = i.profile_image_url
                        }
                    }
                }
            }
            offset = get.pagination?.cursor
        }
        return list
    }

    private suspend fun gqlRange(params: LoadRangeParams): List<Clip> {
        val get = gqlApi.loadGameClips(gqlClientId, gameName, gqlPeriod, params.loadSize, offset)
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
        private val started_at: String?,
        private val ended_at: String?,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val gqlPeriod: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Clip, GameClipsDataSource>() {

        override fun create(): DataSource<Int, Clip> =
                GameClipsDataSource(gameId, gameName, helixClientId, helixToken, started_at, ended_at, helixApi, gqlClientId, gqlPeriod, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
