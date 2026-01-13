package com.statussaver.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * THE BRAIN: Scans WhatsApp status folders
 * SIMPLIFIED VERSION: Uses direct file access with MANAGE_EXTERNAL_STORAGE
 */
class StatusScanner(private val context: Context) {

    private val TAG = "StatusScanner"

    // WhatsApp status paths - try all possible locations
    private val whatsappPaths = listOf(
        // New path (Android 11+)
        "Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
        // Legacy path (Android 10 and below)
        "WhatsApp/Media/.Statuses"
    )

    private val whatsappBusinessPaths = listOf(
        // New path (Android 11+)
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
        // Legacy path (Android 10 and below)
        "WhatsApp Business/Media/.Statuses"
    )

    /**
     * Main function: Scans all WhatsApp folders and returns organized media
     * Returns: Map with keys "IMAGE", "VIDEO", "AUDIO"
     */
    fun scanAllStatus(): Map<String, List<MediaItem>> {
        val allMedia = mutableListOf<MediaItem>()

        Log.d(TAG, "=== Starting Status Scan ===")
        Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT}")

        // Get external storage directory
        val storageDir = Environment.getExternalStorageDirectory()
        Log.d(TAG, "Storage directory: ${storageDir.absolutePath}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Check if we have MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                Log.e(TAG, "MANAGE_EXTERNAL_STORAGE permission not granted!")
                return emptyMap()
            }
        }

        // Scan regular WhatsApp
        Log.d(TAG, "Scanning WhatsApp...")
        whatsappPaths.forEach { path ->
            allMedia.addAll(scanFolder(storageDir, path, "WhatsApp"))
        }

        // Scan WhatsApp Business
        Log.d(TAG, "Scanning WhatsApp Business...")
        whatsappBusinessPaths.forEach { path ->
            allMedia.addAll(scanFolder(storageDir, path, "WhatsApp Business"))
        }

        Log.d(TAG, "Total media found: ${allMedia.size}")

        // Remove duplicates by fileName
        val uniqueMedia = allMedia.distinctBy { it.fileName }
        Log.d(TAG, "Unique media: ${uniqueMedia.size}")

        // Group by type
        return mapOf(
            "IMAGE" to uniqueMedia.filter { it.type == MediaType.IMAGE },
            "VIDEO" to uniqueMedia.filter { it.type == MediaType.VIDEO },
            "AUDIO" to uniqueMedia.filter { it.type == MediaType.AUDIO }
        )
    }

    /**
     * Scan a specific folder path
     */
    private fun scanFolder(storageDir: File, relativePath: String, source: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            val statusFolder = File(storageDir, relativePath)
            
            Log.d(TAG, "Checking folder: ${statusFolder.absolutePath}")
            Log.d(TAG, "Folder exists: ${statusFolder.exists()}")
            Log.d(TAG, "Is directory: ${statusFolder.isDirectory}")
            Log.d(TAG, "Can read: ${statusFolder.canRead()}")

            if (!statusFolder.exists()) {
                Log.d(TAG, "Folder doesn't exist: ${statusFolder.absolutePath}")
                return emptyList()
            }

            if (!statusFolder.isDirectory) {
                Log.d(TAG, "Path is not a directory: ${statusFolder.absolutePath}")
                return emptyList()
            }

            if (!statusFolder.canRead()) {
                Log.e(TAG, "Cannot read folder: ${statusFolder.absolutePath}")
                return emptyList()
            }

            val files = statusFolder.listFiles()
            
            if (files == null) {
                Log.e(TAG, "listFiles() returned null for: ${statusFolder.absolutePath}")
                return emptyList()
            }

            Log.d(TAG, "Found ${files.size} files in $source at ${statusFolder.absolutePath}")

            files.forEach { file ->
                if (file.isFile && !file.name.startsWith(".nomedia")) {
                    val mediaType = getMediaType(file.name)
                    
                    if (mediaType != null) {
                        Log.d(TAG, "Found media: ${file.name} (${mediaType.name}, ${file.length()} bytes)")

                        mediaList.add(
                            MediaItem(
                                path = file.absolutePath,
                                fileName = file.name,
                                type = mediaType,
                                size = file.length(),
                                dateModified = file.lastModified(),
                                uri = Uri.fromFile(file)
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scanning $source at $relativePath - Permission denied!", e)
            Log.e(TAG, "Make sure MANAGE_EXTERNAL_STORAGE is granted for Android 11+")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning $source at $relativePath", e)
            e.printStackTrace()
        }

        return mediaList.sortedByDescending { it.dateModified }
    }

    /**
     * Determines media type from file extension
     */
    private fun getMediaType(fileName: String): MediaType? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> MediaType.IMAGE
            "mp4", "mkv", "avi", "3gp", "webm", "mov" -> MediaType.VIDEO
            "mp3", "m4a", "aac", "opus", "ogg", "wav" -> MediaType.AUDIO
            else -> null
        }
    }

    /**
     * Quick check: Are there any statuses available?
     */
    fun hasStatuses(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.w(TAG, "MANAGE_EXTERNAL_STORAGE not granted")
                return false
            }
        }

        val storageDir = Environment.getExternalStorageDirectory()
        val allPaths = whatsappPaths + whatsappBusinessPaths

        return allPaths.any { path ->
            val folder = File(storageDir, path)
            val exists = folder.exists() && folder.isDirectory
            val hasFiles = exists && (folder.listFiles()?.isNotEmpty() == true)
            
            if (hasFiles) {
                Log.d(TAG, "Found statuses in: ${folder.absolutePath}")
            }
            
            hasFiles
        }
    }

    /**
     * Get total count of all media
     */
    fun getTotalCount(): Int {
        return scanAllStatus().values.sumOf { it.size }
    }

    /**
     * Debug function: Print all available folders
     */
    fun debugListFolders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Log.e(TAG, "DEBUG: MANAGE_EXTERNAL_STORAGE not granted!")
            return
        }

        val storageDir = Environment.getExternalStorageDirectory()
        Log.d(TAG, "=== DEBUG: Listing Folders ===")
        Log.d(TAG, "Storage root: ${storageDir.absolutePath}")

        fun listRecursive(dir: File, depth: Int = 0) {
            if (depth > 4) return // Limit recursion depth
            
            try {
                dir.listFiles()?.forEach { file ->
                    val prefix = "  ".repeat(depth)
                    if (file.isDirectory) {
                        Log.d(TAG, "$prefix[DIR] ${file.name}")
                        if (file.name.contains("whatsapp", ignoreCase = true) || 
                            file.name == "Android" || 
                            file.name == "media") {
                            listRecursive(file, depth + 1)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing ${dir.absolutePath}", e)
            }
        }

        listRecursive(storageDir)
        Log.d(TAG, "=== END DEBUG ===")
    }
}
