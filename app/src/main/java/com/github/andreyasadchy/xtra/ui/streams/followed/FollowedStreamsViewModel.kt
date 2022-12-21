package com.github.andreyasadchy.xtra.ui.streams.followed

import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.Listing
import com.github.andreyasadchy.xtra.ui.common.PagedListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FollowedStreamsViewModel @Inject constructor(
        private val repository: ApiRepository) : PagedListViewModel<Stream>() {

    private val filter = MutableLiveData<Filter>()
    override val result: LiveData<Listing<Stream>> = Transformations.map(filter) {
        repository.loadFollowedStreams(it.account.id, it.helixClientId, it.account.helixToken, it.gqlClientId, it.account.gqlToken, it.apiPref, it.thumbnailsEnabled, viewModelScope)
    }

    fun loadStreams(account: Account, helixClientId: String? = null, gqlClientId: String? = null, apiPref: ArrayList<Pair<Long?, String?>?>, thumbnailsEnabled: Boolean) {
        Filter(account, helixClientId, gqlClientId, apiPref, thumbnailsEnabled).let {
            if (filter.value != it) {
                filter.value = it
            }
        }
    }

    private data class Filter(
        val account: Account,
        val helixClientId: String?,
        val gqlClientId: String?,
        val apiPref: ArrayList<Pair<Long?, String?>?>,
        val thumbnailsEnabled: Boolean)
}