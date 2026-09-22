package com.xtra.kick.ui.game

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.xtra.kick.ui.game.clips.GameClipsFragment
import com.xtra.kick.ui.game.streams.GameStreamsFragment
import com.xtra.kick.ui.game.videos.GameVideosFragment
import kotlin.math.max

class GamePagerAdapter(
    private val fragment: Fragment,
    private val tabs: List<String>,
) : FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment {
        return when (tabs.getOrNull(position)) {
            "0" -> GameVideosFragment()
            "1" -> GameStreamsFragment()
            "2" -> GameClipsFragment()
            else -> GameStreamsFragment()
        }.also { it.arguments = fragment.arguments }
    }

    override fun getItemCount(): Int = max(tabs.size, 1)
}