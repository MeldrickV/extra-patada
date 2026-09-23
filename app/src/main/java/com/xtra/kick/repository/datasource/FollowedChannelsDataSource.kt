package com.xtra.kick.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xtra.kick.model.kick.toUser
import com.xtra.kick.model.ui.User
import com.xtra.kick.repository.BookmarksRepository
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.repository.LocalChannelFollowsRepository
import com.xtra.kick.repository.OfflineVideosRepository
import com.xtra.kick.util.C

class FollowedChannelsDataSource(
    private val sort: String,
    private val order: String,
    private val userId: String?,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val gqlHeaders: Map<String, String>,
    private val graphQLRepository: GraphQLRepository,
    private val helixHeaders: Map<String, String>,
    private val helixRepository: HelixRepository,
    private val enableIntegrity: Boolean,
    private val networkLibrary: String?,
    private val kickRepository: KickRepository,
    private val kickToken: String?,
) : PagingSource<Int, User>() {
    private var api: String? = null
    private var offset: String? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
        return if (!offset.isNullOrBlank()) {
            val list = mutableListOf<User>()
            val result = try {
                loadFromApi(params)
            } catch (e: Exception) {
                null
            }?.let {
                if (it is LoadResult.Error && it.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                    return it
                }
                it as? LoadResult.Page
            }
            result?.data?.forEach { user ->
                user.accountFollow = true
                list.add(user)
            }
            list.filter { it.lastBroadcast == null || it.profileImageURL == null }.mapNotNull { it.id }.chunked(100).forEach { ids ->
                if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                    val response = graphQLRepository.loadQueryUsersLastBroadcast(networkLibrary, gqlHeaders, ids)
                if (enableIntegrity) {
                    response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
                }
                response.data?.users?.forEach { user ->
                    list.find { it.id == user?.id }?.let { item ->
                        if (item.profileImageURL == null) {
                            item.profileImageURL = user?.profileImageURL
                        }
                        item.lastBroadcast = user?.lastBroadcast?.startedAt?.toString()
                    }
                }
                }
            }
            LoadResult.Page(
                data = list,
                prevKey = null,
                nextKey = result?.nextKey
            )
        } else {
            val list = mutableListOf<User>()
            localChannelFollowsRepository.getAll().let { if (order == "asc") it.asReversed() else it }.forEach {
                list.add(User(
                    id = it.userId,
                    login = it.userLogin,
                    name = it.userName,
                    localFollow = true,
                    platform = if (it.userId?.startsWith(C.KICK_USER_PREFIX) == true) C.KICK else null,
                ))
            }
            var nextKey: Int? = null
            if (!kickToken.isNullOrBlank()) {
                try {
                    api = C.KICK
                    val kickResult = loadFromApi(params)
                    if (kickResult is LoadResult.Error && kickResult.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                        return kickResult
                    }
                    (kickResult as? LoadResult.Page)?.let { page ->
                        nextKey = page.nextKey
                        mergeFollowedUsers(list, page.data)
                    }
                } catch (e: Exception) {
                }
            }
            if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() || !helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) {
                try {
                    api = C.GQL
                    val gqlResult = loadFromApi(params)
                    if (gqlResult is LoadResult.Error && gqlResult.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                        return gqlResult
                    }
                    (gqlResult as? LoadResult.Page)?.let { page ->
                        nextKey = page.nextKey
                        mergeFollowedUsers(list, page.data)
                    }
                } catch (e: Exception) {
                    try {
                        api = C.GQL_PERSISTED_QUERY
                        val pqResult = loadFromApi(params)
                        if (pqResult is LoadResult.Error && pqResult.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                            return pqResult
                        }
                        (pqResult as? LoadResult.Page)?.let { page ->
                            nextKey = page.nextKey
                            mergeFollowedUsers(list, page.data)
                        }
                    } catch (e: Exception) {
                        try {
                            api = C.HELIX
                            val helixResult = loadFromApi(params)
                            if (helixResult is LoadResult.Error && helixResult.throwable.message == C.FAILED_INTEGRITY_CHECK) {
                                return helixResult
                            }
                            (helixResult as? LoadResult.Page)?.let { page ->
                                nextKey = page.nextKey
                                mergeFollowedUsers(list, page.data)
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            backfillLastBroadcast(list)?.let { return it }
            val sorted = if (order == "asc") {
                when (sort) {
                    "created_at" -> list.sortedWith(compareBy(nullsLast()) { it.followedAt })
                    "login" -> list.sortedWith(compareBy(nullsLast()) { it.login })
                    else -> list.sortedWith(compareBy(nullsLast()) { it.lastBroadcast })
                }
            } else {
                when (sort) {
                    "created_at" -> list.sortedWith(compareByDescending(nullsFirst()) { it.followedAt })
                    "login" -> list.sortedWith(compareByDescending(nullsFirst()) { it.login })
                    else -> list.sortedWith(compareByDescending(nullsFirst()) { it.lastBroadcast })
                }
            }
            LoadResult.Page(
                data = sorted,
                prevKey = null,
                nextKey = nextKey
            )
        }
    }

    private suspend fun mergeFollowedUsers(list: MutableList<User>, users: List<User>) {
        users.forEach { user ->
            val item = list.find { it.id == user.id }
            if (item == null) {
                user.accountFollow = true
                list.add(user)
            } else {
                list.remove(item)
                list.add(
                    User(
                        id = item.id,
                        login = user.login ?: item.login,
                        name = user.name ?: item.name,
                        profileImageURL = user.profileImageURL,
                        lastBroadcast = user.lastBroadcast,
                        followedAt = user.followedAt,
                        accountFollow = true,
                        localFollow = item.localFollow,
                        platform = user.platform ?: item.platform,
                    )
                )
                if (item.localFollow && item.id != null && user.login != null && user.name != null
                    && (item.login != user.login || item.name != user.name)) {
                    localChannelFollowsRepository.getById(item.id)?.let {
                        localChannelFollowsRepository.update(it.apply {
                            userLogin = user.login
                            userName = user.name
                        })
                    }
                    offlineVideosRepository.getByUserId(item.id).forEach {
                        offlineVideosRepository.update(it.apply {
                            channelLogin = user.login
                            channelName = user.name
                        })
                    }
                    bookmarksRepository.getByUserId(item.id).forEach {
                        bookmarksRepository.update(it.apply {
                            userLogin = user.login
                            userName = user.name
                        })
                    }
                }
            }
        }
    }

    private suspend fun backfillLastBroadcast(list: MutableList<User>): LoadResult<Int, User>? {
        list.filter {
            it.lastBroadcast == null || it.profileImageURL == null
        }.mapNotNull { it.id }.chunked(100).forEach { ids ->
            if (gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) return@forEach
            val response = graphQLRepository.loadQueryUsersLastBroadcast(networkLibrary, gqlHeaders, ids)
            if (enableIntegrity) {
                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
            }
            response.data?.users?.forEach { user ->
                list.find { it.id == user?.id }?.let { item ->
                    list.remove(item)
                    list.add(
                        User(
                            id = item.id,
                            login = user?.login ?: item.login,
                            name = user?.displayName ?: item.name,
                            profileImageURL = user?.profileImageURL,
                            lastBroadcast = user?.lastBroadcast?.startedAt?.toString(),
                            followedAt = item.followedAt,
                            accountFollow = item.accountFollow,
                            localFollow = item.localFollow,
                            platform = item.platform,
                        )
                    )
                    if (item.localFollow && item.id != null && user?.login != null && user.displayName != null
                        && (item.login != user.login || item.name != user.displayName)) {
                        localChannelFollowsRepository.getById(item.id)?.let {
                            localChannelFollowsRepository.update(it.apply {
                                userLogin = user.login
                                userName = user.displayName
                            })
                        }
                        offlineVideosRepository.getByUserId(item.id).forEach {
                            offlineVideosRepository.update(it.apply {
                                channelLogin = user.login
                                channelName = user.displayName
                            })
                        }
                        bookmarksRepository.getByUserId(item.id).forEach {
                            bookmarksRepository.update(it.apply {
                                userLogin = user.login
                                userName = user.displayName
                            })
                        }
                    }
                }
            }
        }
        return null
    }

    private suspend fun loadFromApi(params: LoadParams<Int>): LoadResult<Int, User> {
        return when (api) {
            C.KICK -> if (!kickToken.isNullOrBlank()) kickLoad(params) else throw Exception()
            C.GQL -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlQueryLoad(params) else throw Exception()
            C.GQL_PERSISTED_QUERY -> if (!gqlHeaders[C.HEADER_TOKEN].isNullOrBlank()) gqlLoad(params) else throw Exception()
            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLoad(params) else throw Exception()
            else -> throw Exception()
        }
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): LoadResult<Int, User> {
        val response = graphQLRepository.loadQueryUserFollowedUsers(networkLibrary, gqlHeaders, 100, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user!!.follows!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                User(
                    id = it.id,
                    login = it.login,
                    name = it.displayName,
                    profileImageURL = it.profileImageURL,
                    lastBroadcast = it.lastBroadcast?.startedAt?.toString(),
                    followedAt = item.followedAt?.toString(),
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

    private suspend fun gqlLoad(params: LoadParams<Int>): LoadResult<Int, User> {
        val response = graphQLRepository.loadFollowedChannels(networkLibrary, gqlHeaders, 100, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user.follows
        val items = data.edges
        val list = items.map { item ->
            item.node.let {
                User(
                    id = it.id,
                    login = it.login,
                    name = it.displayName,
                    profileImageURL = it.profileImageURL,
                    followedAt = it.self?.follower?.followedAt,
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

    private suspend fun helixLoad(params: LoadParams<Int>): LoadResult<Int, User> {
        val response = helixRepository.getUserFollows(
            networkLibrary = networkLibrary,
            headers = helixHeaders,
            userId = userId,
            limit = 100,
            offset = offset,
        )
        val list = response.data.map {
            User(
                id = it.id,
                login = it.login,
                name = it.displayName,
                followedAt = it.followedAt,
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

    private suspend fun kickLoad(params: LoadParams<Int>): LoadResult<Int, User> {
        val list = kickRepository.getFollowedChannels(kickToken.orEmpty(), params.loadSize, offset).map {
            it.toUser()
        }
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = null
        )
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}