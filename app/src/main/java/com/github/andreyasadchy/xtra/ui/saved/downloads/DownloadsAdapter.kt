package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.content.ContentResolver
import android.graphics.Color
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentDownloadsListItemBinding
import com.github.andreyasadchy.xtra.model.offline.OfflineVideo
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.loadImage
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible

class DownloadsAdapter(
    private val fragment: Fragment,
    private val stopDownload: (Int) -> Unit,
    private val resumeDownload: (Int) -> Unit,
    private val convertVideo: (OfflineVideo) -> Unit,
    private val moveVideo: (OfflineVideo) -> Unit,
    private val updateChatUrl: (OfflineVideo) -> Unit,
    private val deleteVideo: (OfflineVideo) -> Unit) : PagingDataAdapter<OfflineVideo, DownloadsAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<OfflineVideo>() {
        override fun areItemsTheSame(oldItem: OfflineVideo, newItem: OfflineVideo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: OfflineVideo, newItem: OfflineVideo): Boolean {
            return false //bug, oldItem and newItem are sometimes the same
        }
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder {
        val binding = FragmentDownloadsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PagingViewHolder(binding, fragment)
    }

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PagingViewHolder(
        private val binding: FragmentDownloadsListItemBinding,
        private val fragment: Fragment): RecyclerView.ViewHolder(binding.root) {
        fun bind(item: OfflineVideo?) {
            with(binding) {
                if (item != null) {
                    val context = fragment.requireContext()
                    val channelListener: (View) -> Unit = { fragment.findNavController().navigate(ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                        channelId = item.channelId,
                        channelLogin = item.channelLogin,
                        channelName = item.channelName,
                        channelLogo = item.channelLogo,
                        updateLocal = true
                    )) }
                    val gameListener: (View) -> Unit = {
                        fragment.findNavController().navigate(
                            if (context.prefs().getBoolean(C.UI_GAMEPAGER, true)) {
                                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                    gameId = item.gameId,
                                    gameSlug = item.gameSlug,
                                    gameName = item.gameName
                                )
                            } else {
                                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                    gameId = item.gameId,
                                    gameSlug = item.gameSlug,
                                    gameName = item.gameName
                                )
                            }
                        )
                    }
                    root.setOnClickListener {
                        (fragment.activity as MainActivity).startOfflineVideo(item)
                    }
                    root.setOnLongClickListener { deleteVideo(item); true }
                    thumbnail.loadImage(fragment, item.thumbnail, diskCacheStrategy = DiskCacheStrategy.NONE)
                    item.uploadDate?.let {
                        date.visible()
                        date.text = context.getString(R.string.uploaded_date, TwitchApiHelper.formatTime(context, it))
                    } ?: {
                        date.gone()
                    }
                    if (item.downloadDate != null) {
                        downloadDate.visible()
                        downloadDate.text = context.getString(R.string.downloaded_date, TwitchApiHelper.formatTime(context, item.downloadDate))
                    } else {
                        downloadDate.gone()
                    }
                    if (item.type != null) {
                        val text = TwitchApiHelper.getType(context, item.type)
                        if (text != null) {
                            type.visible()
                            type.text = text
                        } else {
                            type.gone()
                        }
                    } else {
                        type.gone()
                    }
                    if (item.channelLogo != null)  {
                        userImage.visible()
                        userImage.loadImage(fragment, item.channelLogo, circle = true, diskCacheStrategy = DiskCacheStrategy.NONE)
                        userImage.setOnClickListener(channelListener)
                    } else {
                        userImage.gone()
                    }
                    if (item.channelName != null)  {
                        username.visible()
                        username.text = item.channelName
                        username.setOnClickListener(channelListener)
                    } else {
                        username.gone()
                    }
                    item.name?.let {
                        title.visible()
                        title.text = it.trim()
                    } ?: {
                        title.gone()
                    }
                    if (item.gameName != null)  {
                        gameName.visible()
                        gameName.text = item.gameName
                        gameName.setOnClickListener(gameListener)
                    } else {
                        gameName.gone()
                    }
                    item.duration?.let { itemDuration ->
                        duration.visible()
                        duration.text = DateUtils.formatElapsedTime(itemDuration / 1000L)
                        item.sourceStartPosition?.let { sourceStartPosition ->
                            sourceStart.visible()
                            sourceStart.text = context.getString(R.string.source_vod_start, DateUtils.formatElapsedTime(sourceStartPosition / 1000L))
                            sourceEnd.visible()
                            sourceEnd.text = context.getString(R.string.source_vod_end, DateUtils.formatElapsedTime((sourceStartPosition + itemDuration) / 1000L))
                        } ?: {
                            sourceStart.gone()
                            sourceEnd.gone()
                        }
                        if (context.prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true) && item.lastWatchPosition != null && itemDuration > 0L) {
                            progressBar.progress = (item.lastWatchPosition!!.toFloat() / itemDuration * 100).toInt()
                            progressBar.visible()
                        } else {
                            progressBar.gone()
                        }
                    } ?: {
                        duration.gone()
                        sourceStart.gone()
                        sourceEnd.gone()
                        progressBar.gone()
                    }
                    if (sourceEnd.isVisible && sourceStart.isVisible) {
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, context.convertDpToPixels(5F), 0, 0)
                        sourceEnd.layoutParams = params
                    }
                    if (type.isVisible && (sourceStart.isVisible || sourceEnd.isVisible)) {
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.setMargins(0, context.convertDpToPixels(5F), 0, 0)
                        type.layoutParams = params
                    }
                    options.setOnClickListener { it ->
                        PopupMenu(context, it).apply {
                            inflate(R.menu.offline_item)
                            if (item.status == OfflineVideo.STATUS_DOWNLOADED || item.status == OfflineVideo.STATUS_MOVING || item.status == OfflineVideo.STATUS_DELETING || item.status == OfflineVideo.STATUS_CONVERTING) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP || Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !item.url.endsWith(".m3u8")) {
                                    menu.findItem(R.id.moveVideo).apply {
                                        isVisible = true
                                        title = context.getString(if (item.url.toUri().scheme == ContentResolver.SCHEME_CONTENT) {
                                            R.string.move_to_app_storage
                                        } else {
                                            R.string.move_to_shared_storage
                                        })
                                    }
                                }
                                if (item.vod) {
                                    menu.findItem(R.id.convertVideo).isVisible = true
                                }
                                menu.findItem(R.id.updateChatUrl).isVisible = true
                            } else {
                                if (item.downloadChat == true || item.sourceUrl?.endsWith(".m3u8") == true || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false))) {
                                    menu.findItem(R.id.stopDownload).isVisible = true
                                    menu.findItem(R.id.resumeDownload).isVisible = true
                                }
                            }
                            setOnMenuItemClickListener {
                                when(it.itemId) {
                                    R.id.stopDownload -> stopDownload(item.id)
                                    R.id.resumeDownload -> resumeDownload(item.id)
                                    R.id.convertVideo -> convertVideo(item)
                                    R.id.moveVideo -> moveVideo(item)
                                    R.id.updateChatUrl -> updateChatUrl(item)
                                    R.id.delete -> deleteVideo(item)
                                    else -> menu.close()
                                }
                                true
                            }
                            show()
                        }
                    }
                    if (item.status == OfflineVideo.STATUS_DOWNLOADED) {
                        status.gone()
                    } else {
                        downloadProgress.text = when (item.status) {
                            OfflineVideo.STATUS_DOWNLOADING -> context.getString(R.string.downloading_progress, ((item.progress.toFloat() / item.maxProgress) * 100f).toInt())
                            OfflineVideo.STATUS_MOVING -> context.getString(R.string.download_moving)
                            OfflineVideo.STATUS_DELETING -> context.getString(R.string.download_deleting)
                            OfflineVideo.STATUS_CONVERTING -> context.getString(R.string.download_converting)
                            else -> context.getString(R.string.download_pending)
                        }
                        if (item.downloadChat == true && item.status == OfflineVideo.STATUS_DOWNLOADING) {
                            chatDownloadProgress.visible()
                            chatDownloadProgress.text = context.getString(R.string.chat_downloading_progress, item.chatProgress ?: 0)
                        } else {
                            chatDownloadProgress.gone()
                        }
                        status.visible()
                        if (item.vod || item.sourceUrl?.endsWith(".m3u8") == true) {
                            status.background = null
                            status.isClickable = false
                            status.isFocusable = false
                            downloadProgress.setShadowLayer(4f, 0f, 0f, Color.BLACK)
                            chatDownloadProgress.setShadowLayer(4f, 0f, 0f, Color.BLACK)
                        } else {
                            status.setOnClickListener { deleteVideo(item) }
                            status.setOnLongClickListener { deleteVideo(item); true }
                        }
                    }
                }
            }
        }
    }
}