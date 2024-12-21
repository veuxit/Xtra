package com.github.andreyasadchy.xtra.ui.streams.common

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.datasource.GameStreamsDataSource
import com.github.andreyasadchy.xtra.repository.datasource.StreamsDataSource
import com.github.andreyasadchy.xtra.type.StreamSort
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentArgs
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class StreamsViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helix: HelixApi,
    private val apolloClient: ApolloClient,
    savedStateHandle: SavedStateHandle) : ViewModel() {

    private val args = GamePagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val filter = MutableStateFlow<Filter?>(null)

    val sort: String
        get() = filter.value?.sort ?: StreamsSortDialog.SORT_VIEWERS

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest { filter ->
        Pager(
            if (applicationContext.prefs().getString(C.COMPACT_STREAMS, "disabled") == "all") {
                PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
            } else {
                PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
            }
        ) {
            if (args.gameId == null && args.gameSlug == null && args.gameName == null) {
                StreamsDataSource(
                    helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                    helixApi = helix,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                    tags = args.tags?.toList(),
                    gqlApi = graphQLRepository,
                    apolloClient = apolloClient,
                    checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                    apiPref = applicationContext.prefs().getString(C.API_PREFS_STREAMS, null)?.split(',') ?: TwitchApiHelper.streamsApiDefaults
                )
            } else {
                GameStreamsDataSource(
                    gameId = args.gameId,
                    gameSlug = args.gameSlug,
                    gameName = args.gameName,
                    helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                    helixApi = helix,
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                    gqlQuerySort = when (sort) {
                        StreamsSortDialog.SORT_VIEWERS -> StreamSort.VIEWER_COUNT
                        StreamsSortDialog.SORT_VIEWERS_ASC -> StreamSort.VIEWER_COUNT_ASC
                        else -> StreamSort.VIEWER_COUNT
                    },
                    gqlSort = when (sort) {
                        StreamsSortDialog.SORT_VIEWERS -> "VIEWER_COUNT"
                        StreamsSortDialog.SORT_VIEWERS_ASC -> "VIEWER_COUNT_ASC"
                        else -> "VIEWER_COUNT"
                    },
                    tags = args.tags?.toList(),
                    gqlApi = graphQLRepository,
                    apolloClient = apolloClient,
                    checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                    apiPref = applicationContext.prefs().getString(C.API_PREFS_GAME_STREAMS, null)?.split(',') ?: TwitchApiHelper.gameStreamsApiDefaults
                )
            }
        }.flow
    }.cachedIn(viewModelScope)

    fun setFilter(sort: String?) {
        filter.value = Filter(sort)
    }

    class Filter(
        val sort: String?,
    )
}
