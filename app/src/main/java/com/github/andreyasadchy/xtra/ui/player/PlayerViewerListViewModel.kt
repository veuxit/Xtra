package com.github.andreyasadchy.xtra.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.ChannelViewerList
import com.github.andreyasadchy.xtra.repository.ApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewerListViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    private val _viewerList = MutableStateFlow<ChannelViewerList?>(null)
    val viewerList: StateFlow<ChannelViewerList?> = _viewerList
    private var isLoading = false

    fun loadViewerList(gqlHeaders: Map<String, String>, channelLogin: String?) {
        if (_viewerList.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    _viewerList.value = repository.loadChannelViewerList(gqlHeaders, channelLogin)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }
}