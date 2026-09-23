package com.xtra.kick.ui.search.games

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
import com.xtra.kick.repository.datasource.SearchGamesDataSource
import com.xtra.kick.util.C
import com.xtra.kick.util.TwitchApiHelper
import com.xtra.kick.util.prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class GameSearchViewModel(
    applicationContext: Context,
    private val recentSearchesRepository: RecentSearchesRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val kickRepository: KickRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val recentSearches = recentSearchesRepository.getAll(RecentSearch.TYPE_GAME)

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = _query.flatMapLatest { query ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        ) {
            SearchGamesDataSource(
                query = query,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixRepository = helixRepository,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                graphQLRepository = graphQLRepository,
                kickRepository = kickRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            )
        }.flow
    }.cachedIn(viewModelScope)

    fun setQuery(newQuery: String) {
        if (_query.value != newQuery) {
            _query.value = newQuery
        }
    }

    fun saveRecentSearch(query: String) {
        if (query.isNotBlank()) {
            viewModelScope.launch {
                recentSearchesRepository.getItem(query, RecentSearch.TYPE_GAME)?.let {
                    recentSearchesRepository.delete(it)
                }
                recentSearchesRepository.save(RecentSearch(query, RecentSearch.TYPE_GAME, System.currentTimeMillis()))
            }
        }
    }

    fun deleteRecentSearch(item: RecentSearch) {
        viewModelScope.launch {
            recentSearchesRepository.delete(item)
        }
    }

    companion object {
        val GameSearchViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                GameSearchViewModel(application.applicationContext, xtraModule.recentSearchesRepository, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.kickRepository)
            }
        }
    }
}