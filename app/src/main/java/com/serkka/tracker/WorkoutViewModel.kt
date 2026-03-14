package com.serkka.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class WorkoutViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    // Automatically updates the UI whenever the database changes
    val allWorkouts: StateFlow<List<Workout>> = repository.getAllWorkouts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBodyWeights: StateFlow<List<BodyWeight>> = repository.getAllBodyWeights()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allNotes: StateFlow<List<Note>> = repository.getAllNotes()
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

    // Body Weight operations

    var weightInput by mutableStateOf("") // The state tied to your TextField
    fun prepareNewEntry() {
        weightInput = ""
        viewModelScope.launch {
            val lastEntry = repository.getLastWeight()
            // If a previous entry exists, set the input to that value, else keep it empty
            weightInput = lastEntry?.weight?.let { w ->
                if (w % 1 == 0f) w.toInt().toString() else w.toString()
            } ?: ""
        }
    }

    fun addBodyWeight(weight: Float, dateMillis: Long, notes: String = "") {
        viewModelScope.launch {
            repository.addBodyWeight(BodyWeight(date = dateMillis, weight = weight, notes = notes))
        }
    }

    fun updateBodyWeight(bodyWeight: BodyWeight) {
        viewModelScope.launch {
            repository.updateBodyWeight(bodyWeight)
        }
    }

    fun deleteBodyWeight(bodyWeight: BodyWeight) {
        viewModelScope.launch {
            repository.deleteBodyWeight(bodyWeight)
        }
    }

    // Note operations
    fun addNote(title: String, content: String, dateMillis: Long) {
        viewModelScope.launch {
            repository.addNote(Note(title = title, content = content, date = dateMillis))
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

}
