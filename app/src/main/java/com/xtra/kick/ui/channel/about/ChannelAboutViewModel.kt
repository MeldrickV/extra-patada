package com.xtra.kick.ui.channel.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xtra.kick.XtraApp
import com.xtra.kick.model.kick.KickChannelLeaderboards
import com.xtra.kick.model.ui.ChannelPanel
import com.xtra.kick.repository.GraphQLRepository
import com.xtra.kick.repository.KickRepository
import com.xtra.kick.util.C
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ChannelAboutViewModel(
    private val graphQLRepository: GraphQLRepository,
    private val kickRepository: KickRepository,
) : ViewModel() {

    val integrity = MutableSharedFlow<String?>()

    val description = MutableStateFlow<String?>(null)
    val socialMedias = MutableStateFlow<List<Pair<String?, String?>>?>(null)
    val team = MutableStateFlow<Pair<String?, String?>?>(null)
    val originalName = MutableStateFlow<String?>(null)
    val panels = MutableStateFlow<List<ChannelPanel>?>(null)
    val kickLeaderboards = MutableStateFlow<KickChannelLeaderboards?>(null)

    private var isLoading = false

    fun loadKickLeaderboards(slug: String?) {
        val slugValue = slug?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            kickLeaderboards.value = kickRepository.getChannelLeaderboards(slugValue)
        }
    }

    fun loadAbout(channelId: String?, channelLogin: String?, networkLibrary: String?, gqlHeaders: Map<String, String>, enableIntegrity: Boolean) {
        if ((description.value == null || team.value == null || socialMedias.value == null || panels.value == null) && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    val response = graphQLRepository.loadQueryUserAbout(networkLibrary, gqlHeaders, channelId, channelLogin.takeIf { channelId.isNullOrBlank() })
                    if (enableIntegrity) {
                        response.errors?.find { it.message == C.FAILED_INTEGRITY_CHECK }?.let {
                            integrity.emit("refresh")
                            isLoading = false
                            return@launch
                        }
                    }
                    response.data!!.user?.let { user ->
                        description.value = user.description
                        socialMedias.value = user.channel?.socialMedias?.map {
                            it.title to it.url
                        }
                        team.value = user.primaryTeam?.name to user.primaryTeam?.displayName
                        originalName.value = user.subscriptionProducts?.find { it?.tier == "1000" }?.name?.takeIf { it != channelLogin }
                        panels.value = user.panels?.mapNotNull { item ->
                            item?.onDefaultPanel?.let {
                                ChannelPanel(
                                    title = it.title,
                                    imageUrl = it.imageURL,
                                    linkUrl = it.linkURL,
                                    description = it.description,
                                )
                            }
                        }
                    }
                } catch (e: Exception) {

                }
                isLoading = false
            }
        }
    }

    companion object {
        val ChannelAboutViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val xtraModule = application.xtraModule
                ChannelAboutViewModel(xtraModule.graphQLRepository, xtraModule.kickRepository)
            }
        }
    }
}