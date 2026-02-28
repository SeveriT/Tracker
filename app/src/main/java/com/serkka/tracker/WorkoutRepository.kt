package com.serkka.tracker

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val workoutDao: WorkoutDao) {

    fun getAllWorkouts(): Flow<List<Workout>> {
        return workoutDao.getAllWorkouts()
    }

    suspend fun addWorkout(workout: Workout) {
        workoutDao.insertWorkout(workout)
    }

    suspend fun updateWorkout(workout: Workout) {
        workoutDao.updateWorkout(workout)
    }

    suspend fun deleteWorkout(workout: Workout) {
        workoutDao.deleteWorkout(workout)
    }
}
