package com.github.andreyasadchy.xtra.ui.saved.downloads

import android.graphics.Color
import android.os.Build
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
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
    private val deleteVideo: (OfflineVideo) -> Unit) : ListAdapter<OfflineVideo, DownloadsAdapter.PagingViewHolder>(
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
                    if (item.uploadDate != null) {
                        date.visible()
                        date.text = context.getString(R.string.uploaded_date, TwitchApiHelper.formatTime(context, item.uploadDate))
                    } else {
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
                    if (item.name != null)  {
                        title.visible()
                        title.text = item.name.trim()
                    } else {
                        title.gone()
                    }
                    if (item.gameName != null)  {
                        gameName.visible()
                        gameName.text = item.gameName
                        gameName.setOnClickListener(gameListener)
                    } else {
                        gameName.gone()
                    }
                    if (item.duration != null) {
                        duration.visible()
                        duration.text = DateUtils.formatElapsedTime(item.duration / 1000L)
                        if (item.sourceStartPosition != null)  {
                            sourceStart.visible()
                            sourceStart.text = context.getString(R.string.source_vod_start, DateUtils.formatElapsedTime(item.sourceStartPosition / 1000L))
                            sourceEnd.visible()
                            sourceEnd.text = context.getString(R.string.source_vod_end, DateUtils.formatElapsedTime((item.sourceStartPosition + item.duration) / 1000L))
                        } else {
                            sourceStart.gone()
                            sourceEnd.gone()
                        }
                        if (context.prefs().getBoolean(C.PLAYER_USE_VIDEOPOSITIONS, true) && item.lastWatchPosition != null && item.duration > 0L) {
                            progressBar.progress = (item.lastWatchPosition!!.toFloat() / item.duration * 100).toInt()
                            progressBar.visible()
                        } else {
                            progressBar.gone()
                        }
                    } else {
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
                            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE || context.prefs().getBoolean(C.DEBUG_WORKMANAGER_DOWNLOADS, false)) && item.status != OfflineVideo.STATUS_DOWNLOADED) {
                                menu.findItem(R.id.stopDownload).isVisible = true
                                menu.findItem(R.id.resumeDownload).isVisible = true
                            }
                            setOnMenuItemClickListener {
                                when(it.itemId) {
                                    R.id.stopDownload -> stopDownload(item.id)
                                    R.id.resumeDownload -> resumeDownload(item.id)
                                    R.id.delete -> deleteVideo(item)
                                    else -> menu.close()
                                }
                                true
                            }
                            show()
                        }
                    }
                    status.apply {
                        if (item.status == OfflineVideo.STATUS_DOWNLOADED) {
                            gone()
                        } else {
                            text = if (item.status == OfflineVideo.STATUS_DOWNLOADING) {
                                context.getString(R.string.downloading_progress, ((item.progress.toFloat() / item.maxProgress) * 100f).toInt())
                            } else {
                                context.getString(R.string.download_pending)
                            }
                            visible()
                            if (item.vod) {
                                background = null
                                isClickable = false
                                isFocusable = false
                                setShadowLayer(4f, 0f, 0f, Color.BLACK)
                            } else {
                                setOnClickListener { deleteVideo(item) }
                                setOnLongClickListener { deleteVideo(item); true }
                            }
                        }
                    }
                }
            }
        }
    }
}