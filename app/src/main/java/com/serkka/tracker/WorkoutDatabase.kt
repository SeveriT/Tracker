package com.serkka.tracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Workout::class], version = 3, exportSchema = false)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var Instance: WorkoutDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN weightUnit TEXT NOT NULL DEFAULT 'kg'")
            }
        }

        fun getDatabase(context: Context): WorkoutDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout_db")
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }

        fun resetInstance() {
            Instance = null
        }
    }
}
