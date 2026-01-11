package com.statussaver.core

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * THE BRAIN: Scans WhatsApp status folders using SAF
 */
class StatusScanner(private val context: Context) {

    private val TAG = "StatusScanner"
    private val PREFS_NAME = "AppPrefs"
    private val KEY_WHATSAPP_URI = "whatsapp_uri"
    private val KEY_WHATSAPP_BUSINESS_URI = "whatsapp_business_uri"

    /**
     * Main function: Scans all WhatsApp folders and returns organized media
     * Returns: Map with keys "IMAGE", "VIDEO", "AUDIO"
     */
    fun scanAllStatus(): Map<String, List<MediaItem>> {
        val allMedia = mutableListOf<MediaItem>()

        Log.d(TAG, "=== Starting Status Scan ===")

        // Get saved URIs from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val whatsappUriString = prefs.getString(KEY_WHATSAPP_URI, null)
        val whatsappBusinessUriString = prefs.getString(KEY_WHATSAPP_BUSINESS_URI, null)

        // Scan regular WhatsApp
        whatsappUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d(TAG, "Scanning WhatsApp: $uri")
            allMedia.addAll(scanFolder(uri, "WhatsApp"))
        } ?: Log.w(TAG, "No WhatsApp URI found")

        // Scan WhatsApp Business
        whatsappBusinessUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d(TAG, "Scanning WhatsApp Business: $uri")
            allMedia.addAll(scanFolder(uri, "WhatsApp Business"))
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
     * Scans a specific folder using DocumentFile
     */
    private fun scanFolder(treeUri: Uri, source: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            
            if (documentFile == null) {
                Log.e(TAG, "DocumentFile is null for $source")
                return emptyList()
            }

            if (!documentFile.exists()) {
                Log.e(TAG, "Folder doesn't exist for $source")
                return emptyList()
            }

            if (!documentFile.isDirectory) {
                Log.e(TAG, "Not a directory for $source")
                return emptyList()
            }

            Log.d(TAG, "Scanning folder: ${documentFile.name}")

            // Get all files in folder
            val files = documentFile.listFiles()
            Log.d(TAG, "Found ${files.size} files in $source")

            files.forEach { file ->
                if (file.isFile && !file.name.isNullOrEmpty() && !file.name!!.startsWith(".nomedia")) {
                    val fileName = file.name ?: return@forEach
                    val mediaType = getMediaType(fileName)
                    
                    if (mediaType != null) {
                        val uri = file.uri
                        val size = file.length()
                        val dateModified = file.lastModified()

                        Log.d(TAG, "Found media: $fileName (${mediaType.name}, ${size} bytes)")

                        mediaList.add(
                            MediaItem(
                                path = uri.toString(), // Store URI as string
                                fileName = fileName,
                                type = mediaType,
                                size = size,
                                dateModified = dateModified,
                                uri = uri // Store actual URI
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning $source", e)
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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val whatsappUriString = prefs.getString(KEY_WHATSAPP_URI, null)
        val whatsappBusinessUriString = prefs.getString(KEY_WHATSAPP_BUSINESS_URI, null)

        return listOfNotNull(whatsappUriString, whatsappBusinessUriString).any { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                documentFile?.exists() == true && 
                documentFile.isDirectory && 
                (documentFile.listFiles().isNotEmpty())
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Get total count of all media
     */
    fun getTotalCount(): Int {
        return scanAllStatus().values.sumOf { it.size }
    }
}
