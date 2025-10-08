package com.statussaver.core

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * THE BRAIN: Scans WhatsApp status folders and organizes media
 */
class StatusScanner(private val context: Context) {

    // WhatsApp status folder paths
    private val whatsappPaths = listOf(
        "/WhatsApp/Media/.Statuses",
        "/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    )

    // WhatsApp Business status folder paths
    private val whatsappBusinessPaths = listOf(
        "/WhatsApp Business/Media/.Statuses",
        "/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    )

    /**
     * Main function: Scans all WhatsApp folders and returns organized media
     * Returns: Map with keys "IMAGE", "VIDEO", "AUDIO"
     */
    fun scanAllStatus(): Map<String, List<MediaItem>> {
        val allMedia = mutableListOf<MediaItem>()

        // Scan regular WhatsApp
        whatsappPaths.forEach { path ->
            allMedia.addAll(scanFolder(path))
        }

        // Scan WhatsApp Business
        whatsappBusinessPaths.forEach { path ->
            allMedia.addAll(scanFolder(path))
        }

        // Remove duplicates (same file in multiple locations)
        val uniqueMedia = allMedia.distinctBy { it.fileName }

        // Group by type
        return mapOf(
            "IMAGE" to uniqueMedia.filter { it.type == MediaType.IMAGE },
            "VIDEO" to uniqueMedia.filter { it.type == MediaType.VIDEO },
            "AUDIO" to uniqueMedia.filter { it.type == MediaType.AUDIO }
        )
    }

    /**
     * Scans a specific folder for status files
     */
    private fun scanFolder(relativePath: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            val storageDir = Environment.getExternalStorageDirectory()
            val statusFolder = File(storageDir, relativePath)

            if (!statusFolder.exists() || !statusFolder.isDirectory) {
                return emptyList()
            }

            // Get all files in folder
            val files = statusFolder.listFiles() ?: return emptyList()

            files.forEach { file ->
                if (file.isFile && !file.name.startsWith(".nomedia")) {
                    val mediaType = getMediaType(file.name)
                    if (mediaType != null) {
                        mediaList.add(
                            MediaItem(
                                path = file.absolutePath,
                                fileName = file.name,
                                type = mediaType,
                                size = file.length(),
                                dateModified = file.lastModified()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort by date (newest first)
        return mediaList.sortedByDescending { it.dateModified }
    }

    /**
     * Determines media type from file extension
     */
    private fun getMediaType(fileName: String): MediaType? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp" -> MediaType.IMAGE
            "mp4", "mkv", "avi", "3gp", "webm" -> MediaType.VIDEO
            "mp3", "m4a", "aac", "opus", "ogg" -> MediaType.AUDIO
            else -> null
        }
    }

    /**
     * Quick check: Are there any statuses available?
     */
    fun hasStatuses(): Boolean {
        val allPaths = whatsappPaths + whatsappBusinessPaths
        return allPaths.any { path ->
            val storageDir = Environment.getExternalStorageDirectory()
            val folder = File(storageDir, path)
            folder.exists() && folder.isDirectory && (folder.listFiles()?.isNotEmpty() == true)
        }
    }

    /**
     * Get total count of all media
     */
    fun getTotalCount(): Int {
        return scanAllStatus().values.sumOf { it.size }
    }
}
