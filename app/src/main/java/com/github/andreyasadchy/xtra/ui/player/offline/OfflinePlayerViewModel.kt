package com.github.andreyasadchy.xtra.ui.player.offline

import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.OfflineVideo
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class OfflinePlayerViewModel @Inject constructor(
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    shownNotificationsRepository: ShownNotificationsRepository,
    notificationUsersRepository: NotificationUsersRepository,
    okHttpClient: OkHttpClient,
    private val offlineRepository: OfflineRepository) : PlayerViewModel(repository, localFollowsChannel, shownNotificationsRepository, notificationUsersRepository, okHttpClient) {

    val video = MutableSharedFlow<OfflineVideo?>()

    fun getVideo(id: Int) {
        viewModelScope.launch {
            video.emit(offlineRepository.getVideoById(id))
        }
    }

    fun savePosition(id: Int, position: Long) {
        if (loaded.value) {
            viewModelScope.launch {
                offlineRepository.updateVideoPosition(id, position)
            }
        }
    }
}
