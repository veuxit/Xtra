package com.github.andreyasadchy.xtra.ui.common.follow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.lifecycle.MutableLiveData
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.model.Account
import com.github.andreyasadchy.xtra.model.offline.LocalFollowChannel
import com.github.andreyasadchy.xtra.model.offline.LocalFollowGame
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowGameRepository
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class FollowLiveData(
    private val localFollowsChannel: LocalFollowChannelRepository? = null,
    private val localFollowsGame: LocalFollowGameRepository? = null,
    private val userId: String?,
    private val userLogin: String?,
    private val userName: String?,
    private var channelLogo: String?,
    private val repository: ApiRepository,
    private val helixClientId: String? = null,
    private val account: Account,
    private val gqlClientId: String? = null,
    private val gqlClientId2: String? = null,
    private val setting: Int,
    private val viewModelScope: CoroutineScope) : MutableLiveData<Boolean>()  {

    init {
        viewModelScope.launch {
            try {
                val isFollowing = when {
                    localFollowsGame != null -> {
                        when {
                            setting == 0 && !account.gqlToken.isNullOrBlank() && !userName.isNullOrBlank() -> {
                                repository.loadGameFollowing(gqlClientId, account.gqlToken, userName)
                            }
                            !userId.isNullOrBlank() -> localFollowsGame.getFollowByGameId(userId) != null
                            else -> false
                        }
                    }
                    localFollowsChannel != null -> {
                        when {
                            setting == 0 && (
                                    (!helixClientId.isNullOrBlank() && !account.helixToken.isNullOrBlank() && !account.id.isNullOrBlank() && !userId.isNullOrBlank() && account.id != userId) ||
                                            (!account.gqlToken.isNullOrBlank() && !account.login.isNullOrBlank() && !userLogin.isNullOrBlank() && account.login != userLogin)) -> {
                                repository.loadUserFollowing(helixClientId, account.helixToken, userId, account.id, gqlClientId, account.gqlToken, userLogin)
                            }
                            !userId.isNullOrBlank() -> localFollowsChannel.getFollowByUserId(userId) != null
                            else -> false
                        }
                    }
                    else -> false
                }
                super.setValue(isFollowing)
            } catch (e: Exception) {

            }
        }
    }

    suspend fun saveFollowChannel(context: Context): String? {
        try {
            if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                return if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank()) {
                    repository.followUser(gqlClientId2, account.gqlToken2, userId)
                } else {
                    repository.followUser(gqlClientId, account.gqlToken, userId)
                }
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
                    localFollowsChannel?.saveFollow(LocalFollowChannel(userId, userLogin, userName, downloadedLogo))
                }
            }
        } catch (e: Exception) {

        }
        return null
    }

    suspend fun deleteFollowChannel(context: Context): String? {
        try {
            if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                return if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank()) {
                    repository.unfollowUser(gqlClientId2, account.gqlToken2, userId)
                } else {
                    repository.unfollowUser(gqlClientId, account.gqlToken, userId)
                }
            } else {
                if (userId != null) {
                    localFollowsChannel?.getFollowByUserId(userId)?.let { localFollowsChannel.deleteFollow(context, it) }
                }
            }
        } catch (e: Exception) {

        }
        return null
    }

    suspend fun saveFollowGame(context: Context): String? {
        try {
            if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                return if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank()) {
                    repository.followGame(gqlClientId2, account.gqlToken2, userId)
                } else {
                    repository.followGame(gqlClientId, account.gqlToken, userId)
                }
            } else {
                if (userId != null) {
                    if (channelLogo == null) {
                        val get = repository.loadGameBoxArt(userId, helixClientId, account.helixToken, gqlClientId)
                        if (get != null) {
                            channelLogo = TwitchApiHelper.getTemplateUrl(get, "game")
                        }
                    }
                    try {
                        Glide.with(context)
                            .asBitmap()
                            .load(channelLogo)
                            .into(object: CustomTarget<Bitmap>() {
                                override fun onLoadCleared(placeholder: Drawable?) {

                                }

                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                                    DownloadUtils.savePng(context, "box_art", userId, resource)
                                }
                            })
                    } catch (e: Exception) {

                    }
                    val downloadedLogo = File(context.filesDir.toString() + File.separator + "box_art" + File.separator + "${userId}.png").absolutePath
                    localFollowsGame?.saveFollow(LocalFollowGame(userId, userName, downloadedLogo))
                }
            }
        } catch (e: Exception) {

        }
        return null
    }

    suspend fun deleteFollowGame(context: Context): String? {
        try {
            if (setting == 0 && !account.gqlToken.isNullOrBlank()) {
                return if (!gqlClientId2.isNullOrBlank() && !account.gqlToken2.isNullOrBlank()) {
                    repository.unfollowGame(gqlClientId2, account.gqlToken2, userId)
                } else {
                    repository.unfollowGame(gqlClientId, account.gqlToken, userId)
                }
            } else {
                if (userId != null) {
                    localFollowsGame?.getFollowByGameId(userId)?.let { localFollowsGame.deleteFollow(context, it) }
                }
            }
        } catch (e: Exception) {

        }
        return null
    }
}