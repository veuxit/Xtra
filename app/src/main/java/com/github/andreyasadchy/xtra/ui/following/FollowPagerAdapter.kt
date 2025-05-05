package com.github.andreyasadchy.xtra.ui.following

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.github.andreyasadchy.xtra.ui.following.channels.FollowedChannelsFragment
import com.github.andreyasadchy.xtra.ui.following.games.FollowedGamesFragment
import com.github.andreyasadchy.xtra.ui.following.streams.FollowedStreamsFragment
import com.github.andreyasadchy.xtra.ui.following.videos.FollowedVideosFragment

class FollowPagerAdapter(
    fragment: Fragment,
    private val loggedIn: Boolean,
) : FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment {
        return if (loggedIn) {
            when (position) {
                0 -> FollowedGamesFragment()
                1 -> FollowedStreamsFragment()
                2 -> FollowedVideosFragment()
                else -> FollowedChannelsFragment()
            }
        } else {
            when (position) {
                0 -> FollowedGamesFragment()
                1 -> FollowedStreamsFragment()
                else -> FollowedChannelsFragment()
            }
        }
    }

    override fun getItemCount(): Int = if (loggedIn) 4 else 3
}
