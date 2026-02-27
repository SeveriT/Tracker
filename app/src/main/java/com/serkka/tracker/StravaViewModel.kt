package com.serkka.tracker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
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
import java.time.temporal.TemporalAdjusters

class StravaViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("strava_prefs", Context.MODE_PRIVATE)
    
    private val _activities = MutableStateFlow<List<StravaActivity>>(emptyList())
    val activities: StateFlow<List<StravaActivity>> = _activities

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _savedToken = MutableStateFlow(prefs.getString("access_token", "") ?: "")
    val savedToken: StateFlow<String> = _savedToken

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

    init {
        // Auto-fetch if token exists
        if (_savedToken.value.isNotBlank()) {
            fetchActivities(_savedToken.value)
        }
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
                // Fetch more activities to calculate long streaks
                val response = stravaApi.getActivities(authHeader, perPage = 200)
                _activities.value = response
                
                // Save token on success
                prefs.edit().putString("access_token", trimmedToken).apply()
                _savedToken.value = trimmedToken

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

    fun logout() {
        prefs.edit().remove("access_token").apply()
        _savedToken.value = ""
        _activities.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun getActivityData(): Map<String, List<String>> {
        return _activities.value.groupBy { it.startDate.substringBefore("T") }
            .mapValues { entry -> entry.value.map { it.type } }
    }

    fun getWeeklyStreak(): Int {
        val activityDates = _activities.value
            .map { LocalDate.parse(it.startDate.substringBefore("T")) }
            .distinct()
            .sortedDescending()

        if (activityDates.isEmpty()) return 0

        var currentStreak = 0
        var checkDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        
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

    /**
     * Returns the total number of activities done during the current consecutive weekly streak.
     */
    fun getTotalStreakActivities(): Int {
        val allActivities = _activities.value
        if (allActivities.isEmpty()) return 0

        val activityDates = allActivities
            .map { LocalDate.parse(it.startDate.substringBefore("T")) }
            .distinct()
            .sortedDescending()

        var checkDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val hasActivitiesThisWeek = activityDates.any { !it.isBefore(checkDate) }
        if (!hasActivitiesThisWeek) {
            checkDate = checkDate.minusWeeks(1)
        }

        var totalActivitiesCount = 0

        while (true) {
            val weekStart = checkDate
            val weekEnd = checkDate.plusWeeks(1)
            
            val activitiesInWeek = allActivities.filter { 
                val date = LocalDate.parse(it.startDate.substringBefore("T"))
                (date.isEqual(weekStart) || date.isAfter(weekStart)) && date.isBefore(weekEnd)
            }

            if (activitiesInWeek.isNotEmpty()) {
                totalActivitiesCount += activitiesInWeek.size
                checkDate = checkDate.minusWeeks(1)
            } else {
                break
            }
        }
        return totalActivitiesCount
    }
}
