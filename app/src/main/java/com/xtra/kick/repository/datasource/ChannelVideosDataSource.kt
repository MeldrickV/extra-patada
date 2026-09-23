package com.xtra.kick.repository.datasource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.xtra.kick.graphql.type.BroadcastType
import com.xtra.kick.graphql.type.VideoSort
import com.xtra.kick.model.kick.toVideo
import com.xtra.kick.model.ui.Video
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.util.C
import com.xtra.kick.util.TwitchApiHelper
import kotlin.math.min

class ChannelVideosDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val gqlQueryType: BroadcastType?,
    private val gqlQuerySort: VideoSort?,
    private val gqlType: String?,
    private val gqlSort: String?,
    private val helixPeriod: String,
    private val helixBroadcastTypes: String,
    private val helixSort: String,
    private val gqlHeaders: Map<String, String>,
    private val graphQLRepository: GraphQLRepository,
    private val helixHeaders: Map<String, String>,
    private val helixRepository: HelixRepository,
    private val enableIntegrity: Boolean,
    private val networkLibrary: String?,
    private val kickRepository: KickRepository,
) : PagingSource<Int, Video>() {
    private var api: String? = null
    private var offset: String? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Video> {
        return if (!offset.isNullOrBlank()) {
            try {
                loadFromApi(params)
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        } else {
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
                            LoadResult.Error(e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadFromApi(params: LoadParams<Int>): LoadResult<Int, Video> {
        return when (api) {
            C.KICK -> if (!channelLogin.isNullOrBlank()) kickLoad(params) else throw Exception()
            C.GQL -> if (helixPeriod == "all") gqlQueryLoad(params) else throw Exception()
            C.GQL_PERSISTED_QUERY -> if (helixPeriod == "all") gqlLoad(params) else throw Exception()
            C.HELIX -> if (!helixHeaders[C.HEADER_TOKEN].isNullOrBlank()) helixLoad(params) else throw Exception()
            else -> throw Exception()
        }
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): LoadResult<Int, Video> {
        val response = graphQLRepository.loadQueryUserVideos(networkLibrary, gqlHeaders, channelId, channelLogin.takeIf { channelId.isNullOrBlank() }, gqlQuerySort, gqlQueryType?.let { listOf(it) }, params.loadSize, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user!!
        val items = data.videos!!.edges!!
        val list = items.mapNotNull { item ->
            item?.node?.let {
                Video(
                    id = it.id,
                    channelId = channelId,
                    channelLogin = data.login,
                    channelName = data.displayName,
                    channelImageURL = data.profileImageURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    title = it.title,
                    thumbnailURL = it.previewThumbnailURL,
                    createdAt = it.createdAt?.toString(),
                    viewCount = it.viewCount,
                    durationSeconds = it.lengthSeconds,
                    type = it.broadcastType?.toString(),
                    animatedPreviewURL = it.animatedPreviewURL,
                )
            }
        }
        offset = items.lastOrNull()?.cursor?.toString()
        val nextPage = data.videos.pageInfo?.hasNextPage != false
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank() && nextPage) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): LoadResult<Int, Video> {
        val response = graphQLRepository.loadChannelVideos(networkLibrary, gqlHeaders, channelLogin, gqlType, gqlSort, params.loadSize, offset)
        if (enableIntegrity) {
            response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let { return LoadResult.Error(Exception(it.message)) }
        }
        val data = response.data!!.user
        val items = data.videos!!.edges
        val list = items.map { item ->
            item.node.let {
                Video(
                    id = it.id,
                    channelId = it.owner?.id,
                    channelLogin = it.owner?.login,
                    channelName = it.owner?.displayName,
                    channelImageURL = it.owner?.profileImageURL,
                    gameId = it.game?.id,
                    gameSlug = it.game?.slug,
                    gameName = it.game?.displayName,
                    title = it.title,
                    thumbnailURL = it.previewThumbnailURL,
                    createdAt = it.publishedAt,
                    viewCount = it.viewCount,
                    durationSeconds = it.lengthSeconds,
                    animatedPreviewURL = it.animatedPreviewURL,
                )
            }
        }
        offset = items.lastOrNull()?.cursor
        val nextPage = data.videos.pageInfo?.hasNextPage != false
        return LoadResult.Page(
            data = list,
            prevKey = null,
            nextKey = if (!offset.isNullOrBlank() && nextPage) {
                (params.key ?: 1) + 1
            } else null
        )
    }

    private suspend fun helixLoad(params: LoadParams<Int>): LoadResult<Int, Video> {
        val response = helixRepository.getVideos(
            networkLibrary = networkLibrary,
            headers = helixHeaders,
            channelId = channelId,
            period = helixPeriod,
            broadcastType = helixBroadcastTypes,
            sort = helixSort,
            limit = params.loadSize,
            offset = offset,
        )
        val list = response.data.map {
            Video(
                id = it.id,
                channelId = channelId,
                channelLogin = it.channelLogin,
                channelName = it.channelName,
                title = it.title,
                thumbnailURL = it.thumbnailURL,
                createdAt = it.createdAt,
                viewCount = it.viewCount,
                durationSeconds = it.duration?.let { duration -> TwitchApiHelper.getDuration(duration) },
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

    private suspend fun kickLoad(params: LoadParams<Int>): LoadResult<Int, Video> {
        val list = kickRepository.getChannelVideos(channelLogin!!)?.mapNotNull { it ->
            if (it.isLive) {
                null
            } else {
                it.toVideo(
                    channelId = channelId,
                    channelLogin = channelLogin,
                )
            }
        } ?: emptyList()
        val page = params.key ?: 0
        val start = page * params.loadSize
        val pageItems = if (start < list.size) list.subList(start, min(start + params.loadSize, list.size)) else emptyList()
        return LoadResult.Page(
            data = pageItems,
            prevKey = null,
            nextKey = if (start + pageItems.size < list.size) page + 1 else null
        )
    }

    override fun getRefreshKey(state: PagingState<Int, Video>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
