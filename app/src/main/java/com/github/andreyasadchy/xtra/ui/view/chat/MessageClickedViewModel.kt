package com.github.andreyasadchy.xtra.ui.view.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageClickedViewModel @Inject constructor(private val repository: ApiRepository) : ViewModel() {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    private val user = MutableLiveData<User?>()
    private var isLoading = false

    fun loadUser(channelId: String, targetId: String? = null, helixClientId: String? = null, helixToken: String? = null, gqlHeaders: Map<String, String>, checkIntegrity: Boolean): MutableLiveData<User?> {
        if (user.value == null && !isLoading) {
            isLoading = true
            viewModelScope.launch {
                try {
                    val u = repository.loadUserMessageClicked(channelId = channelId, targetId = targetId, helixClientId = helixClientId, helixToken = helixToken, gqlHeaders = gqlHeaders, checkIntegrity = checkIntegrity)
                    user.postValue(u)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                } finally {
                    isLoading = false
                }
            }
        }
        return user
    }
}