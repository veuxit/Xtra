package com.github.andreyasadchy.xtra.ui.game

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsFragment
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsFragment
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosFragment

class GamePagerAdapter(private val fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> GameVideosFragment()
            1 -> GameStreamsFragment()
            else -> GameClipsFragment()
        }.also { it.arguments = fragment.arguments }
    }

    override fun getItemCount(): Int = 3
}