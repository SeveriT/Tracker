package com.serkka.tracker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class BackupManager(private val context: Context) {

    private val dbName = "workout_db"

    suspend fun backupDatabase(destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = WorkoutDatabase.getDatabase(context)
            // Force a Checkpoint
            val cursor = db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
            try {
                if (cursor.moveToFirst()) {
                    Log.d("BackupManager", "Checkpoint result: ${cursor.getInt(0)}")
                }
            } finally {
                cursor.close()
            }

            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists()) {
                copyFile(dbFile, destinationUri)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Backup failed", e)
            false
        }
    }

    suspend fun restoreDatabase(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        val dbPath  = context.getDatabasePath(dbName)
        val walFile = File(dbPath.path + "-wal")
        val shmFile = File(dbPath.path + "-shm")

        // Stage into temp files first so the original DB is untouched if anything fails
        val tempDb  = File(dbPath.path + ".restore_tmp")
        val tempWal = File(dbPath.path + "-wal.restore_tmp")
        val tempShm = File(dbPath.path + "-shm.restore_tmp")

        return@withContext try {
            // 1. Copy incoming URIs to temp files — original DB still intact here
            for (uri in uris) {
                val fileName = getFileName(uri) ?: continue
                val tempTarget = when {
                    fileName.endsWith("-wal") -> tempWal
                    fileName.endsWith("-shm") -> tempShm
                    else                      -> tempDb
                }
                copyFileFromUri(uri, tempTarget)
                Log.d("BackupManager", "Staged $fileName → ${tempTarget.name} (${tempTarget.length()} bytes)")
            }

            if (!tempDb.exists()) {
                Log.e("BackupManager", "No main db file found among the selected URIs")
                return@withContext false
            }

            // 2. All copies succeeded — now close and swap atomically
            WorkoutDatabase.getDatabase(context).close()
            WorkoutDatabase.resetInstance()

            if (dbPath.exists())  dbPath.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            tempDb.renameTo(dbPath)
            if (tempWal.exists()) tempWal.renameTo(walFile)
            if (tempShm.exists()) tempShm.renameTo(shmFile)

            Log.d("BackupManager", "Restore complete")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Restore failed", e)
            // Clean up any temp files so they don't linger
            tempDb.delete(); tempWal.delete(); tempShm.delete()
            false
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) result = cursor.getString(index)
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        return result ?: uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }

    private fun copyFile(sourceFile: File, destUri: Uri) {
        val output = context.contentResolver.openOutputStream(destUri)
        if (output != null) {
            try {
                val input = FileInputStream(sourceFile)
                try {
                    input.copyTo(output)
                } finally {
                    input.close()
                }
            } finally {
                output.close()
            }
        }
    }

    private fun copyFileFromUri(sourceUri: Uri, destFile: File) {
        val input = context.contentResolver.openInputStream(sourceUri)
        if (input != null) {
            try {
                val output = FileOutputStream(destFile)
                try {
                    input.copyTo(output)
                } finally {
                    output.close()
                }
            } finally {
                input.close()
            }
        }
    }
}
