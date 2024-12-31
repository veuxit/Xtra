package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.github.andreyasadchy.xtra.UserClipsQuery
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.ClipsPeriod
import com.github.andreyasadchy.xtra.util.C

class ChannelClipsDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val helixHeaders: Map<String, String>,
    private val startedAt: String?,
    private val endedAt: String?,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlQueryPeriod: ClipsPeriod?,
    private val gqlPeriod: String?,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: List<String>,
) : PagingSource<Int, Clip>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Clip> {
        return try {
            val response = try {
                when (apiPref.getOrNull(0)) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                    C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.getOrNull(1)) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                        C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.getOrNull(2)) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL -> { api = C.GQL; gqlQueryLoad(params) }
                            C.GQL_PERSISTED_QUERY -> { api = C.GQL_PERSISTED_QUERY; gqlLoad(params) }
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        if (e.message == "failed integrity check") return LoadResult.Error(e)
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

    private suspend fun helixLoad(params: LoadParams<Int>): List<Clip> {
        val response = helixApi.getClips(
            headers = helixHeaders,
            channelId = channelId,
            startedAt = startedAt,
            endedAt = endedAt,
            limit = params.loadSize,
            cursor = offset
        )
        val games = response.data.mapNotNull { it.gameId }.let {
            helixApi.getGames(
                headers = helixHeaders,
                ids = it
            ).data
        }
        val list = response.data.map {
            Clip(
                id = it.id,
                channelId = channelId,
                channelLogin = channelLogin,
                channelName = it.channelName,
                videoId = it.videoId,
                vodOffset = it.vodOffset,
                gameId = it.gameId,
                gameName = it.gameId?.let { id ->
                    games.find { game -> game.id == id }?.name
                },
                title = it.title,
                viewCount = it.viewCount,
                uploadDate = it.createdAt,
                duration = it.duration,
                thumbnailUrl = it.thumbnailUrl,
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Clip> {
        val response = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(UserClipsQuery(
            id = if (!channelId.isNullOrBlank()) Optional.Present(channelId) else Optional.Absent,
            login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) Optional.Present(channelLogin) else Optional.Absent,
            sort = Optional.Present(gqlQueryPeriod),
            first = Optional.Present(params.loadSize),
            after = Optional.Present(offset)
        )).execute()
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user!!
        val items = data.clips!!.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Clip(
                    id = it.slug,
                    channelId = channelId,
                    channelLogin = data.login,
                    channelName = data.displayName,
                    videoId = it.video?.id,
                    vodOffset = it.videoOffsetSeconds,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt?.toString(),
                    duration = it.durationSeconds?.toDouble(),
                    thumbnailUrl = it.thumbnailURL,
                    profileImageUrl = data.profileImageURL,
                    videoAnimatedPreviewURL = it.video?.animatedPreviewURL
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.clips.pageInfo?.hasNextPage != false
        return list
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Clip> {
        val response = gqlApi.loadChannelClips(gqlHeaders, channelLogin, gqlPeriod, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user
        val items = data.clips!!.edges
        val list = items.map { item ->
            item.node.let {
                Clip(
                    id = it.slug,
                    channelId = channelId,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    gameId = it.game?.id,
                    gameName = it.game?.name,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt,
                    duration = it.durationSeconds,
                    thumbnailUrl = it.thumbnailURL,
                    profileImageUrl = it.broadcaster?.profileImageURL,
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        nextPage = data.clips.pageInfo?.hasNextPage != false
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Clip>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
