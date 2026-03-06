package com.serkka.tracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Workout::class, BodyWeight::class, Note::class], version = 6, exportSchema = false)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun bodyWeightDao(): BodyWeightDao

    companion object {
        @Volatile
        private var Instance: WorkoutDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `body_weights` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `date` INTEGER NOT NULL, 
                        `weight` REAL NOT NULL, 
                        `notes` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `content` TEXT NOT NULL, 
                        `date` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): WorkoutDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout_db")
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
