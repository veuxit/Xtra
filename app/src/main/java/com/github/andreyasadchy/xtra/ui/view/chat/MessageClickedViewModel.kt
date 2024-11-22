package com.github.andreyasadchy.xtra.ui.view.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.ApiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageClickedViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    val integrity = MutableStateFlow<String?>(null)

    val user = MutableStateFlow<Pair<User?, Boolean?>?>(null)
    private var isLoading = false

    fun loadUser(channelId: String?, channelLogin: String?, targetId: String?, helixHeaders: Map<String, String>, gqlHeaders: Map<String, String>, checkIntegrity: Boolean) {
        if (user.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    val u = repository.loadUserMessageClicked(channelId, channelLogin, targetId, helixHeaders, gqlHeaders, checkIntegrity)
                    user.value = Pair(u, u == null)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check" && integrity.value == null) {
                        integrity.value = "refresh"
                    } else {
                        user.value = Pair(null, true)
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }
}