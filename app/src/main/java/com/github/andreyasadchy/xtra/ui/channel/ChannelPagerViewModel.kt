package com.github.andreyasadchy.xtra.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.ApolloClient
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import javax.inject.Inject
import kotlin.text.isNullOrBlank

@HiltViewModel
class ChannelPagerViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val shownNotificationsRepository: ShownNotificationsRepository,
    private val notificationUsersRepository: NotificationUsersRepository,
    private val apolloClient: ApolloClient,
    private val helixApi: HelixApi,
    private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    private val args = ChannelPagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private val _notificationsEnabled = MutableStateFlow<Boolean?>(null)
    val notificationsEnabled: StateFlow<Boolean?> = _notificationsEnabled
    val notifications = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private val _isFollowing = MutableStateFlow<Boolean?>(null)
    val isFollowing: StateFlow<Boolean?> = _isFollowing
    val follow = MutableStateFlow<Pair<Boolean, String?>?>(null)
    private var updatedLocalUser = false

    private val _stream = MutableStateFlow<Stream?>(null)
    val stream: StateFlow<Stream?> = _stream
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    fun loadStream(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (_stream.value == null) {
            viewModelScope.launch {
                try {
                    _stream.value = repository.loadUserChannelPage(args.channelId, args.channelLogin, helixHeaders, gqlHeaders, checkIntegrity)
                } catch (e: Exception) {

                }
            }
        }
    }

    fun loadUser(helixHeaders: Map<String, String>) {
        if (_user.value == null) {
            if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                viewModelScope.launch {
                    try {
                        _user.value = repository.loadUser(args.channelId, args.channelLogin, helixHeaders)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    fun retry(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (_stream.value == null) {
            loadStream(helixHeaders, gqlHeaders, checkIntegrity)
        } else {
            if (_stream.value?.user == null && _user.value == null) {
                loadUser(helixHeaders)
            }
        }
    }

    fun enableNotifications(gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String, notificationsEnabled: Boolean) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && _isFollowing.value == true && userId != channelId) {
                    val errorMessage = repository.toggleNotifications(gqlHeaders, channelId, false)
                    if (!errorMessage.isNullOrBlank()) {
                        if (errorMessage == "failed integrity check" && integrity.value == null) {
                            integrity.value = "enableNotifications"
                        } else {
                            notifications.value = Pair(true, errorMessage)
                        }
                    } else {
                        _notificationsEnabled.value = true
                        notifications.value = Pair(true, errorMessage)
                        if (notificationsEnabled) {
                            _stream.value?.startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                            }
                        }
                    }
                } else {
                    notificationUsersRepository.saveUser(NotificationUser(channelId))
                    _notificationsEnabled.value = true
                    notifications.value = Pair(true, null)
                    if (notificationsEnabled) {
                        _stream.value?.startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                            shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun disableNotifications(gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && _isFollowing.value == true && userId != channelId) {
                    val errorMessage = repository.toggleNotifications(gqlHeaders, channelId, true)
                    if (!errorMessage.isNullOrBlank()) {
                        if (errorMessage == "failed integrity check" && integrity.value == null) {
                            integrity.value = "disableNotifications"
                        } else {
                            notifications.value = Pair(false, errorMessage)
                        }
                    } else {
                        _notificationsEnabled.value = false
                        notifications.value = Pair(false, errorMessage)
                    }
                } else {
                    notificationUsersRepository.deleteUser(NotificationUser(channelId))
                    _notificationsEnabled.value = false
                    notifications.value = Pair(false, null)
                }
            } catch (e: Exception) {

            }
        }
    }

    fun updateNotifications(gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        viewModelScope.launch {
            shownNotificationsRepository.getNewStreams(notificationUsersRepository, gqlHeaders, apolloClient, helixHeaders, helixApi)
        }
    }

    fun isFollowingChannel(helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?, channelLogin: String?) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!channelId.isNullOrBlank()) {
                        if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                            val response = repository.loadUserFollowing(helixHeaders, channelId, userId, gqlHeaders, channelLogin)
                            _isFollowing.value = response.first
                            _notificationsEnabled.value = if (response.first && response.second != null) {
                                response.second
                            } else {
                                notificationUsersRepository.getByUserId(channelId) != null
                            }
                        } else {
                            _isFollowing.value = localFollowsChannel.getFollowByUserId(channelId) != null
                            _notificationsEnabled.value = notificationUsersRepository.getByUserId(channelId) != null
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(filesDir: String, gqlHeaders: Map<String, String>, setting: Int, userId: String?, channelId: String?, channelLogin: String?, channelName: String?, channelLogo: String?, notificationsEnabled: Boolean) {
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
                            _notificationsEnabled.value = true
                            if (notificationsEnabled) {
                                _stream.value?.startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
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
                        _notificationsEnabled.value = true
                        if (notificationsEnabled) {
                            _stream.value?.startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
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
                            _notificationsEnabled.value = false
                        }
                    } else {
                        localFollowsChannel.getFollowByUserId(channelId)?.let { localFollowsChannel.deleteFollow(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                        notificationUsersRepository.deleteUser(NotificationUser(channelId))
                        _notificationsEnabled.value = false
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun updateLocalUser(filesDir: String, user: User) {
        if (!updatedLocalUser) {
            updatedLocalUser = true
            user.channelId.takeIf { !it.isNullOrBlank() }?.let { userId ->
                viewModelScope.launch {
                    val downloadedLogo = user.channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + userId
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                    if (response.isSuccessful) {
                                        File(path).sink().buffer().use { sink ->
                                            sink.writeAll(response.body.source())
                                        }
                                    }
                                }
                            } catch (e: Exception) {

                            }
                        }
                        path
                    }
                    localFollowsChannel.getFollowByUserId(userId)?.let {
                        localFollowsChannel.updateFollow(it.apply {
                            userLogin = user.channelLogin
                            userName = user.channelName
                            channelLogo = downloadedLogo
                        })
                    }
                    offlineRepository.getVideosByUserId(userId).forEach {
                        offlineRepository.updateVideo(it.apply {
                            channelLogin = user.channelLogin
                            channelName = user.channelName
                            channelLogo = downloadedLogo
                        })
                    }
                    bookmarksRepository.getBookmarksByUserId(userId).forEach {
                        bookmarksRepository.updateBookmark(it.apply {
                            userLogin = user.channelLogin
                            userName = user.channelName
                            userLogo = downloadedLogo
                        })
                    }
                }
            }
        }
    }
}
