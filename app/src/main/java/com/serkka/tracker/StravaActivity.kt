package com.serkka.tracker

import com.google.gson.annotations.SerializedName

data class StravaActivity(
    val id: Long,
    val name: String,
    @SerializedName("start_date_local") val startDate: String, // ISO 8601 format
    val type: String,
    val distance: Float,
    val calories: Float = 0f,
    @SerializedName("moving_time") val movingTime: Int
)
