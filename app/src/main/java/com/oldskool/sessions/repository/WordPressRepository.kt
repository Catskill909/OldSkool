package com.oldskool.sessions.repository

import com.oldskool.sessions.data.WordPressPost
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

interface WordPressApiService {
    @GET("wp-json/wp/v2/posts/")
    suspend fun getPosts(): List<WordPressPost>
}

class WordPressRepository {
    companion object {
        private const val BASE_URL = "https://rarefunk.com/oss/"
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(WordPressApiService::class.java)

    suspend fun getPosts(): List<WordPressPost> {
        return apiService.getPosts()
    }
}
