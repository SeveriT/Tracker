package com.serkka.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.serkka.tracker.ui.theme.GymTrackerTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize the Database, DAO and Repository
        val database = WorkoutDatabase.getDatabase(applicationContext)
        val dao = database.workoutDao()
        val repository = WorkoutRepository(dao)

        // 2. Schedule Automatic Backup
        scheduleBackup()

        setContent {
            GymTrackerTheme(dynamicColor = false)  {
                // 3. Create the ViewModel using a Factory
                val viewModel: WorkoutViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return WorkoutViewModel(repository) as T
                        }
                    }
                )

                // 4. Launch the UI
                WorkoutScreen(viewModel = viewModel)
            }
        }
    }

    private fun scheduleBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "AutoBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupRequest
        )
    }
}
