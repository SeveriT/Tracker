package com.serkka.tracker

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class StravaViewModel : ViewModel() {
    private val _activities = MutableStateFlow<List<StravaActivity>>(emptyList())
    val activities: StateFlow<List<StravaActivity>> = _activities

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val stravaApi: StravaApi by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d("StravaAPI", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://www.strava.com/api/v3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StravaApi::class.java)
    }

    fun fetchActivities(accessToken: String) {
        val trimmedToken = accessToken.trim()
        if (trimmedToken.isBlank()) {
            _error.value = "Token cannot be empty"
            return
        }

        val authHeader = if (trimmedToken.startsWith("Bearer ", ignoreCase = true)) {
            trimmedToken
        } else {
            "Bearer $trimmedToken"
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Fetch more activities to calculate streaks (e.g., last 200)
                val response = stravaApi.getActivities(authHeader, perPage = 200)
                _activities.value = response
                
                if (response.isEmpty()) {
                    _error.value = "No activities found. Ensure you have activities synced to Strava."
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string() ?: ""
                Log.e("StravaViewModel", "HTTP Error ${e.code()}: $errorBody")
                
                _error.value = when {
                    errorBody.contains("activity:read_permission") -> 
                        "Permission Error: Your token is missing the 'activity:read' scope."
                    e.code() == 401 -> "Unauthorized: Token is invalid or expired."
                    e.code() == 403 -> "Forbidden: Check your API limits or scopes."
                    else -> "Strava Error (${e.code()})"
                }
            } catch (e: Exception) {
                _error.value = "Network Error: Check your connection."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Returns a map of date strings to the list of activity types on that day.
     */
    fun getActivityData(): Map<String, List<String>> {
        return _activities.value.groupBy { it.startDate.substringBefore("T") }
            .mapValues { entry -> entry.value.map { it.type } }
    }

    /**
     * Calculates the weekly streak (consecutive weeks with at least one activity).
     */
    fun getWeeklyStreak(): Int {
        val activityDates = _activities.value
            .map { LocalDate.parse(it.startDate.substringBefore("T")) }
            .distinct()
            .sortedDescending()

        if (activityDates.isEmpty()) return 0

        var currentStreak = 0
        var checkDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        
        // If no activity this week yet, start checking from last week
        val activitiesThisWeek = activityDates.any { !it.isBefore(checkDate) }
        if (!activitiesThisWeek) {
            checkDate = checkDate.minusWeeks(1)
        }

        while (true) {
            val hasActivityInWeek = activityDates.any { 
                (it.isEqual(checkDate) || it.isAfter(checkDate)) && it.isBefore(checkDate.plusWeeks(1))
            }
            if (hasActivityInWeek) {
                currentStreak++
                checkDate = checkDate.minusWeeks(1)
            } else {
                break
            }
        }
        return currentStreak
    }

    fun getTotalStreakActivities(): Int {
        // For simplicity, returning total activities in the list for now
        return _activities.value.size
    }
}
