package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class FollowedStreamsDataSource(
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val userId: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Stream>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return try {
            val response = try {
                if (!offset.isNullOrBlank()) {
                    when (api) {
                        C.HELIX -> helixLoad()
                        C.GQL_QUERY -> if (nextPage) gqlQueryLoad() else listOf()
                        C.GQL -> gqlLoad()
                        else -> listOf()
                    }
                } else {
                    val list = mutableListOf<Stream>()
                    val localIds = localFollowsChannel.loadFollows().mapNotNull { it.userId }
                    val local = if (localIds.isNotEmpty()) {
                        try {
                            gqlQueryLocal(localIds)
                        } catch (e: Exception) {
                            if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                            try {
                                if (!helixToken.isNullOrBlank()) helixLocal(localIds) else throw Exception()
                            } catch (e: Exception) {
                                listOf()
                            }
                        }
                    } else listOf()
                    if (local.isNotEmpty()) {
                        list.addAll(local)
                    }
                    val remote = try {
                        when (apiPref.elementAt(0)?.second) {
                            C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                            C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad() } else throw Exception()
                            C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlLoad() } else throw Exception()
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                        try {
                            when (apiPref.elementAt(1)?.second) {
                                C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                                C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad() } else throw Exception()
                                C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlLoad() } else throw Exception()
                                else -> throw Exception()
                            }
                        } catch (e: Exception) {
                            if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                            try {
                                when (apiPref.elementAt(2)?.second) {
                                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad() } else throw Exception()
                                    C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad() } else throw Exception()
                                    C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL; gqlLoad() } else throw Exception()
                                    else -> throw Exception()
                                }
                            } catch (e: Exception) {
                                if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                                listOf()
                            }
                        }
                    }
                    if (remote.isNotEmpty()) {
                        for (i in remote) {
                            val item = list.find { it.channelId == i.channelId }
                            if (item == null) {
                                list.add(i)
                            }
                        }
                    }
                    list.sortByDescending { it.viewerCount }
                    list
                }
            } catch (e: Exception) {
                if (checkIntegrity && e.message == "failed integrity check") return LoadResult.Error(e)
                listOf()
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

    private suspend fun helixLoad(): List<Stream> {
        val get = helixApi.getFollowedStreams(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            userId = userId,
            limit = 100,
            offset = offset
        )
        val list = mutableListOf<Stream>()
        get.data.let { list.addAll(it) }
        val ids = list.mapNotNull { it.channelId }
        if (ids.isNotEmpty()) {
            val users = helixApi.getUsers(clientId = helixClientId, token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, ids = ids).data
            for (i in users) {
                val item = list.find { it.channelId == i.channelId }
                if (item != null) {
                    item.profileImageUrl = i.profileImageUrl
                }
            }
        }
        offset = get.cursor
        return list
    }

    private suspend fun gqlQueryLoad(): List<Stream> {
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQueryUserFollowedStreams(
            headers = gqlHeaders,
            query = context.resources.openRawResource(R.raw.userfollowedstreams).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("id", userId)
                addProperty("first", 100)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlLoad(): List<Stream> {
        val get = gqlApi.loadFollowedStreams(gqlHeaders, 100, offset)
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlQueryLocal(ids: List<String>): List<Stream> {
        val streams = mutableListOf<Stream>()
        for (localIds in ids.chunked(100)) {
            val context = XtraApp.INSTANCE.applicationContext
            val get = gqlApi.loadQueryUsersStream(
                headers = gqlHeaders,
                query = context.resources.openRawResource(R.raw.usersstream).bufferedReader().use { it.readText() },
                variables = JsonObject().apply {
                    val idArray = JsonArray()
                    localIds.forEach {
                        idArray.add(it)
                    }
                    add("id", idArray)
                }).data
            streams.addAll(get)
        }
        return streams
    }

    private suspend fun helixLocal(ids: List<String>): List<Stream> {
        val streams = mutableListOf<Stream>()
        for (localIds in ids.chunked(100)) {
            val get = helixApi.getStreams(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = localIds
            ).data
            for (i in get) {
                if (i.viewerCount != null) {
                    streams.add(i)
                }
            }
        }
        if (streams.isNotEmpty()) {
            val userIds = streams.mapNotNull { it.channelId }
            for (streamIds in userIds.chunked(100)) {
                val users = helixApi.getUsers(clientId = helixClientId, token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) }, ids = streamIds).data
                for (i in users) {
                    val item = streams.find { it.channelId == i.channelId }
                    if (item != null) {
                        item.profileImageUrl = i.profileImageUrl
                    }
                }
            }
        }
        return streams
    }

    override fun getRefreshKey(state: PagingState<Int, Stream>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}