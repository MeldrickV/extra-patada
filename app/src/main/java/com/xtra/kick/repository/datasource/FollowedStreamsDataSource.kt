package com.xtra.kick.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xtra.kick.model.ui.Stream
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.repository.LocalChannelFollowsRepository
import com.xtra.kick.util.C
import com.xtra.kick.util.appendDeduplicated

class FollowedStreamsDataSource(
    private val userId: String?,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val gqlHeaders: Map<String, String>,
    private val graphQLRepository: GraphQLRepository,
    private val helixHeaders: Map<String, String>,
    private val helixRepository: HelixRepository,
    private val enableIntegrity: Boolean,
    private val networkLibrary: String?,
    private val kickRepository: KickRepository,
    private val kickToken: String?,
    private val combinePlatforms: Boolean,
) : PagingSource<Int, Stream>() {
    private var api: String? = null
    private var offset: String? = null
    private var combinedSeen = HashSet<String>()
    private var combinedStarted = false
    private var kickFetched = false
    private var twitchExhausted = false
    private var twitchOffset: String? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return if (combinePlatforms) {
            combinedLoad(params)
        } else if (!offset.isNullOrBlank()) {
            try {
                loadFromApi(params)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        } else {
            val list = mutableListOf<Stream>()
            localChannelFollowsRepository.getAll().mapNotNull { it.userId }.takeIf { it.isNotEmpty() }?.let {
                try {
                    gqlQueryLocal(it)
                } catch (e: Exception) {
                    try {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLocal(it) else throw Exception()
                    } catch (e: Exception) {
                        null
                    }
                }
            }?.let {
                if (it is LoadResult.Error && it.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                    return it
                }
                (it as? LoadResult.Page)?.data?.let { list.addAll(it) }
            }
            val result = if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank() || !kickToken.isNullOrBlank()) {
                try {
                    api = C.KICK
                    loadFromApi(params)
                } catch (e: Exception) {
                    try {
                        api = C.GQL
                        loadFromApi(params)
                    } catch (e: Exception) {
                        try {
                            api = C.GQL_PERSISTED_QUERY
                            loadFromApi(params)
                        } catch (e: Exception) {
                            try {
                                api = C.HELIX
                                loadFromApi(params)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }?.let {
                    if (it is LoadResult.Error && it.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                        return it
                    }
                    it as? LoadResult.Page
                }
            } else null
            result?.data?.forEach { stream ->
                val item = list.find { it.channelId == stream.channelId }
                if (item == null) {
                    list.add(stream)
                }
            }
            list.sortByDescending { it.viewerCount }
            LoadResult.Page(
                data = list,
                prevKey = null,
                nextKey = result?.nextKey
            )
        }
    }

    private suspend fun loadFromApi(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return when (api) {
            C.KICK -> if (!kickToken.isNullOrBlank()) kickLoad(params) else throw Exception()
            C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlQueryLoad(params) else throw Exception()
            C.GQL_PERSISTED_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlLoad(params) else throw Exception()
            C.PLATFORM_BOTH -> if (!kickToken.isNullOrBlank()) kickBothLoad(params) else throw Exception()
            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLoad(params) else throw Exception()
            else -> throw Exception()
        }
    }

    private suspend fun combinedLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        if (params.key == null) {
            combinedStarted = false
            kickFetched = false
            twitchExhausted = false
            twitchOffset = null
            combinedSeen.clear()
        }
        val list = mutableListOf<Stream>()
        if (!combinedStarted) {
            combinedStarted = true
            localChannelFollowsRepository.getAll().mapNotNull { it.userId }.takeIf { it.isNotEmpty() }?.let {
                try {
                    gqlQueryLocal(it)
                } catch (e: Exception) {
                    try {
                        if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLocal(it) else throw Exception()
                    } catch (e: Exception) {
                        null
                    }
                }
            }?.let {
                if (it is LoadResult.Error && it.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                    return it
                }
                (it as? LoadResult.Page)?.data?.forEach { stream ->
                    stream.channelId?.let { id -> if (combinedSeen.add(id)) list.add(stream) }
                }
            }
        }
        val result = try {
            api = C.PLATFORM_BOTH
            loadFromApi(params)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
        if (result is LoadResult.Error) {
            return result
        }
        val page = result as LoadResult.Page
        appendDeduplicated(combinedSeen, list, page.data, { it.channelId ?: it.channelLogin }, Int.MAX_VALUE)
        list.sortByDescending { it.viewerCount }
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = page.nextKey
        )
    }

    private suspend fun kickBothLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val list = mutableListOf<Stream>()
        val max = params.loadSize
        val kickTarget = (max + 1) / 2
        if (!kickFetched) {
            kickFetched = true
            val followed = runCatching {
                kickRepository.getFollowedChannels(kickToken.orEmpty(), kickTarget.coerceAtLeast(1), null)
            }.getOrNull() ?: emptyList()
            for (item in followed) {
                if (list.size >= kickTarget) {
                    break
                }
                val slug = item.slug
                if (slug.isNullOrBlank()) continue
                val channel = runCatching { kickRepository.getChannel(slug) }.getOrNull() ?: continue
                val livestream = channel.livestream
                if (livestream?.isLive != true) continue
                val id = "user_${channel.id}"
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
                        viewerCount = livestream.viewerCount,
                    )
                )
            }
        }
        if (list.size < max && !twitchExhausted) {
            val need = max - list.size
            val response = runCatching {
                helixRepository.getFollowedStreams(
                    networkLibrary = networkLibrary,
                    headers = helixHeaders,
                    userId = userId,
                    limit = need,
                    offset = twitchOffset,
                )
            }.getOrNull()
            if (response?.data?.isNotEmpty() == true) {
                val users = response.data.mapNotNull { it.channelId }.let {
                    runCatching {
                        helixRepository.getUsers(
                            networkLibrary = networkLibrary,
                            headers = helixHeaders,
                            ids = it,
                        ).data
                    }.getOrNull() ?: emptyList()
                }
                val items = response.data.map {
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
                }
                appendDeduplicated(combinedSeen, list, items, { it.channelId ?: it.channelLogin }, max)
                twitchOffset = response.pagination?.cursor
                if (twitchOffset.isNullOrBlank()) {
                    twitchExhausted = true
                }
            } else {
                twitchExhausted = true
            }
        }
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (twitchExhausted) {
                null
            } else {
                (params.key ?: 1) + 1
            }
        )
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val response = graphQLRepository.loadQueryUserFollowedStreams(networkLibrary, gqlHeaders, 100, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user!!.followedLiveUsers!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
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
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        val nextPage = data.pageInfo?.hasNextPage != false
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank() && nextPage) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val response = graphQLRepository.loadFollowedStreams(networkLibrary, gqlHeaders, 100, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.currentUser.followedLiveUsers
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                Stream(
                    id = it.stream?.id,
                    channelId = it.id,
                    channelLogin = it.login,
                    channelName = it.displayName,
                    channelImageURL = it.profileImageURL,
                    gameId = it.stream?.game?.id,
                    gameSlug = it.stream?.game?.slug,
                    gameName = it.stream?.game?.displayName,
                    title = it.stream?.title,
                    thumbnailURL = it.stream?.previewImageURL,
                    createdAt = it.stream?.createdAt,
                    viewerCount = it.stream?.viewersCount,
                    tags = it.stream?.freeformTags?.mapNotNull { tag -> tag.name },
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        val nextPage = data.pageInfo?.hasNextPage != false
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank() && nextPage) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun helixLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val response = helixRepository.getFollowedStreams(
            networkLibrary = networkLibrary,
            headers = helixHeaders,
            userId = userId,
            limit = 100,
            offset = offset,
        )
        val users = response.data.mapNotNull { it.channelId }.let {
            helixRepository.getUsers(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it,
            ).data
        }
        val list = response.data.map {
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
        }
        offset = response.pagination?.cursor
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank()) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun gqlQueryLocal(ids: List<String>): LoadResult<Int, Stream> {
        val items = ids.chunked(100).map { list ->
            graphQLRepository.loadQueryUsersStream(networkLibrary, gqlHeaders, list).also { response ->
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
                }
            }
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
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = null
        )
    }

    private suspend fun helixLocal(ids: List<String>): LoadResult<Int, Stream> {
        val items = ids.chunked(100).map {
            helixRepository.getStreams(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it,
            )
        }.flatMap { it.data }
        val users = items.mapNotNull { it.channelId }.chunked(100).map {
            helixRepository.getUsers(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it,
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
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = null
        )
    }

    private suspend fun kickLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val followed = kickRepository.getFollowedChannels(kickToken.orEmpty(), params.loadSize, offset)
        val list = mutableListOf<Stream>()
        for (item in followed) {
            val slug = item.slug
            if (slug.isNullOrBlank()) continue
            val channel = runCatching { kickRepository.getChannel(slug) }.getOrNull() ?: continue
            val livestream = channel.livestream
            if (livestream?.isLive != true) continue
            val id = "user_${channel.id}"
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
                    viewerCount = livestream.viewerCount,
                )
            )
            if (list.size >= params.loadSize) break
        }
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = null
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Stream>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}