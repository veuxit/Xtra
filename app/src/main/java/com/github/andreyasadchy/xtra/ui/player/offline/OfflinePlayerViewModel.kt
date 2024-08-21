package com.github.andreyasadchy.xtra.ui.player.offline

import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    okHttpClient: OkHttpClient,
    private val offlineRepository: OfflineRepository) : PlayerViewModel(repository, localFollowsChannel, okHttpClient) {

    fun savePosition(id: Int, position: Long) {
        if (loaded.value) {
            offlineRepository.updateVideoPosition(id, position)
        }
    }
}
