package com.serkka.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(private val repository: WorkoutRepository) : ViewModel() {

    // Automatically updates the UI whenever the database changes
    val allWorkouts: StateFlow<List<Workout>> = repository.getAllWorkouts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addWorkout(exercise: String, sets: Int, reps: Int, weight: Float, dateMillis: Long, isPersonalBest: Boolean, weightUnit: String = "kg", notes: String = "") {
        viewModelScope.launch {
            val newWorkout = Workout(
                exerciseName = exercise,
                sets = sets,
                reps = reps,
                weight = weight,
                date = dateMillis,
                isPersonalBest = isPersonalBest,
                weightUnit = weightUnit,
                notes = notes
            )
            repository.addWorkout(newWorkout)
        }
    }

    fun updateWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.updateWorkout(workout)
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            repository.deleteWorkout(workout)
        }
    }
}
