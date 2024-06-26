package com.github.andreyasadchy.xtra.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.repository.ApiRepository
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchPagerViewModel @Inject constructor(
    private val repository: ApiRepository) : ViewModel() {

    private val _integrity by lazy { SingleLiveEvent<Boolean>() }
    val integrity: LiveData<Boolean>
        get() = _integrity

    val userResult = MutableLiveData<Pair<String?, String?>?>()
    private var isLoading = false

    fun loadUserResult(gqlHeaders: Map<String, String>, checkedId: Int, result: String) {
        if (!isLoading) {
            isLoading = true
            userResult.value = null
            viewModelScope.launch {
                try {
                    val get = if (checkedId == 0) {
                        repository.loadUserResult(channelId = result, gqlHeaders = gqlHeaders)
                    } else {
                        repository.loadUserResult(channelLogin = result, gqlHeaders = gqlHeaders)
                    }
                    userResult.postValue(get)
                } catch (e: Exception) {
                    if (e.message == "failed integrity check") {
                        _integrity.postValue(true)
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }
}
