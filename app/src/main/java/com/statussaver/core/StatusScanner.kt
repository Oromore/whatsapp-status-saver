package com.statussaver.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * ULTIMATE STATUS SCANNER
 * Combines 3 methods for 100% device compatibility:
 * 1. MANAGE_EXTERNAL_STORAGE + File API (Direct access)
 * 2. MediaStore API (Database queries)
 * 3. SAF Direct URI (Last resort fallback)
 */
class StatusScanner(private val context: Context) {

    private val TAG = "StatusScanner"
    private val PREFS_NAME = "AppPrefs"
    private val KEY_WHATSAPP_URI = "whatsapp_uri"
    private val KEY_WHATSAPP_BUSINESS_URI = "whatsapp_business_uri"

    // All possible WhatsApp status paths
    private val whatsappPaths = listOf(
        "/WhatsApp/Media/.Statuses",
        "/Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
        "/storage/emulated/0/WhatsApp/Media/.Statuses",
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    )

    private val whatsappBusinessPaths = listOf(
        "/WhatsApp Business/Media/.Statuses",
        "/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
        "/storage/emulated/0/WhatsApp Business/Media/.Statuses",
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
    )

    /**
     * MAIN SCAN FUNCTION
     * Uses all 3 methods in priority order
     */
    fun scanAllStatus(): Map<String, List<MediaItem>> {
        val allMedia = mutableListOf<MediaItem>()

        Log.d(TAG, "=== ULTIMATE STATUS SCAN START ===")
        Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "Has MANAGE_EXTERNAL_STORAGE: ${hasManageExternalStorage()}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - Use all 3 methods

            // METHOD 1: MANAGE_EXTERNAL_STORAGE + File API (Most powerful)
            if (hasManageExternalStorage()) {
                Log.d(TAG, "🔥 METHOD 1: Using MANAGE_EXTERNAL_STORAGE + File API")
                val fileApiResults = scanUsingFileAPIWithFullAccess()
                allMedia.addAll(fileApiResults)
                Log.d(TAG, "✅ File API found: ${fileApiResults.size} files")
            }

            // METHOD 2: MediaStore API (Database query - works even without MANAGE permission)
            if (allMedia.isEmpty()) {
                Log.d(TAG, "🔥 METHOD 2: Using MediaStore API")
                val mediaStoreResults = scanUsingMediaStore()
                allMedia.addAll(mediaStoreResults)
                Log.d(TAG, "✅ MediaStore found: ${mediaStoreResults.size} files")
            }

            // METHOD 3: SAF Direct URI (Last resort)
            if (allMedia.isEmpty()) {
                Log.d(TAG, "🔥 METHOD 3: Using SAF Direct URI (fallback)")
                val safResults = scanUsingSAF()
                allMedia.addAll(safResults)
                Log.d(TAG, "✅ SAF found: ${safResults.size} files")
            }

        } else {
            // Android 10 and below - Simple File API
            Log.d(TAG, "Using legacy File API (Android 10 and below)")
            allMedia.addAll(scanUsingFileAPILegacy())
        }

        Log.d(TAG, "Total media found: ${allMedia.size}")

        // Remove duplicates by file path/URI
        val uniqueMedia = allMedia.distinctBy { it.uri.toString() }
        Log.d(TAG, "Unique media after deduplication: ${uniqueMedia.size}")

        // Group by type
        return mapOf(
            "IMAGE" to uniqueMedia.filter { it.type == MediaType.IMAGE },
            "VIDEO" to uniqueMedia.filter { it.type == MediaType.VIDEO },
            "AUDIO" to uniqueMedia.filter { it.type == MediaType.AUDIO }
        )
    }

    /**
     * Check if app has MANAGE_EXTERNAL_STORAGE permission
     */
    private fun hasManageExternalStorage(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Not needed on Android 10 and below
        }
    }

    /**
     * METHOD 1: MANAGE_EXTERNAL_STORAGE + File API
     * Direct file system access - most reliable when permission is granted
     */
    private fun scanUsingFileAPIWithFullAccess(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            // Try multiple base paths
            val basePaths = listOf(
                Environment.getExternalStorageDirectory(),
                File("/storage/emulated/0"),
                File(Environment.getExternalStorageDirectory(), "Android/media")
            )

            for (basePath in basePaths) {
                // Scan WhatsApp
                whatsappPaths.forEach { relativePath ->
                    val cleanPath = relativePath.removePrefix("/")
                    val folder = File(basePath, cleanPath)
                    mediaList.addAll(scanFolder(folder, "WhatsApp"))
                }

                // Scan WhatsApp Business
                whatsappBusinessPaths.forEach { relativePath ->
                    val cleanPath = relativePath.removePrefix("/")
                    val folder = File(basePath, cleanPath)
                    mediaList.addAll(scanFolder(folder, "WhatsApp Business"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in File API with MANAGE_EXTERNAL_STORAGE", e)
        }

        return mediaList.sortedByDescending { it.dateModified }
    }

    /**
     * Helper: Scan a folder using File API
     */
    private fun scanFolder(folder: File, source: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            if (!folder.exists() || !folder.isDirectory) {
                return emptyList()
            }

            Log.d(TAG, "✓ Scanning folder: ${folder.absolutePath}")

            val files = folder.listFiles() ?: return emptyList()
            Log.d(TAG, "  → Found ${files.size} files in $source")

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
                                dateModified = file.lastModified(),
                                uri = Uri.fromFile(file)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning folder: ${folder.absolutePath}", e)
        }

        return mediaList
    }

    /**
     * METHOD 2: MediaStore API
     * Queries the system media database - bypasses folder listing restrictions
     */
    private fun scanUsingMediaStore(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            // Path patterns for MediaStore query
            val pathPatterns = listOf(
                "%/WhatsApp/Media/.Statuses/%",
                "%/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/%",
                "%/WhatsApp Business/Media/.Statuses/%",
                "%/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses/%",
                "%WhatsApp%Statuses%", // Catch-all pattern
                "%whatsapp%statuses%" // Case-insensitive catch-all
            )

            // Query Images
            mediaList.addAll(
                queryMediaStore(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaType.IMAGE,
                    pathPatterns
                )
            )

            // Query Videos
            mediaList.addAll(
                queryMediaStore(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaType.VIDEO,
                    pathPatterns
                )
            )

            // Query Audio
            mediaList.addAll(
                queryMediaStore(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MediaType.AUDIO,
                    pathPatterns
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "MediaStore scan failed", e)
        }

        return mediaList.sortedByDescending { it.dateModified }
    }

    /**
     * Helper: Query MediaStore with path patterns
     */
    private fun queryMediaStore(
        contentUri: Uri,
        mediaType: MediaType,
        pathPatterns: List<String>
    ): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        // Build selection: (DATA LIKE ? OR DATA LIKE ? OR ...)
        val selection = pathPatterns.joinToString(" OR ") {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }
        val selectionArgs = pathPatterns.toTypedArray()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val path = it.getString(dataColumn)
                    val fileName = it.getString(nameColumn)
                    val size = it.getLong(sizeColumn)
                    val dateModified = it.getLong(dateColumn) * 1000 // Convert to ms

                    // Skip .nomedia files
                    if (fileName.startsWith(".nomedia")) continue

                    // Verify it's actually a status file
                    if (!path.contains(".Statuses")) continue

                    val uri = Uri.withAppendedPath(contentUri, id.toString())

                    Log.d(TAG, "  → MediaStore found: $fileName")

                    mediaList.add(
                        MediaItem(
                            path = path,
                            fileName = fileName,
                            type = mediaType,
                            size = size,
                            dateModified = dateModified,
                            uri = uri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for ${mediaType.name}", e)
        } finally {
            cursor?.close()
        }

        return mediaList
    }

    /**
     * METHOD 3: SAF (Storage Access Framework)
     * Fallback for devices that block everything else
     */
    private fun scanUsingSAF(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val whatsappUriString = prefs.getString(KEY_WHATSAPP_URI, null)
        val whatsappBusinessUriString = prefs.getString(KEY_WHATSAPP_BUSINESS_URI, null)

        // Scan regular WhatsApp
        whatsappUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d(TAG, "✓ Scanning WhatsApp via SAF: $uri")
            mediaList.addAll(scanFolderSAF(uri, "WhatsApp"))
        }

        // Scan WhatsApp Business
        whatsappBusinessUriString?.let { uriString ->
            val uri = Uri.parse(uriString)
            Log.d(TAG, "✓ Scanning WhatsApp Business via SAF: $uri")
            mediaList.addAll(scanFolderSAF(uri, "WhatsApp Business"))
        }

        return mediaList
    }

    /**
     * Scan folder using SAF
     */
    private fun scanFolderSAF(treeUri: Uri, source: String): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        try {
            val rootFolder = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()

            // Find .Statuses folder
            val statusFolder = findStatusFolder(rootFolder, source)

            if (statusFolder == null) {
                Log.e(TAG, "✗ .Statuses folder not found for $source")
                return emptyList()
            }

            Log.d(TAG, "✓ Found .Statuses: ${statusFolder.uri}")

            val files = statusFolder.listFiles()
            Log.d(TAG, "  → ${files.size} files in .Statuses")

            files.forEach { file ->
                if (file.isFile && !file.name.isNullOrEmpty() && !file.name!!.startsWith(".nomedia")) {
                    val fileName = file.name ?: return@forEach
                    val mediaType = getMediaType(fileName)

                    if (mediaType != null) {
                        mediaList.add(
                            MediaItem(
                                path = file.uri.toString(),
                                fileName = fileName,
                                type = mediaType,
                                size = file.length(),
                                dateModified = file.lastModified(),
                                uri = file.uri
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning $source via SAF", e)
        }

        return mediaList.sortedByDescending { it.dateModified }
    }

    /**
     * Helper: Find .Statuses folder through multiple paths
     */
    private fun findStatusFolder(root: DocumentFile, source: String): DocumentFile? {
        val targetPkg = if (source == "WhatsApp") "com.whatsapp" else "com.whatsapp.w4b"

        // Try all possible path combinations
        val pathCombinations = listOf(
            listOf("Android", "media", targetPkg, source, "Media", ".Statuses"),
            listOf("Android", "media", targetPkg, "Media", ".Statuses"),
            listOf(source, "Media", ".Statuses")
        )

        for (pathParts in pathCombinations) {
            var current: DocumentFile? = root

            for (part in pathParts) {
                current = current?.listFiles()?.find {
                    it.name?.equals(part, ignoreCase = true) == true && it.isDirectory
                }
                if (current == null) break
            }

            if (current != null && current.exists()) {
                return current
            }
        }

        return null
    }

    /**
     * Legacy File API for Android 10 and below
     */
    private fun scanUsingFileAPILegacy(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()

        val storageDir = Environment.getExternalStorageDirectory()

        whatsappPaths.forEach { path ->
            val folder = File(storageDir, path.removePrefix("/"))
            mediaList.addAll(scanFolder(folder, "WhatsApp"))
        }

        whatsappBusinessPaths.forEach { path ->
            val folder = File(storageDir, path.removePrefix("/"))
            mediaList.addAll(scanFolder(folder, "WhatsApp Business"))
        }

        return mediaList
    }

    /**
     * Determine media type from file extension
     */
    private fun getMediaType(fileName: String): MediaType? {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif" -> MediaType.IMAGE
            "mp4", "mkv", "avi", "3gp", "webm", "mov" -> MediaType.VIDEO
            "mp3", "m4a", "aac", "opus", "ogg", "wav" -> MediaType.AUDIO
            else -> null
        }
    }

    /**
     * Quick check: Are there any statuses available?
     */
    fun hasStatuses(): Boolean {
        val results = scanAllStatus()
        return results.values.sumOf { it.size } > 0
    }

    /**
     * Get total count
     */
    fun getTotalCount(): Int {
        return scanAllStatus().values.sumOf { it.size }
    }
}
