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
