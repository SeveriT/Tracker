package com.serkka.tracker

import android.util.Log
import com.google.api.client.http.FileContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Collections

class GoogleDriveHelper(private val driveService: Drive) {

    /**
     * Uploads or updates the database file on Google Drive in a specific folder.
     */
    suspend fun uploadFile(
        localFile: java.io.File,
        mimeType: String,
        driveFileName: String,
        folderName: String = "GymTracker Backups"
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Get or create the folder
            val folderId = findOrCreateFolder(folderName) ?: return@withContext null

            val metadata = File()
                .setName(driveFileName)
                .setMimeType(mimeType)
                .setParents(Collections.singletonList(folderId))

            val content = FileContent(mimeType, localFile)

            // 2. Check if file already exists IN THAT FOLDER to update it
            val existingFileId = findFileByName(driveFileName, folderId)
            
            val googleFile = if (existingFileId != null) {
                // Update existing file — setFields ensures id is always returned
                driveService.files().update(existingFileId, null, content)
                    .setFields("id").execute()
            } else {
                // Create new file
                driveService.files().create(metadata, content)
                    .setFields("id").execute()
            }

            Log.d("GoogleDriveHelper", "Drive upload successful: ${googleFile.id}")
            return@withContext googleFile.id
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Drive upload failed", e)
            return@withContext null
        }
    }

    private fun findOrCreateFolder(name: String): String? {
        return try {
            val result = driveService.files().list()
                .setQ("name = '$name' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                .setSpaces("drive")
                .execute()

            val folders = result.files
            if (folders != null && folders.size > 0) {
                folders[0].id
            } else {
                // Create folder
                val metadata = File()
                    .setName(name)
                    .setMimeType("application/vnd.google-apps.folder")
                val folder = driveService.files().create(metadata).setFields("id").execute()
                folder.id
            }
        } catch (e: IOException) {
            Log.e("GoogleDriveHelper", "Folder search/creation failed", e)
            null
        }
    }

    private fun findFileByName(name: String, parentId: String): String? {
        return try {
            val result = driveService.files().list()
                .setQ("name = '$name' and '$parentId' in parents and trashed = false")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val files = result.files
            if (files != null && files.size > 0) {
                files[0].id
            } else {
                null
            }
        } catch (e: IOException) {
            Log.e("GoogleDriveHelper", "Drive file search failed", e)
            null
        }
    }
}
