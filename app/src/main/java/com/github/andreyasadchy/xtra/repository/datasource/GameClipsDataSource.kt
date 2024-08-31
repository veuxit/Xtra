package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.ClipsPeriod
import com.github.andreyasadchy.xtra.type.Language
import com.github.andreyasadchy.xtra.util.C

class GameClipsDataSource(
    private val gameId: String?,
    private val gameSlug: String?,
    private val gameName: String?,
    private val helixHeaders: Map<String, String>,
    private val startedAt: String?,
    private val endedAt: String?,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlQueryLanguages: List<Language>?,
    private val gqlQueryPeriod: ClipsPeriod?,
    private val gqlPeriod: String?,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Clip>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Clip> {
        return try {
            val response = try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && gqlQueryLanguages.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> if (gqlQueryLanguages.isNullOrEmpty()) { api = C.GQL; gqlLoad(params) } else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && gqlQueryLanguages.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> if (gqlQueryLanguages.isNullOrEmpty()) { api = C.GQL; gqlLoad(params) } else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.elementAt(2)?.second) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && gqlQueryLanguages.isNullOrEmpty()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                            C.GQL -> if (gqlQueryLanguages.isNullOrEmpty()) { api = C.GQL; gqlLoad(params) } else throw Exception()
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
            gameId = gameId,
            startedAt = startedAt,
            endedAt = endedAt,
            limit = params.loadSize,
            cursor = offset
        )
        val users = response.data.mapNotNull { it.channelId }.let {
            helixApi.getUsers(
                headers = helixHeaders,
                ids = it
            ).data
        }
        val list = response.data.map {
            val user = it.channelId?.let { id ->
                users.find { user -> user.channelId == id }
            }
            Clip(
                id = it.id,
                channelId = it.channelId,
                channelLogin = user?.channelLogin,
                channelName = it.channelName,
                videoId = it.videoId,
                vodOffset = it.vodOffset,
                gameId = gameId,
                gameSlug = gameSlug,
                gameName = gameName,
                title = it.title,
                viewCount = it.viewCount,
                uploadDate = it.createdAt,
                duration = it.duration,
                thumbnailUrl = it.thumbnailUrl,
                profileImageUrl = user?.profileImageUrl,
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Clip> {
        val response = gqlApi.loadQueryGameClips(
            headers = gqlHeaders,
            id = if (!gameId.isNullOrBlank()) gameId else null,
            slug = if (gameId.isNullOrBlank() && !gameSlug.isNullOrBlank()) gameSlug else null,
            name = if (gameId.isNullOrBlank() && gameSlug.isNullOrBlank() && !gameName.isNullOrBlank()) gameName else null,
            languages = gqlQueryLanguages?.map { it.toString() },
            sort = gqlQueryPeriod?.toString(),
            first = params.loadSize,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.game!!.clips!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Clip(
                    id = it.slug,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    videoId = it.video?.id,
                    vodOffset = it.videoOffsetSeconds,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt?.toString(),
                    duration = it.durationSeconds?.toDouble(),
                    thumbnailUrl = it.thumbnailURL,
                    profileImageUrl = it.broadcaster?.profileImageURL,
                    videoAnimatedPreviewURL = it.video?.animatedPreviewURL
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Clip> {
        val response = gqlApi.loadGameClips(gqlHeaders, gameSlug, gqlPeriod, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.game.clips
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                Clip(
                    id = it.slug,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    gameId = gameId,
                    gameSlug = gameSlug,
                    gameName = gameName,
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
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Clip>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
