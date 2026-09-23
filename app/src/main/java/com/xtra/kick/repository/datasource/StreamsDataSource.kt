package com.xtra.kick.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xtra.kick.model.kick.toStream
import com.xtra.kick.graphql.type.Language
import com.xtra.kick.graphql.type.StreamSort
import com.xtra.kick.model.ui.Stream
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.util.C
import com.xtra.kick.util.appendDeduplicated

class StreamsDataSource(
    private val gqlQueryLanguages: List<Language>?,
    private val gqlQuerySort: StreamSort?,
    private val gqlLanguages: List<String>?,
    private val gqlSort: String?,
    private val tags: List<String>?,
    private val gqlHeaders: Map<String, String>,
    private val graphQLRepository: GraphQLRepository,
    private val helixHeaders: Map<String, String>,
    private val helixRepository: HelixRepository,
    private val kickRepository: KickRepository,
    private val enableIntegrity: Boolean,
    private val networkLibrary: String?,
    private val platform: String,
) : PagingSource<Int, Stream>() {
    private var api: String? = null
    private var offset: String? = null
    private var combinedSeen = HashSet<String>()
    private var kickExhausted = false
    private var twitchExhausted = false
    private var kickOffset: String? = null
    private var twitchOffset: String? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Stream> {
        if (platform == C.PLATFORM_KICK) {
            if (!offset.isNullOrBlank()) {
                return try {
                    loadFromApi(params)
                } catch (e: Exception) {
                    LoadResult.Error(e)
                }
            }
            return try {
                api = C.KICK
                loadFromApi(params)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
        return if (platform == C.PLATFORM_BOTH) {
            try {
                api = C.PLATFORM_BOTH
                loadFromApi(params)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        } else if (!offset.isNullOrBlank()) {
            try {
                loadFromApi(params)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        } else {
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
                        LoadResult.Error(e)
                    }
                }
            }
        }
    }

    private suspend fun loadFromApi(params: LoadParams<Int>): LoadResult<Int, Stream> {
        return when (api) {
            C.KICK -> kickLoad(params)
            C.GQL -> gqlQueryLoad(params)
            C.GQL_PERSISTED_QUERY -> gqlLoad(params)
            C.PLATFORM_BOTH -> bothLoad(params)
            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank() && tags.isNullOrEmpty() && gqlQueryLanguages.isNullOrEmpty() && gqlLanguages.isNullOrEmpty()) helixLoad(params) else throw Exception()
            else -> throw Exception()
        }
    }

    private suspend fun bothLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        if (params.key == null) {
            combinedSeen.clear()
            kickOffset = null
            twitchOffset = null
            kickExhausted = false
            twitchExhausted = false
        }
        val list = mutableListOf<Stream>()
        val max = params.loadSize
        val kickTarget = (max + 1) / 2
        if (!kickExhausted) {
            var remaining = kickTarget
            var attempts = 0
            while (remaining > 0 && !kickExhausted && attempts < 5) {
                attempts++
                val response = runCatching {
                    kickRepository.getLivestreams(remaining.coerceAtLeast(1), kickOffset)
                }.getOrNull()
                if (response == null) {
                    kickExhausted = true
                    break
                }
                val items = response.livestreams.map { it.toStream() }.takeIf { streams ->
                    streams.any { it.channelId != null || it.channelLogin != null }
                } ?: emptyList()
                appendDeduplicated(combinedSeen, list, items, { combinedStreamKey(it) }, max)
                remaining -= items.size
                kickOffset = response.nextCursor
                if (kickOffset.isNullOrBlank()) {
                    kickExhausted = true
                }
            }
            if (attempts >= 5 && !kickExhausted) {
                kickExhausted = true
            }
        }
        if (list.size < max && !twitchExhausted) {
            val need = max - list.size
            val response = runCatching {
                helixRepository.getStreams(
                    networkLibrary = networkLibrary,
                    headers = helixHeaders,
                    limit = need,
                    offset = twitchOffset
                )
            }.getOrNull()
            if (response != null) {
                val users = response.data.mapNotNull { it.channelId }.let {
                    runCatching {
                        helixRepository.getUsers(
                            networkLibrary = networkLibrary,
                            headers = helixHeaders,
                            ids = it
                        ).data
                    }.getOrNull() ?: emptyList()
                }
                val items = response.data.mapNotNull {
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
                    ).takeIf { stream ->
                        stream.channelId != null || stream.channelLogin != null
                    }
                }
                appendDeduplicated(combinedSeen, list, items, { combinedStreamKey(it) }, max)
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
            nextKey = if (kickExhausted && twitchExhausted) {
                null
            } else {
                (params.key ?: 1) + 1
            }
        )
    }

    private fun combinedStreamKey(stream: Stream): String {
        return "${stream.platform ?: ""}|${stream.channelId ?: stream.channelLogin}"
    }

    private suspend fun kickLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val response = kickRepository.getLivestreams(params.loadSize, offset)
        val list = response.livestreams.map { it.toStream() }.takeIf { streams ->
            streams.any { it.channelId != null || it.channelLogin != null }
        } ?: emptyList()
        offset = response.nextCursor
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank()) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): LoadResult<Int, Stream> {
        val response = graphQLRepository.loadQueryTopStreams(networkLibrary, gqlHeaders, gqlQuerySort, tags, gqlQueryLanguages, params.loadSize, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.streams!!
        val items = data.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Stream(
                    id = it.id,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    channelImageURL = it.broadcaster?.profileImageURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    title = it.broadcaster?.broadcastSettings?.title,
                    thumbnailURL = it.previewImageURL,
                    createdAt = it.createdAt?.toString(),
                    viewerCount = it.viewersCount,
                    tags = it.freeformTags?.mapNotNull { tag -> tag.name },
                ).takeIf { stream ->
                    stream.channelId != null || stream.channelLogin != null
                }
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
        val response = graphQLRepository.loadTopStreams(networkLibrary, gqlHeaders, gqlSort, tags, gqlLanguages, params.loadSize, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.streams
        val items = data.edges
        val list = items.mapNotNull { item ->
            item.node.let {
                Stream(
                    id = it.id,
                    channelId = it.broadcaster?.id,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    channelImageURL = it.broadcaster?.profileImageURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    title = it.title,
                    thumbnailURL = it.previewImageURL,
                    createdAt = it.createdAt,
                    viewerCount = it.viewersCount,
                    tags = it.freeformTags?.mapNotNull { tag -> tag.name },
                ).takeIf { stream ->
                    stream.channelId != null || stream.channelLogin != null
                }
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
        val response = helixRepository.getStreams(
            networkLibrary = networkLibrary,
            headers = helixHeaders,
            limit = params.loadSize,
            offset = offset
        )
        val users = response.data.mapNotNull { it.channelId }.let {
            helixRepository.getUsers(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it
            ).data
        }
        val list = response.data.mapNotNull {
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
            ).takeIf { stream ->
                stream.channelId != null || stream.channelLogin != null
            }
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

    override fun getRefreshKey(state: PagingState<Int, Stream>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
