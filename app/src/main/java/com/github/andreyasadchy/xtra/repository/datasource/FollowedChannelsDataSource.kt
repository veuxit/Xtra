package com.github.andreyasadchy.xtra.repository.datasource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.paging.DataSource
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.follows.Follow
import com.github.andreyasadchy.xtra.repository.LocalFollowRepository
import com.github.andreyasadchy.xtra.repository.OfflineRepository
import com.github.andreyasadchy.xtra.util.DownloadUtils
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class FollowedChannelsDataSource(
    private val localFollows: LocalFollowRepository,
    private val offlineRepository: OfflineRepository,
    private val helixClientId: String?,
    private val userToken: String?,
    private val userId: String,
    private val api: HelixApi,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Follow>(coroutineScope) {
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Follow>) {
        loadInitial(params, callback) {
            val list = mutableListOf<Follow>()
            for (i in localFollows.loadFollows()) {
                list.add(Follow(to_id = i.user_id, to_login = i.user_login, to_name = i.user_name, profileImageURL = i.channelLogo, followLocal = true))
            }
            if (userId != "") {
                val get = api.getFollowedChannels(helixClientId, userToken, userId, 100, offset)
                if (get.data != null) {
                    for (i in get.data) {
                        val item = list.find { it.to_id == i.to_id }
                        if (item == null) {
                            i.followTwitch = true
                            list.add(i)
                        } else {
                            item.followTwitch = true
                        }
                    }
                    offset = get.pagination?.cursor
                }
            }
            if (list.isNotEmpty()) {
                val allIds = list.mapNotNull { it.to_id }
                if (allIds.isNotEmpty()) {
                    for (ids in allIds.chunked(100)) {
                        val get = api.getUserById(helixClientId, userToken, ids).data
                        if (get != null) {
                            for (user in get) {

                                val item = list.find { it.to_id == user.id }
                                if (item != null) {
                                    if (item.followLocal) {
                                        if (item.profileImageURL == null || item.profileImageURL?.contains("image_manager_disk_cache") == true) {
                                            val appContext = XtraApp.INSTANCE.applicationContext
                                            item.to_id?.let { id -> user.profile_image_url?.let { profileImageURL -> updateLocalUser(appContext, id, profileImageURL) } }
                                        }
                                    } else {
                                        if (item.profileImageURL == null) {
                                            item.profileImageURL = user.profile_image_url
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            list
        }
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Follow>) {
        loadRange(params, callback) {
            val list = mutableListOf<Follow>()
            if (offset != null && offset != "") {
                if (userId != "") {
                    val get = api.getFollowedChannels(helixClientId, userToken, userId, 100, offset)
                    if (get.data != null) {
                        for (i in get.data) {
                            val item = list.find { it.to_id == i.to_id }
                            if (item == null) {
                                i.followTwitch = true
                                list.add(i)
                            } else {
                                item.followTwitch = true
                            }
                        }
                        offset = get.pagination?.cursor
                    }
                }
                if (list.isNotEmpty()) {
                    val allIds = list.mapNotNull { it.to_id }
                    if (allIds.isNotEmpty()) {
                        for (ids in allIds.chunked(100)) {
                            val get = api.getUserById(helixClientId, userToken, ids).data
                            if (get != null) {
                                for (user in get) {
                                    val item = list.find { it.to_id == user.id }
                                    if (item != null) {
                                        if (item.profileImageURL == null) {
                                            item.profileImageURL = user.profile_image_url
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            list
        }
    }

    private fun updateLocalUser(context: Context, userId: String, profileImageURL: String) {
        GlobalScope.launch {
            try {
                try {
                    Glide.with(context)
                        .asBitmap()
                        .load(TwitchApiHelper.getTemplateUrl(profileImageURL, "profileimage"))
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
                localFollows.getFollowById(userId)?.let { localFollows.updateFollow(it.apply {
                    channelLogo = downloadedLogo }) }
                for (i in offlineRepository.getVideosByUserId(userId.toInt())) {
                    offlineRepository.updateVideo(i.apply {
                        channelLogo = downloadedLogo })
                }
            } catch (e: Exception) {

            }
        }
    }

    class Factory(
        private val localFollows: LocalFollowRepository,
        private val offlineRepository: OfflineRepository,
        private val helixClientId: String?,
        private val userToken: String?,
        private val userId: String,
        private val api: HelixApi,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Follow, FollowedChannelsDataSource>() {

        override fun create(): DataSource<Int, Follow> =
                FollowedChannelsDataSource(localFollows, offlineRepository, helixClientId, userToken, userId, api, coroutineScope).also(sourceLiveData::postValue)
    }
}
