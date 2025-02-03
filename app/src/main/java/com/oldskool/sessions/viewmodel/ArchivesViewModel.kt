package com.oldskool.sessions.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oldskool.sessions.data.WordPressPost
import com.oldskool.sessions.repository.WordPressRepository
import kotlinx.coroutines.launch

class ArchivesViewModel : ViewModel() {
    private val repository = WordPressRepository()
    private val _wordPressData = MutableLiveData<List<WordPressPost>>()
    val wordPressData: LiveData<List<WordPressPost>> = _wordPressData

    init {
        fetchWordPressPosts()
    }

    private fun fetchWordPressPosts() {
        viewModelScope.launch {
            try {
                val posts = repository.getPostsPage(1)
                _wordPressData.value = posts
            } catch (e: Exception) {
                // Handle error
                _wordPressData.value = emptyList()
            }
        }
    }
}
