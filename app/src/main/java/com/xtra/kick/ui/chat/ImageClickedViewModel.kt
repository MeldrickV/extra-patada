package com.xtra.kick.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xtra.kick.XtraApp
import com.xtra.kick.model.chat.EmoteCard
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.util.C
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ImageClickedViewModel(
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()

    val emoteCard = MutableStateFlow<EmoteCard?>(null)

    fun loadEmoteCard(emoteId: String?, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if (emoteCard.value == null) {
            viewModelScope.launch {
                try {
                    val response = if (!emoteId.isNullOrBlank()) {
                        graphQLRepository.loadQueryEmote(networkLibrary, gqlHeaders, emoteId).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("refresh")
                                    return@launch
                                }
                            }
                        }.data!!.emote
                    } else null
                    emoteCard.value = EmoteCard(
                        type = response?.type?.rawValue,
                        subTier = response?.subscriptionTier?.rawValue,
                        bitThreshold = response?.bitsBadgeTierSummary?.threshold,
                        channelLogin = response?.owner?.login,
                        channelName = response?.owner?.displayName,
                    )
                } catch (e: Exception) {
                    try {
                        val response = graphQLRepository.loadEmoteCard(networkLibrary, gqlHeaders, emoteId).also { response ->
                            if (enableIntegrity) {
                                response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                                    integrity.emit("refresh")
                                    return@launch
                                }
                            }
                        }.data?.emote
                        emoteCard.value = EmoteCard(
                            type = response?.type,
                            subTier = response?.subscriptionTier,
                            bitThreshold = response?.bitsBadgeTierSummary?.threshold,
                            channelLogin = response?.owner?.login,
                            channelName = response?.owner?.displayName,
                        )
                    } catch (e: Exception) {

                    }
                }
            }
        }
    }

    companion object {
        val ImageClickedViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                ImageClickedViewModel(xtraModule.graphQLRepository)
            }
        }
    }
}