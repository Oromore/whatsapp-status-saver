package com.statussaver.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * THE BRAIN: Scans WhatsApp status folders
 * Supports both old (File API) and new (SAF) methods
 */
class StatusScanner(private val context: Context) {

    private val TAG = "StatusScanner"
    private val PREFS_NAME = "AppPrefs"
    private val KEY_WHATSAPP_URI = "whatsapp_uri"
    private val KEY_WHATSAPP_BUSINESS_URI = "whatsapp_business_uri"

    // Old paths for Android 10 and below
    private val legacyWhatsappPaths = listOf(
        "/WhatsApp/Media/.Statuses",
        "/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    )

    private val legacyWhatsappBusinessPaths = listOf(
        "/WhatsApp Business/Media/.Statuses",
        "/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    )

    /**
     * Main function: Scans all WhatsApp folders and returns organized media
     * Returns: Map with keys "IMAGE", "VIDEO", "AUDIO"
     */
    fun scanAllStatus(): Map<String, List<MediaItem>> {
        val allMedia = mutableListOf<MediaItem>()

        Log.d(TAG, "=== Starting Status Scan ===")
        Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ → Use SAF
            Log.d(TAG, "Using SAF method (Android 11+)")
            allMedia.addAll(scanUsingSAF())
        } else {
            // Android 10 and below → Use File API
            Log.d(TAG, "Using File API method (Android 10 and below)")
            allMedia.addAll(scanUsingFileAPI())
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
     * Scan using SAF (Android 11+)
     */
    private fun scanUsingSAF(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val whatsappUriString = prefs.getString(KEY_WHATSAPP_URI, null)
        val whatsappBusinessUriString = prefs.getString(KEY_WHATSAPP_BUSINESS_URI, null)

        // Scan regular WhatsApp
        whatsappUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d(TAG, "Scanning WhatsApp via SAF: $uri")
            mediaList.addAll(scanFolderSAF(uri, "WhatsApp"))
        } ?: Log.w(TAG, "No WhatsApp URI found")

        // Scan WhatsApp Business
        whatsappBusinessUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d(TAG, "Scanning WhatsApp Business via SAF: $uri")
            mediaList.addAll(scanFolderSAF(uri, "WhatsApp Business"))
        }

        return mediaList
    }

    /**
     * Scan using File API (Android 10 and below)
     */
    private fun scanUsingFileAPI(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        // Scan regular WhatsApp
        legacyWhatsappPaths.forEach { path ->
            mediaList.addAll(scanFolderFile(path, "WhatsApp"))
        }

        // Scan WhatsApp Business
        legacyWhatsappBusinessPaths.forEach { path ->
            mediaList.addAll(scanFolderFile(path, "WhatsApp Business"))
        }

        return mediaList
    }

    /**
     * Scan a folder using SAF (for Android 11+)
     * Uses manual iteration to bypass findFile() blindness
     */
    private fun scanFolderSAF(treeUri: Uri, source: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            val rootFolder = DocumentFile.fromTreeUri(context, treeUri)

            if (rootFolder == null) {
                Log.e(TAG, "Root folder is null for $source")
                return emptyList()
            }

            val targetPkg = if (source == "WhatsApp") "com.whatsapp" else "com.whatsapp.w4b"
            val targetMainFolder = if (source == "WhatsApp") "WhatsApp" else "WhatsApp Business"

            Log.d(TAG, "Starting manual tunnel for $source")

            // HELPER: Search for a folder manually by name (more reliable than findFile)
            fun findFolderManually(parent: DocumentFile?, name: String): DocumentFile? {
                if (parent == null) return null
                return parent.listFiles().find { 
                    it.name?.equals(name, ignoreCase = true) == true && it.isDirectory 
                }
            }

            // Deep Tunneling - Manual iteration through each level
            val androidDir = findFolderManually(rootFolder, "Android")
            Log.d(TAG, "Android dir found: ${androidDir != null}")

            val mediaDir = findFolderManually(androidDir, "media")
            Log.d(TAG, "media dir found: ${mediaDir != null}")

            val pkgDir = findFolderManually(mediaDir, targetPkg)
            Log.d(TAG, "$targetPkg dir found: ${pkgDir != null}")

            // CRITICAL: Some devices have different folder structures
            // Path 1: Android/media/com.whatsapp/WhatsApp/Media/.Statuses (common)
            // Path 2: Android/media/com.whatsapp/Media/.Statuses (some devices skip WhatsApp folder)
            val waDir = findFolderManually(pkgDir, targetMainFolder)
            Log.d(TAG, "$targetMainFolder dir found: ${waDir != null}")

            val waMediaDir = if (waDir != null) {
                // Standard path: WhatsApp folder exists
                findFolderManually(waDir, "Media")
            } else {
                // Alternative path: Direct jump to Media (WhatsApp folder missing)
                Log.d(TAG, "WhatsApp folder missing, trying direct Media access")
                findFolderManually(pkgDir, "Media")
            }
            Log.d(TAG, "Media dir found: ${waMediaDir != null}")

            // Final destination: .Statuses is often hidden
            var statusFolder = findFolderManually(waMediaDir, ".Statuses")
            Log.d(TAG, ".Statuses dir found in new path: ${statusFolder != null}")

            // FALLBACK: Try legacy path (root/WhatsApp/Media/.Statuses)
            if (statusFolder == null) {
                Log.d(TAG, "Trying legacy path for $source")
                val legacyWaDir = findFolderManually(rootFolder, targetMainFolder)
                val legacyMediaDir = findFolderManually(legacyWaDir, "Media")
                statusFolder = findFolderManually(legacyMediaDir, ".Statuses")
                Log.d(TAG, ".Statuses found in legacy path: ${statusFolder != null}")
            }

            if (statusFolder == null || !statusFolder.exists()) {
                Log.e(TAG, "Tunneling failed: .Statuses not found for $source")
                return emptyList()
            }

            Log.d(TAG, "Successfully reached .Statuses for $source: ${statusFolder.uri}")

            // Get all files in folder
            val files = statusFolder.listFiles()
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
                                path = uri.toString(),
                                fileName = fileName,
                                type = mediaType,
                                size = size,
                                dateModified = dateModified,
                                uri = uri
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning $source via SAF tunnel", e)
            e.printStackTrace()
        }

        return mediaList.sortedByDescending { it.dateModified }
    }

    /**
     * Scan a folder using File API (for Android 10 and below)
     */
    private fun scanFolderFile(relativePath: String, source: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            val storageDir = Environment.getExternalStorageDirectory()
            val statusFolder = File(storageDir, relativePath)

            if (!statusFolder.exists() || !statusFolder.isDirectory) {
                Log.d(TAG, "Folder doesn't exist: ${statusFolder.absolutePath}")
                return emptyList()
            }

            Log.d(TAG, "Scanning File folder: ${statusFolder.absolutePath}")

            val files = statusFolder.listFiles() ?: return emptyList()
            Log.d(TAG, "Found ${files.size} files in $source")

            files.forEach { file ->
                if (file.isFile && !file.name.startsWith(".nomedia")) {
                    val mediaType = getMediaType(file.name)
                    if (mediaType != null) {
                        Log.d(TAG, "Found media: ${file.name} (${mediaType.name})")

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
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning $source via File API", e)
            e.printStackTrace()
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check SAF
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val whatsappUriString = prefs.getString(KEY_WHATSAPP_URI, null)
            val whatsappBusinessUriString = prefs.getString(KEY_WHATSAPP_BUSINESS_URI, null)

            return listOfNotNull(whatsappUriString, whatsappBusinessUriString).any { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    val statusFolder = documentFile?.findFile(".Statuses") ?: documentFile
                    statusFolder?.exists() == true &&
                    statusFolder.isDirectory &&
                    (statusFolder.listFiles().isNotEmpty())
                } catch (e: Exception) {
                    false
                }
            }
        } else {
            // Check File API
            val allPaths = legacyWhatsappPaths + legacyWhatsappBusinessPaths
            return allPaths.any { path ->
                val storageDir = Environment.getExternalStorageDirectory()
                val folder = File(storageDir, path)
                folder.exists() && folder.isDirectory && (folder.listFiles()?.isNotEmpty() == true)
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
