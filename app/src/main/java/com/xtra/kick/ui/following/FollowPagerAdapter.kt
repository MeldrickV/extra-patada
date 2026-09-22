package com.xtra.kick.ui.following

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.xtra.kick.ui.following.channels.FollowedChannelsFragment
import com.xtra.kick.ui.following.games.FollowedGamesFragment
import com.xtra.kick.ui.following.streams.FollowedStreamsFragment
import com.xtra.kick.ui.following.videos.FollowedVideosFragment
import kotlin.math.max

class FollowPagerAdapter(
    fragment: Fragment,
    private val tabs: List<String>,
) : FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment {
        return when (tabs.getOrNull(position)) {
            "0" -> FollowedGamesFragment()
            "1" -> FollowedStreamsFragment()
            "2" -> FollowedVideosFragment()
            "3" -> FollowedChannelsFragment()
            else -> FollowedStreamsFragment()
        }
    }

    override fun getItemCount(): Int = max(tabs.size, 1)
}
