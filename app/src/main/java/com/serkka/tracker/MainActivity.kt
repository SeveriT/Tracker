package com.serkka.tracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    private lateinit var stravaViewModel: StravaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize the Database, DAO and Repository
        val database = WorkoutDatabase.getDatabase(applicationContext)
        val workoutDao = database.workoutDao()
        val bodyWeightDao = database.bodyWeightDao()
        val repository = WorkoutRepository(workoutDao, bodyWeightDao)

        // 2. Initialize StravaViewModel immediately to handle intents
        stravaViewModel = ViewModelProvider(this)[StravaViewModel::class.java]
        
        // Initialize ThemeViewModel
        val themeViewModel = ViewModelProvider(this)[ThemeViewModel::class.java]

        // 3. Schedule Automatic Backup
        scheduleBackup()

        setContent {
            val primaryColor by themeViewModel.primaryColor.collectAsState()

            TrackerTheme(primaryColor = primaryColor) {
                // 4. Create the WorkoutViewModel using a Factory
                val workoutViewModel: WorkoutViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return WorkoutViewModel(repository) as T
                        }
                    }
                )

                // 5. Launch the UI
                WorkoutScreen(
                    viewModel = workoutViewModel,
                    stravaViewModel = stravaViewModel,
                    themeViewModel = themeViewModel
                )
            }
        }
        
        // Handle the initial intent if the app was started via a deep link
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Always update the intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        Log.d("MainActivity", "Handling intent: $uri")
        if (uri != null && uri.scheme == "tracker-app" && uri.host == "localhost") {
            val code = uri.getQueryParameter("code")
            Log.d("MainActivity", "Received Strava code: $code")
            if (code != null) {
                stravaViewModel.exchangeCodeForToken(code)
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
