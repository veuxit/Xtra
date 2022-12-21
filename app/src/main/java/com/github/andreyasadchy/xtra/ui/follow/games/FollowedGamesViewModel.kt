package com.github.andreyasadchy.xtra.ui.follow.games

import android.app.Application
import androidx.core.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.Listing
import com.github.andreyasadchy.xtra.ui.common.PagedListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FollowedGamesViewModel @Inject constructor(
        context: Application,
        private val repository: ApiRepository) : PagedListViewModel<Game>() {

    private val filter = MutableLiveData<Filter>()
    override val result: LiveData<Listing<Game>> = Transformations.map(filter) {
        repository.loadFollowedGames(it.gqlClientId, it.account.gqlToken, it.apiPref, viewModelScope)
    }

    fun setUser(account: Account, gqlClientId: String? = null, apiPref: ArrayList<Pair<Long?, String?>?>) {
        if (filter.value == null) {
            filter.value = Filter(account, gqlClientId, apiPref)
        }
    }

    private data class Filter(
        val account: Account,
        val gqlClientId: String?,
        val apiPref: ArrayList<Pair<Long?, String?>?>)
}
