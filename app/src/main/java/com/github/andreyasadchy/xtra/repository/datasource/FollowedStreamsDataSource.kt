package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.github.andreyasadchy.xtra.repository.LocalFollowRepository
import com.github.andreyasadchy.xtra.repository.TwitchService
import kotlinx.coroutines.CoroutineScope

class FollowedStreamsDataSource(
    private val useHelix: Boolean,
    private val localFollows: LocalFollowRepository,
    private val repository: TwitchService,
    private val gqlClientId: String?,
    private val helixClientId: String?,
    private val userToken: String?,
    private val userId: String,
    private val api: HelixApi,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Stream>(coroutineScope) {
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Stream>) {
        loadInitial(params, callback) {
            val list = mutableListOf<Stream>()
            val userIds = mutableListOf<String>()
            if (localFollows.loadFollows().isNotEmpty()) {
                val ids = mutableListOf<String>()
                for (i in localFollows.loadFollows()) {
                    ids.add(i.user_id)
                }
                for (localIds in ids.chunked(100)) {
                    val streams = mutableListOf<Stream>()
                    if (useHelix) {
                        api.getStreams(helixClientId, userToken, localIds).data?.let { streams.addAll(it) }
                    }
                    for (i in streams) {
                        if (i.viewer_count != null) {
                            if (useHelix) { i.user_id?.let { userIds.add(it) } }
                            list.add(i)
                        }
                    }
                }
            }
            if (userId != "") {
                val get = api.getFollowedStreams(helixClientId, userToken, userId, params.requestedLoadSize, offset)
                if (get.data != null) {
                    for (i in get.data) {
                        val item = list.find { it.user_id == i.user_id }
                        if (item == null) {
                            i.user_id?.let { userIds.add(it) }
                            list.add(i)
                        }
                    }
                    offset = get.pagination?.cursor
                }
            }
            if (userIds.isNotEmpty()) {
                val users = api.getUserById(helixClientId, userToken, userIds).data
                if (users != null) {
                    for (i in users) {
                        val item = list.find { it.user_id == i.id }
                        if (item != null) {
                            item.profileImageURL = i.profile_image_url
                        }
                    }
                }
            }
            list
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Stream>) {
        loadRange(params, callback) {
            val list = mutableListOf<Stream>()
            if (offset != null && offset != "") {
                val userIds = mutableListOf<String>()
                if (userId != "") {
                    val get = api.getFollowedStreams(helixClientId, userToken, userId, params.loadSize, offset)
                    if (get.data != null) {
                        for (i in get.data) {
                            val item = list.find { it.user_id == i.user_id }
                            if (item == null) {
                                i.user_id?.let { userIds.add(it) }
                                list.add(i)
                            }
                        }
                        offset = get.pagination?.cursor
                    }
                }
                if (userIds.isNotEmpty()) {
                    val users = api.getUserById(helixClientId, userToken, userIds).data
                    if (users != null) {
                        for (i in users) {
                            val item = list.find { it.user_id == i.id }
                            if (item != null) {
                                item.profileImageURL = i.profile_image_url
                            }
                        }
                    }
                }
            }
            list
        }
    }

    class Factory(
        private val useHelix: Boolean,
        private val localFollows: LocalFollowRepository,
        private val repository: TwitchService,
        private val gqlClientId: String?,
        private val helixClientId: String?,
        private val userToken: String?,
        private val user_id: String,
        private val api: HelixApi,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Stream, FollowedStreamsDataSource>() {

        override fun create(): DataSource<Int, Stream> =
                FollowedStreamsDataSource(useHelix, localFollows, repository, gqlClientId, helixClientId, userToken, user_id, api, coroutineScope).also(sourceLiveData::postValue)
    }
}