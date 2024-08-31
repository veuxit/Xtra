package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.github.andreyasadchy.xtra.UserFollowedVideosQuery
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.util.C

class FollowedVideosDataSource(
    private val gqlHeaders: Map<String, String>,
    private val gqlQueryType: BroadcastType?,
    private val gqlQuerySort: VideoSort?,
    private val gqlApi: GraphQLRepository,
    private val apolloClient: ApolloClient,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Video>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return try {
            val response = try {
                when (apiPref.elementAt(0)?.second) {
                    C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                    C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && gqlQueryType == BroadcastType.ARCHIVE && gqlQuerySort == VideoSort.TIME) { api = C.GQL; gqlLoad(params) } else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.GQL_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) { api = C.GQL_QUERY; gqlQueryLoad(params) } else throw Exception()
                        C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && gqlQueryType == BroadcastType.ARCHIVE && gqlQuerySort == VideoSort.TIME) { api = C.GQL; gqlLoad(params) } else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") return LoadResult.Error(e)
                    listOf()
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

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Video> {
        val response = apolloClient.newBuilder().apply {
            gqlHeaders.entries.forEach { addHttpHeader(it.key, it.value) }
        }.build().query(UserFollowedVideosQuery(
            sort = Optional.Present(gqlQuerySort),
            type = Optional.Present(gqlQueryType?.let { listOf(it) }),
            first = Optional.Present(params.loadSize),
            after = Optional.Present(offset)
        )).execute()
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.user!!.followedVideos!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Video(
                    id = it.id,
                    channelId = it.owner?.id,
                    channelLogin = it.owner?.login,
                    channelName = it.owner?.displayName,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    type = it.broadcastType?.toString(),
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt?.toString(),
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
        offset = items.lastOrNull()?.cursor?.toString()
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Video> {
        val response = gqlApi.loadFollowedVideos(gqlHeaders, params.loadSize, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.currentUser.followedVideos
        val items = data.edges
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
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
