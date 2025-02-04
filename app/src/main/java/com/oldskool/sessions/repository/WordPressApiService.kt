package com.oldskool.sessions.repository

import com.oldskool.sessions.data.WordPressPost
import retrofit2.http.GET
import retrofit2.http.Query

interface WordPressApiService {
    @GET("wp-json/wp/v2/posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 10,
        @Query("_fields") fields: String = "id,title,excerpt,date,x_featured_media_large,x_metadata"
    ): List<WordPressPost>
}
