package com.xtra.kick.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.xtra.kick.model.NotificationUser
import com.xtra.kick.model.PlaybackState
import com.xtra.kick.model.ShownNotification
import com.xtra.kick.model.VideoPosition
import com.xtra.kick.model.chat.RecentEmote
import com.xtra.kick.model.ui.Bookmark
import com.xtra.kick.model.ui.BookmarkIgnoredUser
import com.xtra.kick.model.ui.ChannelSort
import com.xtra.kick.model.ui.CustomProxy
import com.xtra.kick.model.ui.GameSort
import com.xtra.kick.model.ui.LocalChannelFollow
import com.xtra.kick.model.ui.LocalGameFollow
import com.xtra.kick.model.ui.OfflineVideo
import com.xtra.kick.model.ui.RecentSearch
import com.xtra.kick.model.ui.SavedFilter
import com.xtra.kick.model.ui.StreamProxy
import com.xtra.kick.model.ui.TranslatedChannel
import com.xtra.kick.model.ui.VideoSwap

@Database(
    entities = [
        OfflineVideo::class,
        RecentEmote::class,
        VideoPosition::class,
        LocalChannelFollow::class,
        LocalGameFollow::class,
        Bookmark::class,
        BookmarkIgnoredUser::class,
        ChannelSort::class,
        GameSort::class,
        ShownNotification::class,
        NotificationUser::class,
        TranslatedChannel::class,
        SavedFilter::class,
        RecentSearch::class,
        PlaybackState::class,
        CustomProxy::class,
        StreamProxy::class,
        VideoSwap::class,
    ],
    version = 40,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun offlineVideos(): OfflineVideosDao
    abstract fun recentEmotes(): RecentEmotesDao
    abstract fun videoPositions(): VideoPositionsDao
    abstract fun localChannelFollows(): LocalChannelFollowsDao
    abstract fun localGameFollows(): LocalGameFollowsDao
    abstract fun bookmarks(): BookmarksDao
    abstract fun bookmarkIgnoredUsers(): BookmarkIgnoredUsersDao
    abstract fun channelSort(): ChannelSortDao
    abstract fun gameSort(): GameSortDao
    abstract fun shownNotifications(): ShownNotificationsDao
    abstract fun notificationUsers(): NotificationUsersDao
    abstract fun translatedChannels(): TranslatedChannelsDao
    abstract fun savedFilters(): SavedFiltersDao
    abstract fun recentSearches(): RecentSearchesDao
    abstract fun playbackStates(): PlaybackStatesDao
    abstract fun customProxies(): CustomProxiesDao
    abstract fun streamProxies(): StreamProxiesDao
    abstract fun videoSwap(): VideoSwapDao
}