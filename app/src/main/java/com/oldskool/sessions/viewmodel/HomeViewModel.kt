package com.oldskool.sessions.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oldskool.sessions.data.WordPressPost
import com.oldskool.sessions.repository.WordPressRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = WordPressRepository()
    private val _posts = MutableLiveData<List<WordPressPost>>()
    val posts: LiveData<List<WordPressPost>> = _posts
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    private var currentPage = 1
    private var isLastPage = false
    private val postsList = mutableListOf<WordPressPost>()

    init {
        loadFirstPage()
    }

    private fun loadFirstPage() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                postsList.clear()
                currentPage = 1
                isLastPage = false
                
                val newPosts = repository.getPostsPage(currentPage)
                if (newPosts.isEmpty()) {
                    isLastPage = true
                } else {
                    postsList.addAll(newPosts)
                    _posts.value = postsList.toList()
                    currentPage++
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load posts"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadNextPage() {
        if (_isLoading.value == true || isLastPage) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val newPosts = repository.getPostsPage(currentPage)
                if (newPosts.isEmpty()) {
                    isLastPage = true
                } else {
                    postsList.addAll(newPosts)
                    _posts.value = postsList.toList()
                    currentPage++
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load more posts"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadFirstPage()
    }
}
