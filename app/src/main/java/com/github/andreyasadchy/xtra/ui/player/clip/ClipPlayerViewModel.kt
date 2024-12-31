package com.github.andreyasadchy.xtra.ui.player.clip

import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class ClipPlayerViewModel @Inject constructor(
    repository: ApiRepository,
    localFollowsChannel: LocalFollowChannelRepository,
    shownNotificationsRepository: ShownNotificationsRepository,
    notificationUsersRepository: NotificationUsersRepository,
    okHttpClient: OkHttpClient,
    private val playerRepository: PlayerRepository,
) : PlayerViewModel(repository, localFollowsChannel, shownNotificationsRepository, notificationUsersRepository, okHttpClient) {

    val result = MutableStateFlow<Map<String, String>?>(null)

    fun load(gqlHeaders: Map<String, String>, id: String?) {
        if (result.value == null) {
            viewModelScope.launch {
                try {
                    result.value = playerRepository.loadClipUrls(gqlHeaders, id) ?: emptyMap()
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    } else {
                        result.value = emptyMap()
                    }
                }
            }
        }
    }
}
