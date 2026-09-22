package com.xtra.kick.ui.saved.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.xtra.kick.XtraApp
import com.xtra.kick.model.ui.SavedFilter
import com.xtra.kick.repository.SavedFiltersRepository
import kotlinx.coroutines.launch

class FiltersViewModel(
    private val savedFiltersRepository: SavedFiltersRepository,
) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30),
    ) {
        savedFiltersRepository.getAll()
    }.flow.cachedIn(viewModelScope)

    fun delete(item: SavedFilter) {
        viewModelScope.launch {
            savedFiltersRepository.delete(item)
        }
    }

    companion object {
        val FiltersViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                FiltersViewModel(xtraModule.savedFiltersRepository)
            }
        }
    }
}