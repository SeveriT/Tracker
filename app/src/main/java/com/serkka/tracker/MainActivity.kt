package com.serkka.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkka.tracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize the Database and DAO
        val database = WorkoutDatabase.getDatabase(applicationContext)
        val dao = database.workoutDao()

        setContent {
            GymTrackerTheme {
                // 2. Create the ViewModel using a Factory
                val viewModel: WorkoutViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return WorkoutViewModel(dao) as T
                        }
                    }
                )

                // 3. Launch the UI
                WorkoutScreen(viewModel = viewModel)
            }
        }
    }
}