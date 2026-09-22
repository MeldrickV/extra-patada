package com.xtra.kick.ui.following.games

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
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.LocalGameFollowsRepository
import com.xtra.kick.repository.datasource.FollowedGamesDataSource
import com.xtra.kick.util.C
import com.xtra.kick.util.TwitchApiHelper
import com.xtra.kick.util.prefs

class FollowedGamesViewModel(
    applicationContext: Context,
    private val localGameFollowsRepository: LocalGameFollowsRepository,
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
    ) {
        FollowedGamesDataSource(
            localGameFollowsRepository = localGameFollowsRepository,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
            graphQLRepository = graphQLRepository,
            enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
        )
    }.flow.cachedIn(viewModelScope)

    companion object {
        val FollowedGamesViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                FollowedGamesViewModel(application.applicationContext, xtraModule.localGameFollowsRepository, xtraModule.graphQLRepository)
            }
        }
    }
}
