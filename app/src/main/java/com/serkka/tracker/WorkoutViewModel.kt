package com.serkka.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(private val dao: WorkoutDao) : ViewModel() {

    // Automatically updates the UI whenever the database changes
    val allWorkouts: StateFlow<List<Workout>> = dao.getAllWorkouts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addWorkout(exercise: String, sets: Int, reps: Int, weight: Float, date: String, isPersonalBest: Boolean) {
        viewModelScope.launch {
            val newWorkout = Workout(
                exerciseName = exercise,
                sets = sets,
                reps = reps,
                weight = weight,
                date = date,
                isPersonalBest = isPersonalBest
            )
            dao.insertWorkout(newWorkout)
        }
    }

    fun deleteWorkout(workout: Workout) {
        viewModelScope.launch {
            dao.deleteWorkout(workout)
        }
    }
}
