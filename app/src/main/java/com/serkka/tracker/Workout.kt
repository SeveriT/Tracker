package com.serkka.tracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // Keep it simple with String for now (e.g., "YYYY-MM-DD")
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Float
)