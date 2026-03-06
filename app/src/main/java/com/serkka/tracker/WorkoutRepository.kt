package com.serkka.tracker

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val workoutDao: WorkoutDao, private val bodyWeightDao: BodyWeightDao) {

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

    // Body Weight operations
    fun getAllBodyWeights(): Flow<List<BodyWeight>> = bodyWeightDao.getAllBodyWeights()

    suspend fun addBodyWeight(bodyWeight: BodyWeight) {
        bodyWeightDao.insert(bodyWeight)
    }

    suspend fun updateBodyWeight(bodyWeight: BodyWeight) {
        bodyWeightDao.update(bodyWeight)
    }

    suspend fun deleteBodyWeight(bodyWeight: BodyWeight) {
        bodyWeightDao.delete(bodyWeight)
    }

    suspend fun getLastWeight(): BodyWeight? {
        return bodyWeightDao.getLastBodyWeight()
    }

    // Note operations
    fun getAllNotes(): Flow<List<Note>> = workoutDao.getAllNotes()

    suspend fun addNote(note: Note) {
        workoutDao.insertNote(note)
    }

    suspend fun updateNote(note: Note) {
        workoutDao.updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        workoutDao.deleteNote(note)
    }
}
