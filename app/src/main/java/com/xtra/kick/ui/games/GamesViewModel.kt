package com.xtra.kick.ui.games

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
import com.xtra.kick.model.ui.Tag
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.HelixRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.repository.datasource.GamesDataSource
import com.xtra.kick.util.C
import com.xtra.kick.util.TwitchApiHelper
import com.xtra.kick.util.prefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class GamesViewModel(
    applicationContext: Context,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
    private val kickRepository: KickRepository,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val filtersText = MutableStateFlow<CharSequence?>(null)

    val tags: Array<Tag>
        get() = filter.value?.tags ?: emptyArray()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        ) {
            GamesDataSource(
                tags = tags.ifEmpty { null }?.mapNotNull { it.id },
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext),
                graphQLRepository = graphQLRepository,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixRepository = helixRepository,
                kickRepository = kickRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            )
        }.flow
    }.cachedIn(viewModelScope)

    fun setFilter(tags: Array<Tag>?) {
        filter.value = Filter(tags)
    }

    class Filter(
        val tags: Array<Tag>?,
    )

    companion object {
        val GamesViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                GamesViewModel(application.applicationContext, xtraModule.graphQLRepository, xtraModule.helixRepository, xtraModule.kickRepository)
            }
        }
    }
}
