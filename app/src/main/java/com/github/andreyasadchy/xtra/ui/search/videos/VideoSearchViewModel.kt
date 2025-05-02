package com.github.andreyasadchy.xtra.ui.search.videos

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.datasource.SearchVideosDataSource
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.concurrent.ExecutorService
import javax.inject.Inject

@HiltViewModel
class VideoSearchViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    playerRepository: PlayerRepository,
    bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    helixRepository: HelixRepository,
    cronetEngine: CronetEngine?,
    cronetExecutor: ExecutorService,
    okHttpClient: OkHttpClient,
) : BaseVideosViewModel(playerRepository, bookmarksRepository, graphQLRepository, helixRepository, cronetEngine, cronetExecutor, okHttpClient) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = _query.flatMapLatest { query ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            SearchVideosDataSource(
                query = query,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                graphQLRepository = graphQLRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                apiPref = applicationContext.prefs().getString(C.API_PREFS_SEARCH_VIDEOS, null)?.split(',') ?: TwitchApiHelper.searchVideosApiDefaults,
                useCronet = applicationContext.prefs().getBoolean(C.USE_CRONET, false),
            )
        }.flow
    }.cachedIn(viewModelScope)

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
        }
    }
}