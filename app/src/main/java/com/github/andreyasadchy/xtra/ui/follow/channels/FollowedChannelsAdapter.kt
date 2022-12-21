package com.github.andreyasadchy.xtra.ui.follow.channels

import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.BasePagedListAdapter
import com.github.andreyasadchy.xtra.ui.common.OnChannelSelectedListener
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.loadImage
import com.github.andreyasadchy.xtra.util.visible
import kotlinx.android.synthetic.main.fragment_followed_channels_list_item.view.*

class FollowedChannelsAdapter(
        private val fragment: Fragment,
        private val listener: OnChannelSelectedListener) : BasePagedListAdapter<User>(
        object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
                    oldItem.channelId == newItem.channelId

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = true
        }) {

    override val layoutId: Int = R.layout.fragment_followed_channels_list_item

    override fun bind(item: User, view: View) {
        with(view) {
            setOnClickListener { listener.viewChannel(item.channelId, item.channelLogin, item.channelName, item.channelLogo, item.followLocal) }
            if (item.channelLogo != null)  {
                userImage.visible()
                userImage.loadImage(fragment, item.channelLogo, circle = true, diskCacheStrategy = DiskCacheStrategy.NONE)
            } else {
                userImage.gone()
            }
            if (item.channelName != null)  {
                username.visible()
                username.text = item.channelName
            } else {
                username.gone()
            }
            if (item.lastBroadcast != null) {
                val text = item.lastBroadcast?.let { TwitchApiHelper.formatTimeString(context, it) }
                if (text != null) {
                    userStream.visible()
                    userStream.text = context.getString(R.string.last_broadcast_date, text)
                } else {
                    userStream.gone()
                }
            } else {
                userStream.gone()
            }
            if (item.followedAt != null) {
                val text = TwitchApiHelper.formatTimeString(context, item.followedAt!!)
                if (text != null) {
                    userFollowed.visible()
                    userFollowed.text = context.getString(R.string.followed_at, text)
                } else {
                    userFollowed.gone()
                }
            } else {
                userFollowed.gone()
            }
            if (item.followAccount) {
                twitchText.visible()
            } else {
                twitchText.gone()
            }
            if (item.followLocal) {
                localText.visible()
            } else {
                localText.gone()
            }
        }
    }
}