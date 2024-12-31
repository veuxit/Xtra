package com.github.andreyasadchy.xtra.ui.videos.channel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.SortChannel
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.SortChannelRepository
import com.github.andreyasadchy.xtra.repository.datasource.ChannelVideosDataSource
import com.github.andreyasadchy.xtra.type.BroadcastType
import com.github.andreyasadchy.xtra.type.VideoSort
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosViewModel
import com.github.andreyasadchy.xtra.ui.videos.VideosSortDialog
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class ChannelVideosViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    repository: ApiRepository,
    playerRepository: PlayerRepository,
    bookmarksRepository: BookmarksRepository,
    okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
    private val graphQLRepository: GraphQLRepository,
    private val helix: HelixApi,
    private val apolloClient: ApolloClient,
    private val sortChannelRepository: SortChannelRepository,
) : BaseVideosViewModel(playerRepository, bookmarksRepository, repository, okHttpClient) {

    private val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val sort: String
        get() = filter.value?.sort ?: VideosSortDialog.SORT_TIME
    val period: String
        get() = filter.value?.period ?: VideosSortDialog.PERIOD_ALL
    val type: String
        get() = filter.value?.type ?: VideosSortDialog.VIDEO_TYPE_ALL
    val saveSort: Boolean
        get() = filter.value?.saveSort == true

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest { filter ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            ChannelVideosDataSource(
                channelId = args.channelId,
                channelLogin = args.channelLogin,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixPeriod = when (period) {
                    VideosSortDialog.PERIOD_DAY -> "day"
                    VideosSortDialog.PERIOD_WEEK -> "week"
                    VideosSortDialog.PERIOD_MONTH -> "month"
                    VideosSortDialog.PERIOD_ALL -> "all"
                    else -> "all"
                },
                helixBroadcastTypes = when (type) {
                    VideosSortDialog.VIDEO_TYPE_ALL -> "all"
                    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> "archive"
                    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> "highlight"
                    VideosSortDialog.VIDEO_TYPE_UPLOAD -> "upload"
                    else -> "all"
                },
                helixSort = when (sort) {
                    VideosSortDialog.SORT_TIME -> "time"
                    VideosSortDialog.SORT_VIEWS -> "views"
                    else -> "time"
                },
                helixApi = helix,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                gqlQueryType = when (type) {
                    VideosSortDialog.VIDEO_TYPE_ALL -> null
                    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> BroadcastType.ARCHIVE
                    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> BroadcastType.HIGHLIGHT
                    VideosSortDialog.VIDEO_TYPE_UPLOAD -> BroadcastType.UPLOAD
                    else -> null
                },
                gqlQuerySort = when (sort) {
                    VideosSortDialog.SORT_TIME -> VideoSort.TIME
                    VideosSortDialog.SORT_VIEWS -> VideoSort.VIEWS
                    else -> VideoSort.TIME
                },
                gqlType = when (type) {
                    VideosSortDialog.VIDEO_TYPE_ALL -> null
                    VideosSortDialog.VIDEO_TYPE_ARCHIVE -> "ARCHIVE"
                    VideosSortDialog.VIDEO_TYPE_HIGHLIGHT -> "HIGHLIGHT"
                    VideosSortDialog.VIDEO_TYPE_UPLOAD -> "UPLOAD"
                    else -> null
                },
                gqlSort = when (sort) {
                    VideosSortDialog.SORT_TIME -> "TIME"
                    VideosSortDialog.SORT_VIEWS -> "VIEWS"
                    else -> "TIME"
                },
                gqlApi = graphQLRepository,
                apolloClient = apolloClient,
                checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                apiPref = applicationContext.prefs().getString(C.API_PREFS_CHANNEL_VIDEOS, null)?.split(',') ?: TwitchApiHelper.channelVideosApiDefaults
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getSortChannel(id: String): SortChannel? {
        return sortChannelRepository.getById(id)
    }

    suspend fun saveSortChannel(item: SortChannel) {
        sortChannelRepository.save(item)
    }

    fun setFilter(sort: String?, type: String?, saveSort: Boolean?) {
        filter.value = Filter(sort, null, type, saveSort)
    }

    class Filter(
        val sort: String?,
        val period: String?,
        val type: String?,
        val saveSort: Boolean?,
    )
}
