package com.oldskool.sessions.repository

import com.oldskool.sessions.data.WordPressPost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WordPressRepository {
    companion object {
        private const val BASE_URL = "https://rarefunk.com/oss/"
        private const val PAGE_SIZE = 10
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(WordPressApiService::class.java)

    fun getPostsStream(): Flow<List<WordPressPost>> = flow {
        var currentPage = 1
        var hasMorePages = true

        while (hasMorePages) {
            val posts = apiService.getPosts(currentPage, PAGE_SIZE)
            if (posts.isNotEmpty()) {
                emit(posts)
                currentPage++
            } else {
                hasMorePages = false
            }
        }
    }

    suspend fun getPostsPage(page: Int): List<WordPressPost> {
        return apiService.getPosts(page, PAGE_SIZE)
    }
}
