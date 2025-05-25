package com.github.andreyasadchy.xtra.ui.channel

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.model.ShownNotification
import com.github.andreyasadchy.xtra.model.ui.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.NotificationUsersRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.repository.ShownNotificationsRepository
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.getByteArrayCronetCallback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.chromium.net.CronetEngine
import org.chromium.net.UrlResponseInfo
import org.chromium.net.apihelpers.RedirectHandlers
import org.chromium.net.apihelpers.UrlRequestCallbacks
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import kotlin.coroutines.suspendCoroutine

@HiltViewModel
class ChannelPagerViewModel @Inject constructor(
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val offlineRepository: OfflineRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val shownNotificationsRepository: ShownNotificationsRepository,
    private val notificationUsersRepository: NotificationUsersRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val cronetEngine: CronetEngine?,
    private val cronetExecutor: ExecutorService,
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

    fun loadStream(useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (_stream.value == null) {
            viewModelScope.launch {
                _stream.value = try {
                    val response = graphQLRepository.loadQueryUserChannelPage(useCronet, gqlHeaders, args.channelId, if (args.channelId.isNullOrBlank()) args.channelLogin else null)
                    if (enableIntegrity && integrity.value == null) {
                        response.errors?.find { it.message == "failed integrity check" }?.let {
                            integrity.value = "refresh"
                            return@launch
                        }
                    }
                    response.data!!.user?.let {
                        Stream(
                            id = it.stream?.id,
                            channelId = it.id,
                            channelLogin = it.login,
                            channelName = it.displayName,
                            gameId = it.stream?.game?.id,
                            gameSlug = it.stream?.game?.slug,
                            gameName = it.stream?.game?.displayName,
                            type = it.stream?.type,
                            title = it.stream?.title,
                            viewerCount = it.stream?.viewersCount,
                            startedAt = it.stream?.createdAt?.toString(),
                            thumbnailUrl = it.stream?.previewImageURL,
                            profileImageUrl = it.profileImageURL,
                            user = User(
                                channelId = it.id,
                                channelLogin = it.login,
                                channelName = it.displayName,
                                type = when {
                                    it.roles?.isStaff == true -> "staff"
                                    else -> null
                                },
                                broadcasterType = when {
                                    it.roles?.isPartner == true -> "partner"
                                    it.roles?.isAffiliate == true -> "affiliate"
                                    else -> null
                                },
                                profileImageUrl = it.profileImageURL,
                                createdAt = it.createdAt?.toString(),
                                followersCount = it.followers?.totalCount,
                                bannerImageURL = it.bannerImageURL,
                                lastBroadcast = it.lastBroadcast?.startedAt?.toString()
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                        try {
                            helixRepository.getStreams(
                                useCronet = useCronet,
                                headers = helixHeaders,
                                ids = args.channelId?.let { listOf(it) },
                                logins = if (args.channelId.isNullOrBlank()) args.channelLogin?.let { listOf(it) } else null
                            ).data.firstOrNull()?.let {
                                Stream(
                                    id = it.id,
                                    channelId = it.channelId,
                                    channelLogin = it.channelLogin,
                                    channelName = it.channelName,
                                    gameId = it.gameId,
                                    gameName = it.gameName,
                                    type = it.type,
                                    title = it.title,
                                    viewerCount = it.viewerCount,
                                    startedAt = it.startedAt,
                                    thumbnailUrl = it.thumbnailUrl,
                                    tags = it.tags
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }
            }
        }
    }

    fun loadUser(useCronet: Boolean, helixHeaders: Map<String, String>) {
        if (_user.value == null) {
            viewModelScope.launch {
                _user.value = if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    try {
                        helixRepository.getUsers(
                            useCronet = useCronet,
                            headers = helixHeaders,
                            ids = args.channelId?.let { listOf(it) },
                            logins = if (args.channelId.isNullOrBlank()) args.channelLogin?.let { listOf(it) } else null
                        ).data.firstOrNull()?.let {
                            User(
                                channelId = it.channelId,
                                channelLogin = it.channelLogin,
                                channelName = it.channelName,
                                type = it.type,
                                broadcasterType = it.broadcasterType,
                                profileImageUrl = it.profileImageUrl,
                                createdAt = it.createdAt,
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }
        }
    }

    fun retry(useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (_stream.value == null) {
            loadStream(useCronet, gqlHeaders, helixHeaders, enableIntegrity)
        } else {
            if (_stream.value?.user == null && _user.value == null) {
                loadUser(useCronet, helixHeaders)
            }
        }
    }

    fun enableNotifications(userId: String?, channelId: String, setting: Int, notificationsEnabled: Boolean, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && _isFollowing.value == true && userId != channelId) {
                    val errorMessage = graphQLRepository.loadToggleNotificationsUser(useCronet, gqlHeaders, channelId, false).also { response ->
                        if (enableIntegrity && integrity.value == null) {
                            response.errors?.find { it.message == "failed integrity check" }?.let {
                                integrity.value = "enableNotifications"
                                return@launch
                            }
                        }
                    }.errors?.firstOrNull()?.message
                    if (!errorMessage.isNullOrBlank()) {
                        notifications.value = Pair(true, errorMessage)
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

    fun disableNotifications(userId: String?, channelId: String, setting: Int, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && _isFollowing.value == true && userId != channelId) {
                    val errorMessage = graphQLRepository.loadToggleNotificationsUser(useCronet, gqlHeaders, channelId, true).also { response ->
                        if (enableIntegrity && integrity.value == null) {
                            response.errors?.find { it.message == "failed integrity check" }?.let {
                                integrity.value = "disableNotifications"
                                return@launch
                            }
                        }
                    }.errors?.firstOrNull()?.message
                    if (!errorMessage.isNullOrBlank()) {
                        notifications.value = Pair(false, errorMessage)
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

    fun updateNotifications(useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        viewModelScope.launch {
            shownNotificationsRepository.getNewStreams(notificationUsersRepository, useCronet, gqlHeaders, graphQLRepository, helixHeaders, helixRepository)
        }
    }

    fun isFollowingChannel(userId: String?, channelId: String?, channelLogin: String?, setting: Int, useCronet: Boolean, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>) {
        if (_isFollowing.value == null) {
            viewModelScope.launch {
                try {
                    if (!channelId.isNullOrBlank()) {
                        if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                            val response = try {
                                if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || channelLogin == null) throw Exception()
                                val follower = graphQLRepository.loadFollowingUser(useCronet, gqlHeaders, channelLogin).data?.user?.self?.follower
                                Pair(follower != null, follower?.disableNotifications == false)
                            } catch (e: Exception) {
                                val following = helixRepository.getUserFollows(
                                    useCronet = useCronet,
                                    headers = helixHeaders,
                                    userId = userId,
                                    targetId = channelId,
                                ).data.firstOrNull()?.channelId == channelId
                                Pair(following, null)
                            }
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

    fun saveFollowChannel(userId: String?, channelId: String?, channelLogin: String?, channelName: String?, setting: Int, notificationsEnabled: Boolean, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = graphQLRepository.loadFollowUser(useCronet, gqlHeaders, channelId).also { response ->
                            if (enableIntegrity && integrity.value == null) {
                                response.errors?.find { it.message == "failed integrity check" }?.let {
                                    integrity.value = "follow"
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(true, errorMessage)
                        } else {
                            _isFollowing.value = true
                            follow.value = Pair(true, null)
                            _notificationsEnabled.value = true
                            if (notificationsEnabled) {
                                _stream.value?.startedAt.takeUnless { it.isNullOrBlank() }?.let { TwitchApiHelper.parseIso8601DateUTC(it) }?.let {
                                    shownNotificationsRepository.saveList(listOf(ShownNotification(channelId, it)))
                                }
                            }
                        }
                    } else {
                        localFollowsChannel.saveFollow(LocalFollowChannel(channelId, channelLogin, channelName))
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

    fun deleteFollowChannel(userId: String?, channelId: String?, setting: Int, useCronet: Boolean, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        viewModelScope.launch {
            try {
                if (!channelId.isNullOrBlank()) {
                    if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                        val errorMessage = graphQLRepository.loadUnfollowUser(useCronet, gqlHeaders, channelId).also { response ->
                            if (enableIntegrity && integrity.value == null) {
                                response.errors?.find { it.message == "failed integrity check" }?.let {
                                    integrity.value = "unfollow"
                                    return@launch
                                }
                            }
                        }.errors?.firstOrNull()?.message
                        if (!errorMessage.isNullOrBlank()) {
                            follow.value = Pair(false, errorMessage)
                        } else {
                            _isFollowing.value = false
                            follow.value = Pair(false, null)
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

    fun updateLocalUser(useCronet: Boolean, filesDir: String, user: User) {
        if (!updatedLocalUser) {
            updatedLocalUser = true
            user.channelId.takeIf { !it.isNullOrBlank() }?.let { userId ->
                viewModelScope.launch {
                    val downloadedLogo = user.channelLogo.takeIf { !it.isNullOrBlank() }?.let {
                        File(filesDir, "profile_pics").mkdir()
                        val path = filesDir + File.separator + "profile_pics" + File.separator + userId
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                if (useCronet && cronetEngine != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        val request = UrlRequestCallbacks.forByteArrayBody(RedirectHandlers.alwaysFollow())
                                        cronetEngine.newUrlRequestBuilder(it, request.callback, cronetExecutor).build().start()
                                        val response = request.future.get()
                                        if (response.urlResponseInfo.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.responseBody as ByteArray)
                                            }
                                        }
                                    } else {
                                        val response = suspendCoroutine<Pair<UrlResponseInfo, ByteArray>> { continuation ->
                                            cronetEngine.newUrlRequestBuilder(it, getByteArrayCronetCallback(continuation), cronetExecutor).build().start()
                                        }
                                        if (response.first.httpStatusCode in 200..299) {
                                            FileOutputStream(path).use {
                                                it.write(response.second)
                                            }
                                        }
                                    }
                                } else {
                                    okHttpClient.newCall(Request.Builder().url(it).build()).execute().use { response ->
                                        if (response.isSuccessful) {
                                            File(path).sink().buffer().use { sink ->
                                                sink.writeAll(response.body.source())
                                            }
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
