package com.github.andreyasadchy.xtra.ui.follow.channels

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.apollographql.apollo.ApolloClient
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.SortChannel
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.SortChannelRepository
import com.github.andreyasadchy.xtra.repository.datasource.FollowedChannelsDataSource
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class FollowedChannelsViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helix: HelixApi,
    private val apolloClient: ApolloClient,
    private val sortChannelRepository: SortChannelRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val okHttpClient: OkHttpClient) : ViewModel() {

    private val _sortText = MutableStateFlow<CharSequence?>(null)
    val sortText: StateFlow<CharSequence?> = _sortText
    private val filter = MutableStateFlow(setUser())

    val sort: FollowSortEnum
        get() = filter.value.sort
    val order: FollowOrderEnum
        get() = filter.value.order

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest { filter ->
        Pager(
            PagingConfig(pageSize = 15, prefetchDistance = 5, initialLoadSize = 15)
        ) {
            FollowedChannelsDataSource(
                localFollowsChannel = localFollowsChannel,
                offlineRepository = offlineRepository,
                bookmarksRepository = bookmarksRepository,
                okHttpClient = okHttpClient,
                coroutineScope = viewModelScope,
                filesDir = applicationContext.filesDir.path,
                userId = Account.get(applicationContext).id,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixApi = helix,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
                gqlApi = graphQLRepository,
                apolloClient = apolloClient,
                checkIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false) && applicationContext.prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true),
                apiPref = TwitchApiHelper.listFromPrefs(applicationContext.prefs().getString(C.API_PREF_FOLLOWED_CHANNELS, ""), TwitchApiHelper.followedChannelsApiDefaults),
                sort = filter.sort,
                order = filter.order
            )
        }.flow
    }.cachedIn(viewModelScope)

    private fun setUser(): Filter {
        val sortValues = runBlocking { sortChannelRepository.getById("followed_channels") }
        _sortText.value = ContextCompat.getString(applicationContext, R.string.sort_and_order).format(
            when (sortValues?.videoSort) {
                FollowSortEnum.FOLLOWED_AT.value -> ContextCompat.getString(applicationContext, R.string.time_followed)
                FollowSortEnum.ALPHABETICALLY.value -> ContextCompat.getString(applicationContext, R.string.alphabetically)
                else -> ContextCompat.getString(applicationContext, R.string.last_broadcast)
            },
            when (sortValues?.videoType) {
                FollowOrderEnum.ASC.value -> ContextCompat.getString(applicationContext, R.string.ascending)
                else -> ContextCompat.getString(applicationContext, R.string.descending)
            }
        )
        return Filter(
            sort = when (sortValues?.videoSort) {
                FollowSortEnum.FOLLOWED_AT.value -> FollowSortEnum.FOLLOWED_AT
                FollowSortEnum.ALPHABETICALLY.value -> FollowSortEnum.ALPHABETICALLY
                else -> FollowSortEnum.LAST_BROADCAST
            },
            order = when (sortValues?.videoType) {
                FollowOrderEnum.ASC.value -> FollowOrderEnum.ASC
                else -> FollowOrderEnum.DESC
            }
        )
    }

    fun filter(sort: FollowSortEnum, order: FollowOrderEnum, text: CharSequence, saveDefault: Boolean) {
        filter.value = filter.value.copy(sort = sort, order = order)
        _sortText.value = text
        if (saveDefault) {
            viewModelScope.launch {
                val sortDefaults = sortChannelRepository.getById("followed_channels")
                (sortDefaults?.apply {
                    videoSort = sort.value
                    videoType = order.value
                } ?: SortChannel(
                    id = "followed_channels",
                    videoSort = sort.value,
                    videoType = order.value
                )).let { sortChannelRepository.save(it) }
            }
        }
        if (saveDefault != applicationContext.prefs().getBoolean(C.SORT_DEFAULT_FOLLOWED_CHANNELS, false)) {
            applicationContext.prefs().edit { putBoolean(C.SORT_DEFAULT_FOLLOWED_CHANNELS, saveDefault) }
        }
    }

    private data class Filter(
        val sort: FollowSortEnum = FollowSortEnum.LAST_BROADCAST,
        val order: FollowOrderEnum = FollowOrderEnum.DESC)
}
