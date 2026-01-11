package com.statussaver.core

import android.graphics.Bitmap
import android.net.Uri

/**
 * Represents a single WhatsApp status media file
 */
data class MediaItem(
    val path: String,           // URI as string for compatibility
    val fileName: String,        // File name
    val type: MediaType,         // Image, Video, or Audio
    val size: Long,              // File size in bytes
    val dateModified: Long,      // Timestamp
    var thumbnail: Bitmap? = null, // Thumbnail (loaded later)
    val uri: Uri? = null         // Actual URI for SAF access
)

enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO
}

/**
 * Helper to get file extension
 */
fun MediaItem.getExtension(): String {
    return fileName.substringAfterLast('.', "")
}

/**
 * Helper to format file size
 */
fun MediaItem.getFormattedSize(): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}
