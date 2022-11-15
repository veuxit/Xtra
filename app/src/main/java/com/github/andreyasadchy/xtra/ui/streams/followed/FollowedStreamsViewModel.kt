package com.github.andreyasadchy.xtra.ui.streams.followed

import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.User
import com.github.andreyasadchy.xtra.model.helix.stream.Stream
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
        repository.loadFollowedStreams(it.user.id, it.helixClientId, it.user.helixToken, it.gqlClientId, it.user.gqlToken, it.apiPref, it.thumbnailsEnabled, viewModelScope)
    }

    fun loadStreams(user: User, helixClientId: String? = null, gqlClientId: String? = null, apiPref: ArrayList<Pair<Long?, String?>?>, thumbnailsEnabled: Boolean) {
        Filter(user, helixClientId, gqlClientId, apiPref, thumbnailsEnabled).let {
            if (filter.value != it) {
                filter.value = it
            }
        }
    }

    private data class Filter(
        val user: User,
        val helixClientId: String?,
        val gqlClientId: String?,
        val apiPref: ArrayList<Pair<Long?, String?>?>,
        val thumbnailsEnabled: Boolean)
}