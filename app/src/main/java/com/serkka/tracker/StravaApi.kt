package com.serkka.tracker

import com.google.gson.annotations.SerializedName
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
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

    @GET("activities/{id}")
    suspend fun getActivityDetail(
        @Header("Authorization") token: String,
        @Path("id") id: Long
    ): StravaActivity

    @GET("athlete")
    suspend fun getAuthenticatedAthlete(
        @Header("Authorization") token: String
    ): StravaAthlete

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): TokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TokenResponse

    /**
     * Create a manual activity on Strava.
     *
     * Required fields:
     *   - name            : display name for the activity
     *   - sportType       : e.g. "Run", "Ride", "WeightTraining" (see Strava docs)
     *   - startDateLocal  : ISO 8601 without timezone, e.g. "2025-06-01T09:30:00"
     *   - elapsedTime     : total duration in seconds
     *
     * Optional fields:
     *   - distance        : metres (omit or pass null to skip)
     *   - description     : free-text description
     */
    @FormUrlEncoded
    @POST("activities")
    suspend fun createActivity(
        @Header("Authorization") token: String,
        @Field("name") name: String,
        @Field("sport_type") sportType: String,
        @Field("start_date_local") startDateLocal: String,
        @Field("elapsed_time") elapsedTime: Int,
        @Field("distance") distance: Float? = null,
        @Field("description") description: String? = null
    ): StravaActivity
}

data class TokenResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_at: Long,
    val expires_in: Long,
    val athlete: StravaAthlete?
)

data class StravaAthlete(
    val id: Long,
    @SerializedName("profile_medium") val profileMedium: String,
    val firstname: String,
    val lastname: String
)
