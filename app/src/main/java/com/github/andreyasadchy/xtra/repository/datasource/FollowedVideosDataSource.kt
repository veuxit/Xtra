package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class FollowedVideosDataSource(
    private val userId: String?,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Video>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Video>) {
        loadInitial(params, callback) {
            try {
                when (apiPref.elementAt(0)?.second) {
                    C.GQL -> if (!gqlToken.isNullOrBlank()) gqlInitial(params) else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                mutableListOf()
            }
        }
    }

    private suspend fun gqlInitial(params: LoadInitialParams): List<Video> {
        api = C.GQL
        val get = gqlApi.loadFollowedVideos(gqlClientId, gqlToken, params.requestedLoadSize, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Video>) {
        loadRange(params, callback) {
            when (api) {
                C.GQL -> gqlRange(params)
                else -> mutableListOf()
            }
        }
    }

    private suspend fun gqlRange(params: LoadRangeParams): List<Video> {
        val get = gqlApi.loadFollowedVideos(gqlClientId, gqlToken, params.loadSize, offset)
        return if (!offset.isNullOrBlank()) {
            offset = get.cursor
            get.data
        } else mutableListOf()
    }

    class Factory(
        private val userId: String?,
        private val gqlClientId: String?,
        private val gqlToken: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, FollowedVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                FollowedVideosDataSource(userId, gqlClientId, gqlToken, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
