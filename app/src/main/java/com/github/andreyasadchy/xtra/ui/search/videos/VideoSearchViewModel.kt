package com.github.andreyasadchy.xtra.ui.search.videos

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
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
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class VideoSearchViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    playerRepository: PlayerRepository,
    bookmarksRepository: BookmarksRepository,
    repository: ApiRepository,
    private val graphQLRepository: GraphQLRepository, ) : BaseVideosViewModel(applicationContext, playerRepository, bookmarksRepository, repository) {

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = query.flatMapLatest { query ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            SearchVideosDataSource(
                query = query,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                gqlApi = graphQLRepository,
                checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                apiPref = TwitchApiHelper.listFromPrefs(applicationContext.prefs().getString(C.API_PREF_SEARCH_VIDEOS, ""), TwitchApiHelper.searchVideosApiDefaults))
        }.flow
    }.cachedIn(viewModelScope)

    fun setQuery(newQuery: String) {
        if (query.value != newQuery) {
            query.value = newQuery
        }
    }
}