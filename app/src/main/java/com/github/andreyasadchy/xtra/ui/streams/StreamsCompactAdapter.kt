package com.github.andreyasadchy.xtra.ui.streams

import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.common.OnChannelSelectedListener
import com.github.andreyasadchy.xtra.ui.games.GamesFragment
import com.github.andreyasadchy.xtra.util.*
import kotlinx.android.synthetic.main.fragment_streams_list_item_compact.view.*

class StreamsCompactAdapter(
        fragment: Fragment,
        clickListener: BaseStreamsFragment.OnStreamSelectedListener,
        channelClickListener: OnChannelSelectedListener,
        gameClickListener: GamesFragment.OnGameSelectedListener) : BaseStreamsAdapter(fragment, clickListener, channelClickListener, gameClickListener) {

    override val layoutId: Int = R.layout.fragment_streams_list_item_compact

    override fun bind(item: Stream, view: View) {
        super.bind(item, view)
        with(view) {
            if (item.viewerCount != null) {
                viewers.visible()
                viewers.text = TwitchApiHelper.formatCount(context, item.viewerCount ?: 0)
            } else {
                viewers.gone()
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
            if (context.prefs().getBoolean(C.UI_UPTIME, true) && item.startedAt != null) {
                val text = TwitchApiHelper.getUptime(context = context, input = item.startedAt)
                if (text != null) {
                    uptime.visible()
                    uptime.text = text
                } else {
                    uptime.gone()
                }
            } else {
                uptime.gone()
            }
            if (!item.tags.isNullOrEmpty() && context.prefs().getBoolean(C.UI_TAGS, true)) {
                tagsLayout.removeAllViews()
                tagsLayout.visible()
                for (tag in item.tags) {
                    val text = TextView(context)
                    text.text = tag
                    text.setOnClickListener { gameClickListener.openGame(tags = listOf(tag), id = fragment.parentFragment?.arguments?.getString(C.GAME_ID), name = fragment.parentFragment?.arguments?.getString(C.GAME_NAME)) }
                    tagsLayout.addView(text)
                }
            } else {
                tagsLayout.gone()
            }
        }
    }
}