package com.github.andreyasadchy.xtra.ui.player.clip

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClipPlayerViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    private val graphQLRepository: GraphQLRepository) : PlayerViewModel(applicationContext, repository, localFollowsChannel) {

    var result = MutableLiveData<Map<String, String>>()

    fun load(gqlHeaders: Map<String, String>, id: String?) {
        if (result.value == null) {
            viewModelScope.launch {
                try {
                    graphQLRepository.loadClipUrls(gqlHeaders, id)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                    null
                }.let { result.postValue(it) }
            }
        }
    }
}
