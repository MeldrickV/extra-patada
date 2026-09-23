package com.xtra.kick.repository

import com.xtra.kick.db.NotificationUsersDao
import com.xtra.kick.db.ShownNotificationsDao
import com.xtra.kick.model.NotificationUser
import com.xtra.kick.model.ShownNotification
import com.xtra.kick.model.ui.Stream
import com.xtra.kick.util.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

class NotificationsRepository(
    private val shownNotificationsDao: ShownNotificationsDao,
    private val notificationUsersDao: NotificationUsersDao,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val kickRepository: KickRepository,
) {

    suspend fun getNewStreams(networkLibrary: String?, gqlHeaders: Map<String, String>, helixHeaders: Map<String, String>, kickToken: String? = null): List<Stream> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Stream>()
        if (!kickToken.isNullOrBlank()) {
            runCatching { kickNotifications(kickToken) }.getOrDefault(emptyList()).let { list.addAll(it) }
        }
        notificationUsersDao.getAll().map { it.channelId }.takeIf { it.isNotEmpty() }?.let {
            try {
                gqlQueryLocal(networkLibrary, gqlHeaders, it)
            } catch (e: Exception) {
                if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    try {
                        helixLocal(networkLibrary, helixHeaders, it)
                    } catch (e: Exception) {
                        return@withContext emptyList()
                    }
                } else return@withContext emptyList()
            }.let { list.addAll(it) }
        }
        if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
            try {
                gqlQueryLoad(networkLibrary, gqlHeaders)
            } catch (e: Exception) {
                return@withContext emptyList()
            }.mapNotNull { item ->
                item.takeIf { list.find { it.channelId == item.channelId } == null }
            }.let {
                list.addAll(it)
            }
        }
        val liveList = list.mapNotNull { stream ->
            stream.channelId.takeUnless { it.isNullOrBlank() }?.let { channelId ->
                stream.createdAt.takeUnless { it.isNullOrBlank() }?.let { Instant.parseOrNull(it)?.toEpochMilliseconds()?.takeIf { ms -> ms > 0 } }?.let { createdAt ->
                    ShownNotification(channelId, createdAt)
                }
            }
        }
        val oldList = shownNotificationsDao.getAll()
        oldList.filter { item -> liveList.find { it.channelId == item.channelId } == null }.let {
            shownNotificationsDao.deleteList(it)
        }
        shownNotificationsDao.insertList(liveList)
        val newStreams = liveList.mapNotNull { item ->
            item.takeIf { oldList.find { it.channelId == item.channelId }.let { it == null || it.startedAt < item.startedAt } }?.channelId
        }
        list.filter { it.channelId in newStreams }
    }

    private suspend fun gqlQueryLoad(networkLibrary: String?, gqlHeaders: Map<String, String>): List<Stream> {
        val list = mutableListOf<Stream>()
        var offset: String? = null
        do {
            val response = graphQLRepository.loadQueryUserFollowedStreams(networkLibrary, gqlHeaders, 100, offset)
            val data = response.data!!.user!!.followedLiveUsers!!
            val items = data.edges!!
            items.mapNotNull { item ->
                item?.node?.let {
                    if (it.self?.follower?.notificationSettings?.isEnabled == true) {
                        Stream(
                            id = it.stream?.id,
                            channelId = it.id,
                            channelLogin = it.login,
                            channelName = it.displayName,
                            channelImageURL = it.profileImageURL,
                            gameId = it.stream?.game?.id,
                            gameSlug = it.stream?.game?.slug,
                            gameName = it.stream?.game?.displayName,
                            title = it.stream?.broadcaster?.broadcastSettings?.title,
                            thumbnailURL = it.stream?.previewImageURL,
                            createdAt = it.stream?.createdAt?.toString(),
                            viewerCount = it.stream?.viewersCount,
                            tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name },
                        )
                    } else null
                }
            }.let { list.addAll(it) }
            offset = items.lastOrNull()?.cursor?.toString()
        } while (!items.lastOrNull()?.cursor?.toString().isNullOrBlank() && data.pageInfo?.hasNextPage == true)
        return list
    }

    private suspend fun gqlQueryLocal(networkLibrary: String?, gqlHeaders: Map<String, String>, ids: List<String>): List<Stream> {
        val items = ids.chunked(100).map { list ->
            graphQLRepository.loadQueryUsersStream(networkLibrary, gqlHeaders, list)
        }.flatMap { it.data!!.users!! }
        val list = items.mapNotNull { item ->
            item?.let {
                if (it.stream?.viewersCount != null) {
                    Stream(
                        id = it.stream.id,
                        channelId = it.id,
                        channelLogin = it.login,
                        channelName = it.displayName,
                        channelImageURL = it.profileImageURL,
                        gameId = it.stream.game?.id,
                        gameSlug = it.stream.game?.slug,
                        gameName = it.stream.game?.displayName,
                        title = it.stream.broadcaster?.broadcastSettings?.title,
                        thumbnailURL = it.stream.previewImageURL,
                        createdAt = it.stream.createdAt?.toString(),
                        viewerCount = it.stream.viewersCount,
                        tags = it.stream.freeformTags?.mapNotNull { tag -> tag.name },
                    )
                } else null
            }
        }
        return list
    }

    private suspend fun helixLocal(networkLibrary: String?, helixHeaders: Map<String, String>, ids: List<String>): List<Stream> {
        val items = ids.chunked(100).map {
            helixRepository.getStreams(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val users = items.mapNotNull { it.channelId }.chunked(100).map {
            helixRepository.getUsers(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it
            )
        }.flatMap { it.data }
        val list = items.mapNotNull {
            if (it.viewerCount != null) {
                Stream(
                    id = it.id,
                    channelId = it.channelId,
                    channelLogin = it.channelLogin,
                    channelName = it.channelName,
                    channelImageURL = it.channelId?.let { id ->
                        users.find { user -> user.id == id }?.profileImageURL
                    },
                    gameId = it.gameId,
                    gameName = it.gameName,
                    title = it.title,
                    thumbnailURL = it.thumbnailURL,
                    createdAt = it.startedAt,
                    viewerCount = it.viewerCount,
                    tags = it.tags,
                )
            } else null
        }
        return list
    }

    private suspend fun kickNotifications(kickToken: String): List<Stream> {
        val followed = kickRepository.getFollowedChannels(kickToken, 100, null)
        val enabledIds = notificationUsersDao.getAll().map { it.channelId }.toSet()
        val list = mutableListOf<Stream>()
        for (item in followed) {
            val slug = item.slug
            if (slug.isNullOrBlank()) continue
            val id = "user_${item.id}"
            if (id !in enabledIds) continue
            val channel = runCatching { kickRepository.getChannel(slug) }.getOrNull() ?: continue
            val livestream = channel.livestream ?: continue
            if (livestream.isLive != true) continue
            list.add(
                Stream(
                    id = id,
                    channelId = id,
                    channelLogin = slug,
                    channelName = channel.user?.username ?: item.username,
                    channelImageURL = channel.user?.profilePicture ?: item.profilePicture,
                    gameId = livestream.category?.id,
                    gameSlug = livestream.category?.slug,
                    gameName = livestream.category?.name,
                    title = livestream.sessionTitle,
                    thumbnailURL = channel.user?.profilePicture ?: item.profilePicture,
                    createdAt = livestream.startedAt,
                    viewerCount = livestream.viewerCount,
                )
            )
        }
        return list
    }

    suspend fun saveList(list: List<ShownNotification>) = withContext(Dispatchers.IO) {
        shownNotificationsDao.insertList(list)
    }

    suspend fun getUserById(id: String) = withContext(Dispatchers.IO) {
        notificationUsersDao.getById(id)
    }

    suspend fun saveUser(item: NotificationUser) = withContext(Dispatchers.IO) {
        notificationUsersDao.insert(item)
    }

    suspend fun deleteUser(item: NotificationUser) = withContext(Dispatchers.IO) {
        notificationUsersDao.delete(item)
    }
}
