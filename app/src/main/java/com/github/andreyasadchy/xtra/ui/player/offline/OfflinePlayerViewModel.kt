package com.github.andreyasadchy.xtra.ui.player.offline

import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository) : PlayerViewModel(repository, localFollowsChannel) {

    fun savePosition(id: Int, position: Long) {
        offlineRepository.updateVideoPosition(id, position)
    }
}
