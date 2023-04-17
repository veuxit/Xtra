package com.github.andreyasadchy.xtra.ui.games

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.github.andreyasadchy.xtra.ui.clips.common.ClipsFragment
import com.github.andreyasadchy.xtra.ui.streams.common.StreamsFragment
import com.github.andreyasadchy.xtra.ui.videos.game.GameVideosFragment

class GamePagerAdapter(private val fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GameVideosFragment()
            1 -> StreamsFragment()
            else -> ClipsFragment()
        }.also { it.arguments = fragment.arguments }
    }

    override fun getItemCount(): Int = 3
}