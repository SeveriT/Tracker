package com.serkka.tracker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

// ---------------------------------------------------------------------------
// Upload state for the workout timer
// ---------------------------------------------------------------------------

sealed class UploadState {
    object Idle    : UploadState()
    object Loading : UploadState()
    data class Success(val activity: StravaActivity) : UploadState()
    data class Error(val message: String)            : UploadState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

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

    private val _profilePicUrl = MutableStateFlow(prefs.getString("profile_pic", "") ?: "")
    val profilePicUrl: StateFlow<String> = _profilePicUrl

    /** Upload state for the workout timer screen. */
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState

    // How many recent activities to fetch full details for (to get calories)
    private val DETAIL_FETCH_LIMIT = 5

    private val stravaApi: StravaApi by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d("StravaAPI", message)
        }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
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

    // Manual fetch only, or triggered by UI
    fun checkAndFetchActivities() {
        val accessToken  = prefs.getString("access_token", "") ?: ""
        val refreshToken = prefs.getString("refresh_token", "") ?: ""
        val expiresAt    = prefs.getLong("expires_at", 0)

        if (accessToken.isNotBlank()) {
            val now = System.currentTimeMillis() / 1000
            if (now < expiresAt - 600) {
                fetchActivitiesWithToken(accessToken)
                fetchProfile(accessToken)
            } else if (refreshToken.isNotBlank()) {
                refreshStravaToken(refreshToken)
            } else {
                fetchActivitiesWithToken(accessToken)
                fetchProfile(accessToken)
            }
        }
    }

    /**
     * Returns a fresh, valid Bearer token string, refreshing via the refresh token
     * if the current access token is within 10 minutes of expiry.
     * Throws if no token is available or refresh fails.
     */
    private suspend fun getValidAccessToken(): String {
        val accessToken  = prefs.getString("access_token",  "") ?: ""
        val refreshToken = prefs.getString("refresh_token", "") ?: ""
        val expiresAt    = prefs.getLong("expires_at", 0)

        if (accessToken.isBlank()) throw IllegalStateException("Not logged in to Strava")

        val now = System.currentTimeMillis() / 1000
        return if (now < expiresAt - 600) {
            // Token still valid
            "Bearer $accessToken"
        } else if (refreshToken.isNotBlank()) {
            // Refresh silently
            Log.d("StravaViewModel", "Token near expiry, refreshing…")
            val response = stravaApi.refreshToken(
                clientId     = STRAVA_CLIENT_ID,
                clientSecret = STRAVA_CLIENT_SECRET,
                refreshToken = refreshToken
            )
            saveTokenResponse(response)
            "Bearer ${response.access_token}"
        } else {
            // No refresh token — use what we have and hope for the best
            "Bearer $accessToken"
        }
    }

    private fun refreshStravaToken(refreshToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = stravaApi.refreshToken(
                    clientId      = STRAVA_CLIENT_ID,
                    clientSecret  = STRAVA_CLIENT_SECRET,
                    refreshToken  = refreshToken
                )
                saveTokenResponse(response)
                fetchActivitiesWithToken(response.access_token)
                fetchProfile(response.access_token)
            } catch (e: Exception) {
                Log.e("StravaViewModel", "Token refresh failed", e)
                _error.value = "Session expired. Please log in again."
                logout()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun saveTokenResponse(response: TokenResponse) {
        prefs.edit().apply {
            putString("access_token",  response.access_token)
            putString("refresh_token", response.refresh_token)
            putLong("expires_at",      response.expires_at)
            response.athlete?.profileMedium?.let { putString("profile_pic", it) }
            apply()
        }
        _savedToken.value = response.access_token
        response.athlete?.profileMedium?.let { _profilePicUrl.value = it }
    }

    fun fetchActivities(accessToken: String) {
        fetchActivitiesWithToken(accessToken)
        fetchProfile(accessToken)
    }

    private fun fetchProfile(accessToken: String) {
        val authHeader = if (accessToken.startsWith("Bearer ", ignoreCase = true)) accessToken else "Bearer $accessToken"
        viewModelScope.launch {
            try {
                val athlete = stravaApi.getAuthenticatedAthlete(authHeader)
                prefs.edit().putString("profile_pic", athlete.profileMedium).apply()
                _profilePicUrl.value = athlete.profileMedium
            } catch (e: Exception) {
                Log.e("StravaViewModel", "Failed to fetch profile", e)
            }
        }
    }

    private fun fetchActivitiesWithToken(accessToken: String) {
        val trimmedToken = accessToken.trim()
        val authHeader   = if (trimmedToken.startsWith("Bearer ", ignoreCase = true)) trimmedToken else "Bearer $trimmedToken"

        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val response = stravaApi.getActivities(authHeader, perPage = 200)

                if (prefs.getString("access_token", "") != trimmedToken) {
                    prefs.edit().putString("access_token", trimmedToken).apply()
                    _savedToken.value = trimmedToken
                }

                if (response.isEmpty()) {
                    _error.value     = "No activities found."
                    _activities.value = emptyList()
                    return@launch
                }

                // Check if the latest activity is the same as the one we already have.
                // If it is, we don\u0027t need to fetch individual details again.
                val latestSavedId = prefs.getLong("latest_activity_id", -1L)
                val latestFetchedId = response.first().id

                if (latestFetchedId == latestSavedId && _activities.value.isNotEmpty()) {
                    Log.d("StravaViewModel", "No new activities. Skipping detail fetch.")
                    _isLoading.value = false
                    return@launch
                }

                // Publish summary immediately
                _activities.value = response

                // Fetch full details only for the most recent activities
                val detailedActivities = response
                    .take(DETAIL_FETCH_LIMIT)
                    .map { activity ->
                        async {
                            try {
                                stravaApi.getActivityDetail(authHeader, activity.id)
                            } catch (e: Exception) {
                                Log.e("StravaViewModel", "Failed to fetch detail for ${activity.id}", e)
                                activity
                            }
                        }
                    }
                    .awaitAll()

                val detailMap     = detailedActivities.associateBy { it.id }
                val finalList     = response.map { activity -> detailMap[activity.id] ?: activity }
                
                _activities.value = finalList
                
                // Save the new latest ID
                prefs.edit().putLong("latest_activity_id", latestFetchedId).apply()

            } catch (e: HttpException) {
                if (e.code() == 401) {
                    val refreshToken = prefs.getString("refresh_token", "") ?: ""
                    if (refreshToken.isNotBlank()) {
                        refreshStravaToken(refreshToken)
                    } else {
                        _error.value = "Session expired. Please log in again."
                    }
                } else {
                    _error.value = "Strava Error (${e.code()})"
                }
            } catch (e: Exception) {
                _error.value = "Network Error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun exchangeCodeForToken(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val response = stravaApi.exchangeToken(
                    clientId     = STRAVA_CLIENT_ID,
                    clientSecret = STRAVA_CLIENT_SECRET,
                    code         = code
                )
                saveTokenResponse(response)
                fetchActivitiesWithToken(response.access_token)
                fetchProfile(response.access_token)
            } catch (e: Exception) {
                Log.e("StravaViewModel", "Code exchange failed", e)
                _error.value = "Code exchange failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Workout timer upload
    // ---------------------------------------------------------------------------

    /**
     * Upload a manually recorded workout to Strava.
     *
     * @param name            Activity title shown on Strava
     * @param sportType       Strava sport_type string, e.g. "Run", "WeightTraining"
     * @param startDateLocal  ISO 8601 local datetime, e.g. "2025-06-01T09:30:00"
     * @param elapsedSeconds  Total workout duration in seconds
     * @param distanceMeters  Optional distance in metres
     */
    fun uploadWorkout(
        name:           String,
        sportType:      String,
        startDateLocal: String,
        elapsedSeconds: Int,
        distanceMeters: Float? = null
    ) {
        if (prefs.getString("access_token", "").isNullOrBlank()) {
            _uploadState.value = UploadState.Error("Not logged in to Strava. Connect in Settings first.")
            return
        }

        viewModelScope.launch {
            _uploadState.value = UploadState.Loading
            try {
                // Refresh token if needed before every upload — fixes "Session expired" on stale tokens
                val authHeader = getValidAccessToken()

                val activity = stravaApi.createActivity(
                    token          = authHeader,
                    name           = name,
                    sportType      = sportType,
                    startDateLocal = startDateLocal,
                    elapsedTime    = elapsedSeconds,
                    distance       = distanceMeters
                )
                _uploadState.value = UploadState.Success(activity)
                
                // Reset the latest activity ID so we force a refresh to see the new activity
                prefs.edit().putLong("latest_activity_id", -1L).apply()
                checkAndFetchActivities()

            } catch (e: HttpException) {
                val msg = when (e.code()) {
                    401  -> "Strava login expired. Please reconnect in Settings."
                    403  -> "Strava permission denied – make sure 'activity:write' scope is granted."
                    else -> "Strava error (${e.code()})"
                }
                _uploadState.value = UploadState.Error(msg)
            } catch (e: IllegalStateException) {
                _uploadState.value = UploadState.Error("Not logged in to Strava. Connect in Settings first.")
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error("Upload failed: ${e.message}")
            }
        }
    }

    /** Reset upload state back to Idle (call after handling Success or Error). */
    fun clearUploadState() {
        _uploadState.value = UploadState.Idle
    }

    // ---------------------------------------------------------------------------
    // Auth / misc
    // ---------------------------------------------------------------------------

    fun logout() {
        prefs.edit().clear().apply()
        _savedToken.value   = ""
        _profilePicUrl.value = ""
        _activities.value   = emptyList()
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
            val weekEnd   = checkDate.plusWeeks(1)

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
