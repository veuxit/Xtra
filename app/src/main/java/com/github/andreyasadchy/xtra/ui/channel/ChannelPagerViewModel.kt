package com.github.andreyasadchy.xtra.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.ApolloClient
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.Notification
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationsRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
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

@HiltViewModel
class ChannelPagerViewModel @Inject constructor(
    private val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val shownNotificationsRepository: ShownNotificationsRepository,
    private val notificationsRepository: NotificationsRepository,
    private val apolloClient: ApolloClient,
    private val helixApi: HelixApi,
    private val okHttpClient: OkHttpClient,
    savedStateHandle: SavedStateHandle) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    private val args = ChannelPagerFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private val _notificationsEnabled = MutableStateFlow<Boolean?>(null)
    val notificationsEnabled: StateFlow<Boolean?> = _notificationsEnabled
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
                    } catch (e: Exception) {}
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

    fun notificationsEnabled(channelId: String) {
        if (_notificationsEnabled.value == null) {
            viewModelScope.launch {
                _notificationsEnabled.value = notificationsRepository.getByUserId(channelId) != null
            }
        }
    }

    fun enableNotifications(channelId: String) {
        viewModelScope.launch {
            notificationsRepository.saveUser(Notification(channelId))
            _notificationsEnabled.value = true
        }
    }

    fun disableNotifications(channelId: String) {
        viewModelScope.launch {
            notificationsRepository.deleteUser(Notification(channelId))
            _notificationsEnabled.value = false
        }
    }

    fun updateNotifications(gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        viewModelScope.launch {
            shownNotificationsRepository.getNewStreams(notificationsRepository, gqlHeaders, apolloClient, helixHeaders, helixApi)
        }
    }

    fun isFollowingChannel(helixHeaders: Map<String, String>, account: Account, gqlHeaders: Map<String, String>, setting: Int, channelId: String?, channelLogin: String?) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    val isFollowing = if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        if ((!helixHeaders[C.HEADER_CLIENT_ID].isNullOrBlank() && !helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && !account.id.isNullOrBlank() && !channelId.isNullOrBlank() && account.id != channelId) ||
                            (!account.login.isNullOrBlank() && !channelLogin.isNullOrBlank() && account.login != channelLogin)) {
                            repository.loadUserFollowing(helixHeaders, channelId, account.id, gqlHeaders, channelLogin)
                        } else false
                    } else {
                        channelId?.let {
                            localFollowsChannel.getFollowByUserId(it)
                        } != null
                    }
                    _isFollowing.value = isFollowing
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(filesDir: String, gqlHeaders: Map<String, String>, setting: Int, userId: String?, userLogin: String?, userName: String?, channelLogo: String?) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.followUser(gqlHeaders, userId)
                    if (!errorMessage.isNullOrBlank()) {
                        if (errorMessage == "failed integrity check" && integrity.value == null) {
                            integrity.value = "follow"
                        } else {
                            follow.value = Pair(true, errorMessage)
                        }
                    } else {
                        _isFollowing.value = true
                        follow.value = Pair(true, errorMessage)
                    }
                } else {
                    if (!userId.isNullOrBlank()) {
                        val downloadedLogo = channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                            File(filesDir, "profile_pics").mkdir()
                            val path = filesDir + File.separator + "profile_pics" + File.separator + userId
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
                        localFollowsChannel.saveFollow(LocalFollowChannel(userId, userLogin, userName, downloadedLogo))
                        _isFollowing.value = true
                        follow.value = Pair(true, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowChannel(gqlHeaders: Map<String, String>, setting: Int, userId: String?) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val errorMessage = repository.unfollowUser(gqlHeaders, userId)
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
                    if (userId != null) {
                        localFollowsChannel.getFollowByUserId(userId)?.let { localFollowsChannel.deleteFollow(it) }
                        _isFollowing.value = false
                        follow.value = Pair(false, null)
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun updateLocalUser(filesDir: String, user: User) {
        if (!updatedLocalUser) {
            updatedLocalUser = true
            user.channelId.takeIf { !it.isNullOrBlank()}?.let { userId ->
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
