package com.xtra.kick.ui.search.channels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.xtra.kick.XtraApp
import com.xtra.kick.model.ui.RecentSearch
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.repository.RecentSearchesRepository
import com.xtra.kick.repository.datasource.SearchChannelsDataSource
import com.xtra.kick.util.C
import com.xtra.kick.util.PlatformState
import com.xtra.kick.util.TwitchApiHelper
import com.xtra.kick.util.prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class ChannelSearchViewModel(
    private val applicationContext: Context,
    private val recentSearchesRepository: RecentSearchesRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val kickRepository: KickRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val recentSearches = recentSearchesRepository.getAll(RecentSearch.TYPE_CHANNEL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = combine(_query, PlatformState.flow) { query, platform ->
        query to platform
    }.flatMapLatest { (query, platform) ->
        Pager(
            PagingConfig(pageSize = 15, prefetchDistance = 5, initialLoadSize = 15)
        ) {
            SearchChannelsDataSource(
                query = query,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixRepository = helixRepository,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                graphQLRepository = graphQLRepository,
                kickRepository = kickRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                platform = platform,
            )
        }.flow
    }.cachedIn(viewModelScope)

    fun setPlatform(platform: String) {
        PlatformState.set(applicationContext, platform)
    }

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
        }
    }

    fun saveRecentSearch(query: String) {
        if (query.isNotBlank()) {
            viewModelScope.launch {
                recentSearchesRepository.getItem(query, RecentSearch.TYPE_CHANNEL)?.let {
                    recentSearchesRepository.delete(it)
                }
                recentSearchesRepository.save(RecentSearch(query, RecentSearch.TYPE_CHANNEL, System.currentTimeMillis()))
            }
        }
    }

    fun deleteRecentSearch(item: RecentSearch) {
        viewModelScope.launch {
            recentSearchesRepository.delete(item)
        }
    }

    companion object {
        val ChannelSearchViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                ChannelSearchViewModel(application.applicationContext, xtraModule.recentSearchesRepository, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.kickRepository)
            }
        }
    }
}