package com.statussaver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.statussaver.core.StatusScanner
import com.statussaver.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "AppPrefs"
        private const val KEY_FIRST_LAUNCH = "isFirstLaunch"
        private const val KEY_WHATSAPP_URI = "whatsapp_uri"
        private const val KEY_WHATSAPP_BUSINESS_URI = "whatsapp_business_uri"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: StatusScanner
    private val PERMISSION_REQUEST_CODE = 100

    private lateinit var bannerAdManager: BannerAdManager
    private lateinit var interstitialAdManager: InterstitialAdManager

    // Launcher for MANAGE_EXTERNAL_STORAGE permission (Android 11+)
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "✅ MANAGE_EXTERNAL_STORAGE granted")
                loadStatuses()
            } else {
                Log.e(TAG, "❌ MANAGE_EXTERNAL_STORAGE denied")
                Toast.makeText(this, "Storage permission is required to view statuses", Toast.LENGTH_LONG).show()
                // Fallback to MediaStore or SAF
                handlePermissionDenied()
            }
        }
    }

    // SAF Folder Picker (fallback if MANAGE_EXTERNAL_STORAGE is denied)
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            Log.d(TAG, "Storage root selected: $uri")
            saveWhatsAppUri(uri)
            saveWhatsAppBusinessUri(uri)

            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                Log.d(TAG, "Persistent URI permission granted")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistent permission", e)
            }

            loadStatuses()
        } ?: run {
            Log.e(TAG, "No folder selected")
            showEmptyState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")
        Log.d(TAG, "Android Version: ${Build.VERSION.SDK_INT}")

        scanner = StatusScanner(this)

        // Initialize ad managers
        bannerAdManager = BannerAdManager(this)
        interstitialAdManager = InterstitialAdManager(this)

        UnityAdsManager.onReady {
            Log.d(TAG, "Unity Ads ready - loading banner")
            runOnUiThread {
                bannerAdManager.loadBanner(binding.adContainer)
            }
        }

        // Handle permissions
        handleInitialPermissions()

        // Set up click listeners
        binding.btnImages.setOnClickListener {
            interstitialAdManager.trackAppInteraction()
            showMediaFragment("IMAGE")
        }

        binding.btnVideos.setOnClickListener {
            interstitialAdManager.trackAppInteraction()
            showMediaFragment("VIDEO")
        }

        binding.btnAudio.setOnClickListener {
            interstitialAdManager.trackAppInteraction()
            showMediaFragment("AUDIO")
        }
    }

    private fun handleInitialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ → Request MANAGE_EXTERNAL_STORAGE first
            Log.d(TAG, "Android 11+ detected")
            
            if (checkBasicPermissions()) {
                if (Environment.isExternalStorageManager()) {
                    // Already has MANAGE_EXTERNAL_STORAGE
                    Log.d(TAG, "✅ Already has MANAGE_EXTERNAL_STORAGE")
                    loadStatuses()
                } else {
                    // Need to request MANAGE_EXTERNAL_STORAGE
                    if (isFirstLaunch()) {
                        showPrivacyPolicyDialog()
                    } else {
                        requestManageExternalStorage()
                    }
                }
            } else {
                // Request basic permissions first
                requestBasicPermissions()
            }
        } else {
            // Android 10 and below → Use old permissions
            Log.d(TAG, "Android 10 or below detected")
            if (checkBasicPermissions()) {
                if (isFirstLaunch()) {
                    showPrivacyPolicyDialog()
                } else {
                    loadStatuses()
                }
            } else {
                requestBasicPermissions()
            }
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission (Android 11+)
     */
    private fun requestManageExternalStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs full storage access to view WhatsApp statuses.\n\n" +
                        "In the next screen:\n" +
                        "1. Find this app in the list\n" +
                        "2. Toggle 'Allow access to manage all files' to ON\n" +
                        "3. Come back to the app")
                .setPositiveButton("Grant Permission") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        // Some devices don't support the specific app settings
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    handlePermissionDenied()
                }
                .setCancelable(false)
                .show()
        }
    }

    /**
     * Handle case when MANAGE_EXTERNAL_STORAGE is denied
     * Fall back to MediaStore + SAF
     */
    private fun handlePermissionDenied() {
        AlertDialog.Builder(this)
            .setTitle("Alternative Access Method")
            .setMessage("Without full storage permission, the app will:\n\n" +
                    "1. Try to find statuses using MediaStore (works on most devices)\n" +
                    "2. If that fails, ask you to manually grant folder access\n\n" +
                    "Some features may be limited.")
            .setPositiveButton("Continue") { _, _ ->
                // Try MediaStore first (it might work without MANAGE_EXTERNAL_STORAGE)
                loadStatuses()
            }
            .setNegativeButton("Try SAF Access") { _, _ ->
                requestFolderAccess()
            }
            .show()
    }

    /**
     * Request SAF folder access (fallback method)
     */
    private fun requestFolderAccess() {
        AlertDialog.Builder(this)
            .setTitle("Grant Folder Access")
            .setMessage("Please grant access to the storage root:\n\n" +
                    "1. DO NOT navigate anywhere\n" +
                    "2. Simply tap 'USE THIS FOLDER'\n" +
                    "3. Then tap 'ALLOW'")
            .setPositiveButton("Continue") { _, _ ->
                requestStorageAccess()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showEmptyState()
            }
            .show()
    }

    private fun requestStorageAccess() {
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:"
        )

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri)
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }

        folderPicker.launch(treeUri)
    }

    private fun saveWhatsAppUri(uri: Uri) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WHATSAPP_URI, uri.toString()).apply()
        Log.d(TAG, "Saved WhatsApp URI: $uri")
    }

    private fun saveWhatsAppBusinessUri(uri: Uri) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WHATSAPP_BUSINESS_URI, uri.toString()).apply()
        Log.d(TAG, "Saved WhatsApp Business URI: $uri")
    }

    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    private fun setFirstLaunchComplete() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    private fun showPrivacyPolicyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_privacy_policy, null)
        val privacyTextView = dialogView.findViewById<TextView>(R.id.privacyPolicyText)

        privacyTextView.movementMethod = ScrollingMovementMethod()
        privacyTextView.text = getString(R.string.privacy_policy_content)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.privacy_policy_title))
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.privacy_continue)) { dialog, _ ->
                dialog.dismiss()
                setFirstLaunchComplete()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestManageExternalStorage()
                } else {
                    loadStatuses()
                }
            }
            .show()
    }

    private fun checkBasicPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBasicPermissions() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.permission_message))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                } else {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                showEmptyState()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                handleInitialPermissions()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
        }
    }

    private fun loadStatuses() {
        binding.statusCount.text = getString(R.string.loading)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaMap = scanner.scanAllStatus()
                val imageCount = mediaMap["IMAGE"]?.size ?: 0
                val videoCount = mediaMap["VIDEO"]?.size ?: 0
                val audioCount = mediaMap["AUDIO"]?.size ?: 0
                val totalCount = imageCount + videoCount + audioCount

                withContext(Dispatchers.Main) {
                    if (totalCount > 0) {
                        showContent()
                        binding.statusCount.text = "$totalCount statuses available"
                        binding.imageCount.text = "$imageCount items"
                        binding.videoCount.text = "$videoCount items"
                        binding.audioCount.text = "$audioCount items"
                    } else {
                        // No statuses found - offer alternative access
                        showNoStatusesDialog()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error loading statuses", e)
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    showNoStatusesDialog()
                }
            }
        }
    }

    private fun showNoStatusesDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Statuses Found")
            .setMessage("The app couldn't find any WhatsApp statuses. This could mean:\n\n" +
                    "1. No one has posted statuses recently\n" +
                    "2. Access method needs adjustment\n\n" +
                    "Would you like to try the manual folder access method?")
            .setPositiveButton("Try Manual Access") { _, _ ->
                requestFolderAccess()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showEmptyState()
            }
            .show()
    }

    private fun showMediaFragment(mediaType: String) {
        Log.d(TAG, "Showing $mediaType fragment")

        binding.header.visibility = View.GONE
        binding.homeContent.visibility = View.GONE

        val fragment = MediaListFragment.newInstance(mediaType)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun showHomeScreen() {
        Log.d(TAG, "Showing home screen")

        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        binding.header.visibility = View.VISIBLE
        binding.homeContent.visibility = View.VISIBLE

        if (checkBasicPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    loadStatuses()
                }
            } else {
                loadStatuses()
            }
        }
    }

    private fun showContent() {
        binding.homeContent.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.homeContent.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.statusCount.text = ""
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.fragments.isNotEmpty()) {
            showHomeScreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "=== onDestroy ===")
        if (isFinishing) {
            Log.d(TAG, "App closing - destroying banner")
            bannerAdManager.destroy()
        }
        super.onDestroy()
    }
}
