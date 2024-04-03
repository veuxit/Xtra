package com.github.andreyasadchy.xtra.ui.player.offline

import android.content.Context
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository) : PlayerViewModel(applicationContext, repository, localFollowsChannel) {

    fun savePosition(id: Int, position: Long) {
        if (loaded.value == true) {
            offlineRepository.updateVideoPosition(id, position)
        }
    }
}
