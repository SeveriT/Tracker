package com.serkka.tracker

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface StravaApi {
    @GET("athlete/activities")
    suspend fun getActivities(
        @Header("Authorization") token: String,
        @Query("before") before: Long? = null,
        @Query("after") after: Long? = null,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null
    ): List<StravaActivity>
}
