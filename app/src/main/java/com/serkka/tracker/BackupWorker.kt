package com.serkka.tracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        Log.d("BackupWorker", "Starting scheduled backup...")
        
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
        if (account == null) {
            Log.w("BackupWorker", "No Google account signed in. Skipping auto-backup.")
            return@withContext androidx.work.ListenableWorker.Result.failure()
        }

        return@withContext try {
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext,
                Collections.singleton(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Tracker").build()

            val driveHelper = GoogleDriveHelper(driveService)

            // Force a checkpoint to ensure data is in the .db file
            val db = WorkoutDatabase.getDatabase(applicationContext)
            db.query(androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { cursor ->
                cursor.moveToFirst()
            }

            val dbFile = applicationContext.getDatabasePath("workout_db")
            if (dbFile.exists()) {
                val fileId = driveHelper.uploadFile(
                    localFile = dbFile,
                    mimeType = "application/x-sqlite3",
                    driveFileName = "workout_backup_auto.db"
                )
                if (fileId != null) {
                    Log.d("BackupWorker", "Auto-backup successful: $fileId")
                    applicationContext
                        .getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                        .edit().putLong("last_backup_ms", System.currentTimeMillis()).apply()
                    androidx.work.ListenableWorker.Result.success()
                } else {
                    Log.e("BackupWorker", "Drive upload failed")
                    androidx.work.ListenableWorker.Result.retry()
                }
            } else {
                Log.e("BackupWorker", "Database file not found")
                androidx.work.ListenableWorker.Result.failure()
            }
        } catch (e: Exception) {
            Log.e("BackupWorker", "Auto-backup crashed", e)
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
