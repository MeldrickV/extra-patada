package com.xtra.kick.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xtra.kick.graphql.type.ClipsPeriod
import com.xtra.kick.model.kick.toClip
import com.xtra.kick.model.ui.Clip
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.util.C
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ChannelClipsDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val gqlQueryPeriod: ClipsPeriod?,
    private val gqlPeriod: String?,
    private val startedAt: String?,
    private val endedAt: String?,
    private val gqlHeaders: Map<String, String>,
    private val graphQLRepository: GraphQLRepository,
    private val helixHeaders: Map<String, String>,
    private val helixRepository: HelixRepository,
    private val enableIntegrity: Boolean,
    private val networkLibrary: String?,
    private val kickRepository: KickRepository,
    private val kickPeriodDays: Int?,
    private val kickSortByViews: Boolean,
) : PagingSource<Int, Clip>() {
    private var api: String? = null
    private var offset: String? = null
    private var kickAllClips: List<Clip>? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Clip> {
        return if (channelId?.startsWith(C.KICK_USER_PREFIX) == true) {
            try {
                kickLoad(params)
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

    private suspend fun loadFromApi(params: LoadParams<Int>): LoadResult<Int, Clip> {
        return when (api) {
            C.KICK -> if (!channelLogin.isNullOrBlank()) kickLoad(params) else throw Exception()
            C.GQL -> gqlQueryLoad(params)
            C.GQL_PERSISTED_QUERY -> gqlLoad(params)
            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLoad(params) else throw Exception()
            else -> throw Exception()
        }
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): LoadResult<Int, Clip> {
        val response = graphQLRepository.loadQueryUserClips(networkLibrary, gqlHeaders, channelId, channelLogin.takeIf { channelId.isNullOrBlank() }, gqlQueryPeriod, params.loadSize, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user!!
        val items = data.clips!!.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Clip(
                    id = it.slug,
                    channelId = channelId,
                    channelLogin = data.login,
                    channelName = data.displayName,
                    channelImageURL = data.profileImageURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    title = it.title,
                    thumbnailURL = it.thumbnailURL,
                    createdAt = it.createdAt?.toString(),
                    viewCount = it.viewCount,
                    durationSeconds = it.durationSeconds,
                    videoId = it.video?.id,
                    videoOffsetSeconds = if (it.videoOffsetSeconds != null && it.durationSeconds != null) {
                        max(it.videoOffsetSeconds - it.durationSeconds, 0) // api is returning wrong offset
                    } else {
                        it.videoOffsetSeconds
                    },
                    videoCreatedAt = it.video?.createdAt?.toString(),
                    videoAnimatedPreviewURL = it.video?.animatedPreviewURL,
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        val nextPage = data.clips.pageInfo?.hasNextPage != false
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank() && nextPage) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): LoadResult<Int, Clip> {
        val response = graphQLRepository.loadChannelClips(networkLibrary, gqlHeaders, channelLogin, gqlPeriod, params.loadSize, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user
        val items = data.clips!!.edges
        val list = items.map { item ->
            item.node.let {
                Clip(
                    id = it.slug,
                    channelId = channelId,
                    channelLogin = it.broadcaster?.login,
                    channelName = it.broadcaster?.displayName,
                    channelImageURL = it.broadcaster?.profileImageURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.name,
                    title = it.title,
                    thumbnailURL = it.thumbnailURL,
                    createdAt = it.createdAt,
                    viewCount = it.viewCount,
                    durationSeconds = it.durationSeconds,
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        val nextPage = data.clips.pageInfo?.hasNextPage != false
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank() && nextPage) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun helixLoad(params: LoadParams<Int>): LoadResult<Int, Clip> {
        val response = helixRepository.getClips(
            networkLibrary = networkLibrary,
            headers = helixHeaders,
            channelId = channelId,
            startedAt = startedAt,
            endedAt = endedAt,
            limit = params.loadSize,
            offset = offset,
        )
        val games = response.data.mapNotNull { it.gameId }.let {
            helixRepository.getGames(
                networkLibrary = networkLibrary,
                headers = helixHeaders,
                ids = it,
            ).data
        }
        val list = response.data.map {
            Clip(
                id = it.id,
                channelId = channelId,
                channelLogin = channelLogin,
                channelName = it.channelName,
                gameId = it.gameId,
                gameName = it.gameId?.let { id ->
                    games.find { game -> game.id == id }?.name
                },
                title = it.title,
                thumbnailURL = it.thumbnailURL,
                createdAt = it.createdAt,
                viewCount = it.viewCount,
                durationSeconds = it.duration?.toInt(),
                videoId = it.videoId,
                videoOffsetSeconds = if (it.vodOffset != null && it.duration != null) {
                    max(it.vodOffset - it.duration.toInt(), 0)
                } else {
                    it.vodOffset
                },
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

    private suspend fun kickLoad(params: LoadParams<Int>): LoadResult<Int, Clip> {
        val channel = runCatching { kickRepository.getChannel(channelLogin!!) }.getOrNull()
        val channelName = channel?.user?.username
        val channelImageURL = channel?.user?.resolvedProfilePicture
        val videosByLivestreamId = runCatching { kickRepository.getChannelVideos(channelLogin!!) }.getOrNull()
            .orEmpty()
            .mapNotNull { video -> video.id.takeIf { it > 0 }?.toString()?.let { it to video } }
            .toMap()
        val all = kickAllClips ?: run {
            val fetched = buildList {
                var cursor: String? = null
                for (attempt in 0 until 4) {
                    val page = runCatching { kickRepository.getChannelClipsPage(channelLogin!!, cursor) }.getOrNull() ?: break
                    addAll(page.clips)
                    cursor = page.cursor
                    if (cursor.isNullOrBlank()) break
                }
            }
            val seen = HashSet<String>()
            val unique = fetched.filter { seen.add(it.id) }
            val mapped = unique.map { clip ->
                val video = clip.livestreamId?.let { videosByLivestreamId[it] }
                clip.toClip(
                    channelId = channelId,
                    channelLogin = channelLogin,
                    channelName = channelName,
                    channelImageURL = channelImageURL,
                    videoId = video?.video?.uuid?.takeIf { it.isNotBlank() },
                    videoCreatedAt = video?.startTime,
                )
            }
            val minCreated = kickPeriodDays?.let { Clock.System.now() - it.days }
            val filtered = mapped.filter { clip ->
                val created = clip.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() }
                created == null || minCreated == null || created >= minCreated.toEpochMilliseconds()
            }
            val sorted = if (kickSortByViews) {
                filtered.sortedByDescending { it.viewCount ?: 0 }
            } else {
                filtered.sortedByDescending { it.createdAt?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L }
            }
            sorted.also { kickAllClips = it }
        }
        val page = params.key ?: 0
        val start = page * params.loadSize
        val pageItems = if (start < all.size) all.subList(start, min(start + params.loadSize, all.size)) else emptyList()
        return LoadResult.Page(
            data = pageItems,
            prevKey = null,
            nextKey = if (start + pageItems.size < all.size) page + 1 else null
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Clip>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
