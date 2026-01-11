package com.statussaver.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * THE ACTION: Saves status files to Downloads folder
 */
class FileSaver(private val context: Context) {

    private val TAG = "FileSaver"

    /**
     * Main function: Saves a media item to Downloads/Status/
     * Returns: Success or failure
     */
    fun saveToDownloads(item: MediaItem): Boolean {
        return try {
            Log.d(TAG, "Attempting to save: ${item.fileName}")
            Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")
            Log.d(TAG, "Item path: ${item.path}")
            Log.d(TAG, "Item URI: ${item.uri}")

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage)
                Log.d(TAG, "Using MediaStore method")
                saveUsingMediaStore(item)
            } else {
                // Android 9 and below
                Log.d(TAG, "Using FileSystem method")
                saveUsingFileSystem(item)
            }

            Log.d(TAG, "Save result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Save failed with exception", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Android 10+ method using MediaStore
     */
    private fun saveUsingMediaStore(item: MediaItem): Boolean {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(item.type))
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Status")
            }

            val collection = when (item.type) {
                MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val resolver = context.contentResolver
            val destUri = resolver.insert(collection, contentValues)

            if (destUri == null) {
                Log.e(TAG, "Failed to create MediaStore entry")
                return false
            }

            Log.d(TAG, "Created MediaStore URI: $destUri")

            // Get source URI
            val sourceUri = item.uri ?: Uri.parse(item.path)

            // Copy using content resolver
            resolver.openOutputStream(destUri)?.use { outputStream ->
                resolver.openInputStream(sourceUri)?.use { inputStream ->
                    val bytes = inputStream.copyTo(outputStream)
                    Log.d(TAG, "Copied $bytes bytes")
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore save failed", e)
            return false
        }
    }

    /**
     * Android 9 and below - direct file copy
     */
    private fun saveUsingFileSystem(item: MediaItem): Boolean {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

            Log.d(TAG, "Downloads directory: ${downloadsDir?.absolutePath}")

            if (downloadsDir == null || !downloadsDir.exists()) {
                Log.e(TAG, "Downloads directory doesn't exist or is null")
                return false
            }

            val statusDir = File(downloadsDir, "Status")
            Log.d(TAG, "Status directory: ${statusDir.absolutePath}")

            // Create folder if it doesn't exist
            if (!statusDir.exists()) {
                val created = statusDir.mkdirs()
                Log.d(TAG, "Created status directory: $created")
                if (!created) {
                    Log.e(TAG, "Failed to create status directory")
                    return false
                }
            }

            val destFile = File(statusDir, item.fileName)
            Log.d(TAG, "Destination file: ${destFile.absolutePath}")

            // Get source URI
            val sourceUri = item.uri ?: Uri.parse(item.path)
            
            // Copy using content resolver for URI, or direct file for path
            if (item.uri != null || item.path.startsWith("content://")) {
                // Use ContentResolver
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val bytes = input.copyTo(output)
                        Log.d(TAG, "Copied $bytes bytes via URI")
                    }
                }
            } else {
                // Legacy: Direct file copy
                val sourceFile = File(item.path)
                if (!sourceFile.exists()) {
                    Log.e(TAG, "Source file doesn't exist: ${item.path}")
                    return false
                }
                sourceFile.copyTo(destFile, overwrite = true)
                Log.d(TAG, "Copied via direct file")
            }

            if (!destFile.exists()) {
                Log.e(TAG, "Destination file was not created")
                return false
            }

            Log.d(TAG, "Destination file size: ${destFile.length()} bytes")

            // Notify media scanner
            notifyMediaScanner(destFile.absolutePath)

            Log.d(TAG, "Save completed successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "FileSystem save failed", e)
            return false
        }
    }

    /**
     * Get MIME type for media type
     */
    private fun getMimeType(type: MediaType): String {
        return when (type) {
            MediaType.IMAGE -> "image/*"
            MediaType.VIDEO -> "video/*"
            MediaType.AUDIO -> "audio/*"
        }
    }

    /**
     * Notify media scanner (for Android 9 and below)
     */
    private fun notifyMediaScanner(path: String) {
        try {
            val file = File(path)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
            }
            context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                values
            )
            Log.d(TAG, "Media scanner notified")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify media scanner", e)
            e.printStackTrace()
        }
    }

    /**
     * Check if file already exists in Downloads
     */
    fun isAlreadySaved(item: MediaItem): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val statusDir = File(downloadsDir, "Status")
        val file = File(statusDir, item.fileName)
        return file.exists()
    }

    /**
     * Batch save multiple items
     * Returns: Number of successfully saved items
     */
    fun batchSave(items: List<MediaItem>): Int {
        var successCount = 0
        items.forEach { item ->
            if (saveToDownloads(item)) {
                successCount++
            }
        }
        return successCount
    }
}
