package com.github.andreyasadchy.xtra.ui.search.channels

import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.common.BasePagedListAdapter
import com.github.andreyasadchy.xtra.ui.common.OnChannelSelectedListener
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.loadImage
import com.github.andreyasadchy.xtra.util.visible
import kotlinx.android.synthetic.main.fragment_search_channels_list_item.view.*

class ChannelSearchAdapter(
        private val fragment: Fragment,
        private val listener: OnChannelSelectedListener) : BasePagedListAdapter<User>(
        object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
                    oldItem.channelId == newItem.channelId

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = true
        }) {

    override val layoutId: Int = R.layout.fragment_search_channels_list_item

    override fun bind(item: User, view: View) {
        with(view) {
            setOnClickListener { listener.viewChannel(item.channelId, item.channelLogin, item.channelName, item.channelLogo) }
            if (item.channelLogo != null) {
                userImage.visible()
                userImage.loadImage(fragment, item.channelLogo, circle = true)
            } else {
                userImage.gone()
            }
            if (item.channelName != null) {
                userName.visible()
                userName.text = item.channelName
            } else {
                userName.gone()
            }
            if (item.followersCount != null) {
                userFollowers.visible()
                userFollowers.text = context.getString(R.string.followers, TwitchApiHelper.formatCount(context, item.followersCount))
            } else {
                userFollowers.gone()
            }
            if (!item.type.isNullOrBlank() || item.isLive == true) {
                typeText.visible()
                if (item.type == "rerun") {
                    typeText.text = context.getString(R.string.video_type_rerun)
                } else {
                    typeText.text = context.getString(R.string.live)
                }
            } else {
                typeText.gone()
            }
        }
    }
}