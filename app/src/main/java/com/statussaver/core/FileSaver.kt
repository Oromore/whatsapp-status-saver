package com.statussaver.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * THE ACTION: Saves status files to Downloads folder
 */
class FileSaver(private val context: Context) {

    /**
     * Main function: Saves a media item to Downloads/WhatsAppStatus/
     * Returns: Success or failure
     */
    fun saveToDownloads(item: MediaItem): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage)
                saveUsingMediaStore(item)
            } else {
                // Android 9 and below
                saveUsingFileSystem(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Android 10+ method using MediaStore
     */
    private fun saveUsingMediaStore(item: MediaItem): Boolean {
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
        val uri = resolver.insert(collection, contentValues) ?: return false

        resolver.openOutputStream(uri)?.use { outputStream ->
            FileInputStream(File(item.path)).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        return true
    }

    /**
     * Android 9 and below - direct file copy
     */
    private fun saveUsingFileSystem(item: MediaItem): Boolean {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        val statusDir = File(downloadsDir, "WhatsAppStatus")

        // Create folder if it doesn't exist
        if (!statusDir.exists()) {
            statusDir.mkdirs()
        }

        val destFile = File(statusDir, item.fileName)

        // Copy file
        FileInputStream(File(item.path)).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        // Notify system about new file
        notifyMediaScanner(destFile.absolutePath)

        return true
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
        } catch (e: Exception) {
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
