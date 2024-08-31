package com.github.andreyasadchy.xtra.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C

class SearchVideosDataSource(
    private val query: String,
    private val gqlHeaders: Map<String, String>,
    private val gqlApi: GraphQLRepository,
    private val checkIntegrity: Boolean,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?) : PagingSource<Int, Video>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return try {
            val response = if (query.isBlank()) listOf() else try {
                when (apiPref?.elementAt(0)?.second) {
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad() }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                if (e.message == "failed integrity check") return LoadResult.Error(e)
                try {
                    when (apiPref?.elementAt(1)?.second) {
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> { api = C.GQL; gqlLoad() }
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
                nextKey = if (!offset.isNullOrBlank() && (api != C.GQL_QUERY || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Video> {
        val response = gqlApi.loadQuerySearchVideos(
            headers = gqlHeaders,
            query = query,
            first = params.loadSize,
            after = offset
        )
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.searchFor!!.videos!!
        val list = data.items!!.map {
            Video(
                id = it.id,
                channelId = it.owner?.id,
                channelLogin = it.owner?.login,
                channelName = it.owner?.displayName,
                type = it.broadcastType?.toString(),
                title = it.title,
                viewCount = it.viewCount,
                uploadDate = it.createdAt?.toString(),
                duration = it.lengthSeconds?.toString(),
                thumbnailUrl = it.previewThumbnailURL,
                gameId = it.game?.id,
                gameSlug = it.game?.slug,
                gameName = it.game?.displayName,
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
        offset = data.cursor
        nextPage = data.pageInfo?.hasNextPage ?: true
        return list
    }

    private suspend fun gqlLoad(): List<Video> {
        val response = gqlApi.loadSearchVideos(gqlHeaders, query, offset)
        if (checkIntegrity) {
            response.errors?.find { it.message == "failed integrity check" }?.let { throw Exception(it.message) }
        }
        val data = response.data!!.searchFor.videos
        val list = data.edges.map { item ->
            item.item.let {
                Video(
                    id = it.id,
                    channelId = it.owner?.id,
                    channelLogin = it.owner?.login,
                    channelName = it.owner?.displayName,
                    title = it.title,
                    viewCount = it.viewCount,
                    uploadDate = it.createdAt,
                    duration = it.lengthSeconds?.toString(),
                    thumbnailUrl = it.previewThumbnailURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                )
            }
        }
        offset = data.cursor
        return list
    }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
