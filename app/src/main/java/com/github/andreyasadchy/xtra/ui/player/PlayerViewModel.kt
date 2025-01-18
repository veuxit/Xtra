package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File


abstract class PlayerViewModel(
    private val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val shownNotificationsRepository: ShownNotificationsRepository,
    private val notificationUsersRepository: NotificationUsersRepository,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    var started = false
    var background = false
    var pipMode = false
    var playerMode = PlaybackService.PLAYER_MODE_NORMAL
    val loaded = MutableStateFlow(false)
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)

    fun isFollowingChannel(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?, channelLogin: String?) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!channelId.isNullOrBlank()) {
                        if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                            val response = repository.loadUserFollowing(helixHeaders, channelId, userId, gqlHeaders, channelLogin)
                            _isFollowing.value = response.first
                        } else {
                            _isFollowing.value = localFollowsChannel.getFollowByUserId(channelId) != null
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(filesDir: String, gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, notificationsEnabled: Boolean, startedAt: String? = null) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = repository.followUser(gqlHeaders, channelId)
                        if (!errorMessage.isNullOrBlank()) {
                            if (errorMessage == "failed integrity check" && integrity.value == null) {
                                integrity.value = "follow"
                            } else {
                                follow.value = Pair(true, errorMessage)
                            }
                        } else {
                            _isFollowing.value = true
                            follow.value = Pair(true, errorMessage)
                            if (notificationsEnabled) {
                                startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                    shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                    } else {
                        val downloadedLogo = channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "profile_pics").mkdir()
                            val path = filesDir + File.separator + "profile_pics" + File.separator + channelId
                            viewModelScope.launch(Dispatchers.IO) {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
                                    }
                                }
                            }
                            path
                        }
                        localFollowsChannel.saveFollow(LocalFollowChannel(channelId, channelLogin, channelName, downloadedLogo))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                        notificationUsersRepository.saveUser(NotificationUser(channelId))
                        if (notificationsEnabled) {
                            startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowChannel(gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = repository.unfollowUser(gqlHeaders, channelId)
                        if (!errorMessage.isNullOrBlank()) {
                            if (errorMessage == "failed integrity check" && integrity.value == null) {
                                integrity.value = "unfollow"
                            } else {
                                follow.value = Pair(false, errorMessage)
                            }
                        } else {
                            _isFollowing.value = false
                            follow.value = Pair(false, errorMessage)
                        }
                    } else {
                        localFollowsChannel.getFollowByUserId(channelId)?.let { localFollowsChannel.deleteFollow(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                        notificationUsersRepository.deleteUser(NotificationUser(channelId))
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}