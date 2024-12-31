package com.github.andreyasadchy.xtra.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.ApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchPagerViewModel @Inject constructor(
    private val repository: ApiRepository,
) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val userResult = MutableStateFlow<Pair<String?, String?>?>(null)
    private var isLoading = false

    fun loadUserResult(gqlHeaders: Map<String, String>, checkedId: Int, result: String) {
        if (userResult.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    userResult.value = if (checkedId == 0) {
                        repository.loadUserResult(channelId = result, gqlHeaders = gqlHeaders)
                    } else {
                        repository.loadUserResult(channelLogin = result, gqlHeaders = gqlHeaders)
                    }
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
