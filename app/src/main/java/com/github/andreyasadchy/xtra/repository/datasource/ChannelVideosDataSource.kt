package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.BroadcastTypeEnum
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.model.ui.VideoPeriodEnum
import com.github.andreyasadchy.xtra.model.ui.VideoSortEnum
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.util.C

class ChannelVideosDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val helixHeaders: Map<String, String>,
    private val helixPeriod: VideoPeriodEnum,
    private val helixBroadcastTypes: BroadcastTypeEnum,
    private val helixSort: VideoSortEnum,
    private val helixApi: HelixApi,
    private val gqlHeaders: Map<String, String>,
    private val gqlQueryType: BroadcastType?,
    private val gqlQuerySort: VideoSort?,
    private val gqlType: String?,
    private val gqlSort: String?,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Video>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return try {
            val response = try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> if (helixPeriod == VideoPeriodEnum.ALL) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                    C.GQL -> if (helixPeriod == VideoPeriodEnum.ALL) { api = C.GQL; gqlLoad(params) } else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> if (helixPeriod == VideoPeriodEnum.ALL) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                        C.GQL -> if (helixPeriod == VideoPeriodEnum.ALL) { api = C.GQL; gqlLoad(params) } else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    try {
                        when (apiPref.elementAt(2)?.second) {
                            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> if (helixPeriod == VideoPeriodEnum.ALL) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                            C.GQL -> if (helixPeriod == VideoPeriodEnum.ALL) { api = C.GQL; gqlLoad(params) } else throw Exception()
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

    private suspend fun helixLoad(params: LoadParams<Int>): List<Video> {
        val response = helixApi.getVideos(
            headers = helixHeaders,
            channelId = channelId,
            period = helixPeriod,
            broadcastType = helixBroadcastTypes,
            sort = helixSort,
            limit = params.loadSize,
            offset = offset
        )
        val list = response.data.map {
            Video(
                id = it.id,
                channelId = channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                title = it.title,
                viewCount = it.viewCount,
                uploadDate = it.uploadDate,
                duration = it.duration,
                thumbnailUrl = it.thumbnailUrl,
            )
        }
        offset = response.pagination?.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Video> {
        val response = gqlApi.loadQueryUserVideos(
            headers = gqlHeaders,
            id = if (!channelId.isNullOrBlank()) channelId else null,
            login = if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null,
            sort = gqlQuerySort?.toString(),
            types = gqlQueryType?.let { listOf(it.toString()) },
            first = params.loadSize,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user!!
        val items = data.videos!!.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Video(
                    id = it.id,
                    channelId = channelId,
                    channelLogin = data.login,
                    channelName = data.displayName,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    type = it.broadcastType?.toString(),
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt?.toString(),
                    duration = it.lengthSeconds?.toString(),
                    thumbnailUrl = it.previewThumbnailURL,
                    profileImageUrl = data.profileImageURL,
                    tags = it.contentTags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    },
                    animatedPreviewURL =  it.animatedPreviewURL
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.videos.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Video> {
        val response = gqlApi.loadChannelVideos(gqlHeaders, channelLogin, gqlType, gqlSort, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user
        val items = data.videos!!.edges
        val list = items.map { item ->
            item.node.let {
                Video(
                    id = it.id,
                    channelId = it.owner?.id,
                    channelLogin = it.owner?.login,
                    channelName = it.owner?.displayName,
                    gameId = it.game?.id,
                    gameName = it.game?.displayName,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.publishedAt,
                    duration = it.lengthSeconds?.toString(),
                    thumbnailUrl = it.previewThumbnailURL,
                    profileImageUrl = it.owner?.profileImageURL,
                    tags = it.contentTags?.map { tag ->
                        Tag(
                            id = tag.id,
                            name = tag.localizedName
                        )
                    },
                    animatedPreviewURL =  it.animatedPreviewURL
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        nextPage = data.videos.pageInfo?.hasNextPage ?: true
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
