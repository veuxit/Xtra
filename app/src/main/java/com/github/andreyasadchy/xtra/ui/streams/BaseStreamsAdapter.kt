package com.github.andreyasadchy.xtra.ui.streams

import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.common.BasePagedListAdapter
import com.github.andreyasadchy.xtra.ui.common.OnChannelSelectedListener
import com.github.andreyasadchy.xtra.ui.games.GamesFragment
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.loadImage
import com.github.andreyasadchy.xtra.util.visible
import kotlinx.android.synthetic.main.fragment_streams_list_item.view.*

abstract class BaseStreamsAdapter(
        protected val fragment: Fragment,
        private val clickListener: BaseStreamsFragment.OnStreamSelectedListener,
        private val channelClickListener: OnChannelSelectedListener,
        val gameClickListener: GamesFragment.OnGameSelectedListener) : BasePagedListAdapter<Stream>(
        object : DiffUtil.ItemCallback<Stream>() {
            override fun areItemsTheSame(oldItem: Stream, newItem: Stream): Boolean =
                    oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Stream, newItem: Stream): Boolean =
                    oldItem.viewerCount == newItem.viewerCount &&
                            oldItem.gameName == newItem.gameName &&
                            oldItem.title == newItem.title
        }) {

    override fun bind(item: Stream, view: View) {
        val channelListener: (View) -> Unit = { channelClickListener.viewChannel(item.channelId, item.channelLogin, item.channelName, item.channelLogo, streamId = item.id) }
        val gameListener: (View) -> Unit = { gameClickListener.openGame(item.gameId, item.gameName) }
        with(view) {
            setOnClickListener { clickListener.startStream(item) }
            if (item.channelLogo != null)  {
                userImage.visible()
                userImage.loadImage(fragment, item.channelLogo, circle = true)
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
            if (item.title != null && item.title != "")  {
                title.visible()
                title.text = item.title.trim()
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
        }
    }
}
