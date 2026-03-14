package com.serkka.tracker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WorkoutTimerViewModel(private val app: Application) : AndroidViewModel(app) {

    // ── Core timer state ──────────────────────────────────────────────────────
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _currentLapSeconds = MutableStateFlow(0L)
    val currentLapSeconds: StateFlow<Long> = _currentLapSeconds

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _hasStarted = MutableStateFlow(false)
    val hasStarted: StateFlow<Boolean> = _hasStarted

    private val _startDateTime = MutableStateFlow<LocalDateTime?>(null)
    val startDateTime: StateFlow<LocalDateTime?> = _startDateTime

    private val _selectedType = MutableStateFlow(workoutActivityTypes[0])
    val selectedType: StateFlow<WorkoutActivityType> = _selectedType

    // ── Upload dialog state (also outlives navigation) ────────────────────────
    private val _showUploadDialog = MutableStateFlow(false)
    val showUploadDialog: StateFlow<Boolean> = _showUploadDialog

    private val _activityName = MutableStateFlow("")
    val activityName: StateFlow<String> = _activityName

    private val _distanceKm = MutableStateFlow("")
    val distanceKm: StateFlow<String> = _distanceKm

    // ── Tick job ──────────────────────────────────────────────────────────────
    private var tickJob: Job? = null

    // Start (or resume) the timer
    fun start() {
        if (!_hasStarted.value) {
            _startDateTime.value = LocalDateTime.now()
            _hasStarted.value = true
            _currentLapSeconds.value = 0L
            startTimerService()
        } else {
            resumeTimerService()
        }
        if (_isRunning.value) return          // already ticking
        _isRunning.value = true
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _elapsedSeconds.value++
                _currentLapSeconds.value++
            }
        }
    }

    fun pause() {
        _isRunning.value = false
        tickJob?.cancel()
        tickJob = null
        pauseTimerService()
    }

    fun toggleRunning() {
        if (_isRunning.value) pause() else start()
    }

    fun lap() {
        if (_isRunning.value) {
            _currentLapSeconds.value = 0L
        }
    }

    // Stop tapping opens the upload dialog and pauses the timer
    fun requestStop() {
        pause()
        val dateStr = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("MMM d"))
        _activityName.value = "${_selectedType.value.label} – $dateStr"
        _showUploadDialog.value = true
    }

    // Cancel upload dialog — resume the timer, workout still in progress
    fun dismissUploadDialog() {
        _showUploadDialog.value = false
        start()
    }

    // Discard the workout entirely (from the dialog)
    fun discard() {
        reset()
    }

    // Reset everything after a successful upload
    fun reset() {
        pause()
        stopTimerService()
        _hasStarted.value       = false
        _elapsedSeconds.value   = 0L
        _currentLapSeconds.value = 0L
        _startDateTime.value    = null
        _activityName.value     = ""
        _distanceKm.value       = ""
        _showUploadDialog.value = false
        _selectedType.value     = workoutActivityTypes[0]
    }

    fun setActivityName(name: String)              { _activityName.value  = name }

    // ── Foreground service helpers ─────────────────────────────────────────────
    private fun serviceIntent(action: String) =
        Intent(app, TimerForegroundService::class.java).apply { this.action = action }

    private fun startTimerService() {
        val intent = serviceIntent(TimerForegroundService.ACTION_START)
            .putExtra(TimerForegroundService.EXTRA_ELAPSED, _elapsedSeconds.value)
        app.startForegroundService(intent)
    }

    private fun pauseTimerService() {
        app.startService(serviceIntent(TimerForegroundService.ACTION_PAUSE))
    }

    private fun resumeTimerService() {
        val intent = serviceIntent(TimerForegroundService.ACTION_RESUME)
            .putExtra(TimerForegroundService.EXTRA_ELAPSED, _elapsedSeconds.value)
        app.startService(intent)
    }

    private fun stopTimerService() {
        app.startService(serviceIntent(TimerForegroundService.ACTION_STOP))
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }
}
