package com.github.andreyasadchy.xtra.ui.search.games

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.apollographql.apollo3.ApolloClient
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.datasource.SearchGamesDataSource
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
class GameSearchViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helix: HelixApi,
    private val apolloClient: ApolloClient) : ViewModel() {

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = query.flatMapLatest { query ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        ) {
            SearchGamesDataSource(
                query = query,
                helixClientId = applicationContext.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi"),
                helixToken = Account.get(applicationContext).helixToken,
                helixApi = helix,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                gqlApi = graphQLRepository,
                apolloClient = apolloClient,
                checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                apiPref = TwitchApiHelper.listFromPrefs(applicationContext.prefs().getString(C.API_PREF_SEARCH_GAMES, ""), TwitchApiHelper.searchGamesApiDefaults))
        }.flow
    }.cachedIn(viewModelScope)

    fun setQuery(newQuery: String) {
        if (query.value != newQuery) {
            query.value = newQuery
        }
    }
}