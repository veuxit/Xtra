package com.github.andreyasadchy.xtra.ui.player.clip

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.ui.player.PlayerHelper
import com.github.andreyasadchy.xtra.ui.player.PlayerViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedList
import javax.inject.Inject

@HiltViewModel
class ClipPlayerViewModel @Inject constructor(
    context: Application,
    private val graphQLRepository: GraphQLRepository,
    private val repository: ApiRepository,
    private val localFollowsChannel: LocalFollowChannelRepository) : PlayerViewModel(context) {

    val follow = MutableLiveData<Pair<Boolean, String?>>()
    private lateinit var clip: Clip
    private val helper = PlayerHelper()
    val qualityMap: Map<String, String>
        get() = helper.urls
    val loaded: LiveData<Boolean>
        get() = helper.loaded

    override fun changeQuality(index: Int) {
        qualityIndex = index
        player?.currentPosition?.let { playbackPosition = it }
        helper.urls.values.elementAtOrNull(index)?.let { play(it) }
        if (prefs.getString(C.PLAYER_DEFAULTQUALITY, "saved") == "saved") {
            qualities?.getOrNull(index)?.let { prefs.edit { putString(C.PLAYER_QUALITY, it) } }
        }
    }

    override fun onResume() {
        initializePlayer()
        play()
        player?.seekTo(playbackPosition)
    }

    override fun onPause() {
        player?.currentPosition?.let { playbackPosition = it }
        releasePlayer()
    }

    fun setClip(clip: Clip) {
        if (!this::clip.isInitialized) {
            this.clip = clip
            viewModelScope.launch {
                try {
                    val skipAccessToken = prefs.getString(C.TOKEN_SKIP_CLIP_ACCESS_TOKEN, "2")?.toIntOrNull() ?: 2
                    val urls = if (skipAccessToken <= 1 && !clip.thumbnailUrl.isNullOrBlank()) {
                        TwitchApiHelper.getClipUrlMapFromPreview(clip.thumbnailUrl)
                    } else {
                        graphQLRepository.loadClipUrls(
                            clientId = prefs.getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"),
                            slug = clip.id
                        ) ?: if (skipAccessToken == 2 && !clip.thumbnailUrl.isNullOrBlank()) {
                            TwitchApiHelper.getClipUrlMapFromPreview(clip.thumbnailUrl)
                        } else mapOf()
                    }
                    helper.urls = urls
                    qualities = LinkedList(urls.keys)
                    setQualityIndex()
                    (urls.values.elementAtOrNull(qualityIndex) ?: urls.values.firstOrNull())?.let {
                        play(it)
                        helper.loaded.value = true
                    }
                } catch (e: Exception) {

                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val playerError = player?.playerError
        if (playerError?.type == ExoPlaybackException.TYPE_UNEXPECTED && playerError.unexpectedException is IllegalStateException) {
            val context = getApplication<Application>()
            context.shortToast(R.string.player_error)
            if (qualityIndex < helper.urls.size - 1) {
                changeQuality(++qualityIndex)
            }
        }
    }

    private fun play(url: String) {
        mediaItem = MediaItem.fromUri(url.toUri())
        initializePlayer()
        play()
        player?.seekTo(playbackPosition)
    }

    fun isFollowingChannel(context: Context, channelId: String?, channelLogin: String?) {
        if (!follow.isInitialized) {
            viewModelScope.launch {
                try {
                    val setting = context.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
                    val account = Account.get(context)
                    val helixClientId = context.prefs().getString(C.HELIX_CLIENT_ID, "ilfexgv3nnljz3isbm257gzwrzr7bi")
                    val gqlClientId = context.prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp")
                    val isFollowing = if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                        if ((!helixClientId.isNullOrBlank() && !account.helixToken.isNullOrBlank() && !account.id.isNullOrBlank() && !channelId.isNullOrBlank() && account.id != channelId) ||
                            (!account.login.isNullOrBlank() && !channelLogin.isNullOrBlank() && account.login != channelLogin)) {
                            repository.loadUserFollowing(helixClientId, account.helixToken, channelId, account.id, gqlClientId, account.gqlToken, channelLogin)
                        } else false
                    } else {
                        channelId?.let {
                            localFollowsChannel.getFollowByUserId(it)
                        } != null
                    }
                    follow.postValue(Pair(isFollowing, null))
                } catch (e: Exception) {

                }
            }
        }
    }

    fun saveFollowChannel(context: Context, userId: String?, userLogin: String?, userName: String?, channelLogo: String?) {
        GlobalScope.launch {
            val setting = context.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
            val account = Account.get(context)
            val gqlClientId = context.prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp")
            val gqlClientId2 = context.prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp")
            try {
                if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                    val errorMessage = if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank()) {
                        repository.followUser(gqlClientId2, account.gqlToken2, userId)
                    } else {
                        repository.followUser(gqlClientId, account.gqlToken, userId)
                    }
                    follow.postValue(Pair(true, errorMessage))
                } else {
                    if (userId != null) {
                        try {
                            Glide.with(context)
                                .asBitmap()
                                .load(channelLogo)
                                .into(object: CustomTarget<Bitmap>() {
                                    override fun onLoadCleared(placeholder: Drawable?) {

                                    }

                                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                        DownloadUtils.savePng(context, "profile_pics", userId, resource)
                                    }
                                })
                        } catch (e: Exception) {

                        }
                        val downloadedLogo = File(context.filesDir.toString() + File.separator + "profile_pics" + File.separator + "${userId}.png").absolutePath
                        localFollowsChannel.saveFollow(LocalFollowChannel(userId, userLogin, userName, downloadedLogo))
                        follow.postValue(Pair(true, null))
                    }
                }
            } catch (e: Exception) {

            }
        }
    }

    fun deleteFollowChannel(context: Context, userId: String?) {
        GlobalScope.launch {
            val setting = context.prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toInt() ?: 0
            val account = Account.get(context)
            val gqlClientId = context.prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp")
            val gqlClientId2 = context.prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp")
            try {
                if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                    val errorMessage = if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank()) {
                        repository.unfollowUser(gqlClientId2, account.gqlToken2, userId)
                    } else {
                        repository.unfollowUser(gqlClientId, account.gqlToken, userId)
                    }
                    follow.postValue(Pair(false, errorMessage))
                } else {
                    if (userId != null) {
                        localFollowsChannel.getFollowByUserId(userId)?.let { localFollowsChannel.deleteFollow(context, it) }
                        follow.postValue(Pair(false, null))
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}
