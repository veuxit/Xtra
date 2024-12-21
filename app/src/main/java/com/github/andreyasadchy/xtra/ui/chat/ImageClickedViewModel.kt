package com.github.andreyasadchy.xtra.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.chat.EmoteCard
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageClickedViewModel @Inject constructor(private val graphQLRepository: GraphQLRepository) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val emoteCard = MutableStateFlow<EmoteCard?>(null)

    fun loadEmoteCard(gqlHeaders: Map<String, String>, emoteId: String?) {
        if (emoteCard.value == null) {
            viewModelScope.launch {
                try {
                    val response = graphQLRepository.loadEmoteCard(gqlHeaders, emoteId).data?.emote
                    emoteCard.value = EmoteCard(
                        type = response?.type,
                        subTier = response?.subscriptionTier,
                        bitThreshold = response?.bitsBadgeTierSummary?.threshold,
                        channelLogin = response?.owner?.login,
                        channelName = response?.owner?.displayName,
                    )
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                }
            }
        }
    }
}