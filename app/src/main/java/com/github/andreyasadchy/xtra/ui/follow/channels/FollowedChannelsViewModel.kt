package com.github.andreyasadchy.xtra.ui.follow.channels

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.SortChannel
import com.github.andreyasadchy.xtra.model.ui.FollowOrderEnum
import com.github.andreyasadchy.xtra.model.ui.FollowSortEnum
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.Listing
import com.github.andreyasadchy.xtra.repository.SortChannelRepository
import com.github.andreyasadchy.xtra.ui.common.PagedListViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class FollowedChannelsViewModel @Inject constructor(
    context: Application,
    private val repository: ApiRepository,
    private val sortChannelRepository: SortChannelRepository) : PagedListViewModel<User>() {

    private val _sortText = MutableLiveData<CharSequence>()
    val sortText: LiveData<CharSequence>
        get() = _sortText
    private val filter = MutableLiveData<Filter>()
    override val result: LiveData<Listing<User>> = Transformations.map(filter) {
        repository.loadFollowedChannels(it.account.id, it.helixClientId, it.account.helixToken, it.gqlClientId, it.account.gqlToken, it.apiPref, it.sort, it.order, viewModelScope)
    }
    val sort: FollowSortEnum
        get() = filter.value!!.sort
    val order: FollowOrderEnum
        get() = filter.value!!.order

    fun setUser(context: Context, account: Account, helixClientId: String?, gqlClientId: String?, apiPref: ArrayList<Pair<Long?, String?>?>) {
        if (filter.value == null) {
            val sortValues = runBlocking { sortChannelRepository.getById("followed_channels") }
            filter.value = Filter(
                account = account,
                helixClientId = helixClientId,
                gqlClientId = gqlClientId,
                apiPref = apiPref,
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
            _sortText.value = context.getString(R.string.sort_and_order,
                when (sortValues?.videoSort) {
                    FollowSortEnum.FOLLOWED_AT.value -> context.getString(R.string.time_followed)
                    FollowSortEnum.ALPHABETICALLY.value -> context.getString(R.string.alphabetically)
                    else -> context.getString(R.string.last_broadcast)
                },
                when (sortValues?.videoType) {
                    FollowOrderEnum.ASC.value -> context.getString(R.string.ascending)
                    else -> context.getString(R.string.descending)
                }
            )
        }
    }

    fun filter(sort: FollowSortEnum, order: FollowOrderEnum, text: CharSequence, saveDefault: Boolean) {
        filter.value = filter.value?.copy(sort = sort, order = order)
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
        val appContext = XtraApp.INSTANCE.applicationContext
        if (saveDefault != appContext.prefs().getBoolean(C.SORT_DEFAULT_FOLLOWED_CHANNELS, false)) {
            appContext.prefs().edit { putBoolean(C.SORT_DEFAULT_FOLLOWED_CHANNELS, saveDefault) }
        }
    }

    private data class Filter(
        val account: Account,
        val helixClientId: String?,
        val gqlClientId: String?,
        val apiPref: ArrayList<Pair<Long?, String?>?>,
        val sort: FollowSortEnum = FollowSortEnum.LAST_BROADCAST,
        val order: FollowOrderEnum = FollowOrderEnum.DESC)
}
