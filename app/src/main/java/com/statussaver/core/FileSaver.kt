package com.statussaver.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * THE ACTION: Saves status files to Downloads folder
 */
class FileSaver(private val context: Context) {

    private val TAG = "FileSaver"

    /**
     * Main function: Saves a media item to Downloads/WhatsAppStatus/
     * Returns: Success or failure
     */
    fun saveToDownloads(item: MediaItem): Boolean {
        return try {
            Log.d(TAG, "Attempting to save: ${item.fileName}")
            Log.d(TAG, "Android version: ${Build.VERSION.SDK_INT}")
            
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
                    "${Environment.DIRECTORY_DOWNLOADS}/WhatsAppStatus")
            }

            val collection = when (item.type) {
                MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, contentValues)
            
            if (uri == null) {
                Log.e(TAG, "Failed to create MediaStore entry")
                return false
            }
            
            Log.d(TAG, "Created MediaStore URI: $uri")

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(File(item.path)).use { inputStream ->
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
            
            val statusDir = File(downloadsDir, "WhatsAppStatus")
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
            
            // Check if source file exists
            val sourceFile = File(item.path)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file doesn't exist: ${item.path}")
                return false
            }
            
            Log.d(TAG, "Source file size: ${sourceFile.length()} bytes")

            // Copy file
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    val bytes = input.copyTo(output)
                    Log.d(TAG, "Copied $bytes bytes to destination")
                }
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
        val statusDir = File(downloadsDir, "WhatsAppStatus")
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

