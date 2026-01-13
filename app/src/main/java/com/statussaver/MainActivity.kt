package com.statussaver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: StatusScanner

    private lateinit var bannerAdManager: BannerAdManager
    private lateinit var interstitialAdManager: InterstitialAdManager

    // Launcher for MANAGE_EXTERNAL_STORAGE permission (Android 11+)
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE granted!")
                loadStatuses()
            } else {
                Log.e(TAG, "MANAGE_EXTERNAL_STORAGE denied")
                Toast.makeText(this, "Storage permission is required to view statuses", Toast.LENGTH_LONG).show()
                showEmptyState()
            }
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

        // Wait for Unity Ads ready, then load PERMANENT banner
        UnityAdsManager.onReady {
            Log.d(TAG, "Unity Ads ready - loading PERMANENT banner")
            runOnUiThread {
                bannerAdManager.loadBanner(binding.adContainer)
            }
        }

        // Check permissions based on Android version
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
            // Android 11+ → Need MANAGE_EXTERNAL_STORAGE
            Log.d(TAG, "Android 11+ detected - checking MANAGE_EXTERNAL_STORAGE")
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE already granted")
                if (isFirstLaunch()) {
                    showPrivacyPolicyDialog()
                } else {
                    loadStatuses()
                }
            } else {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE not granted")
                if (isFirstLaunch()) {
                    showPrivacyPolicyDialog()
                } else {
                    requestManageStoragePermission()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 → Use READ/WRITE_EXTERNAL_STORAGE
            Log.d(TAG, "Android 6-10 detected - using standard permissions")
            if (checkBasicPermissions()) {
                if (isFirstLaunch()) {
                    showPrivacyPolicyDialog()
                } else {
                    loadStatuses()
                }
            } else {
                if (isFirstLaunch()) {
                    showPrivacyPolicyDialog()
                } else {
                    requestBasicPermissions()
                }
            }
        } else {
            // Android 5 and below - no runtime permissions needed
            Log.d(TAG, "Android 5 or below - no runtime permissions needed")
            if (isFirstLaunch()) {
                showPrivacyPolicyDialog()
            } else {
                loadStatuses()
            }
        }
    }

    private fun checkBasicPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 6-12
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestManageStoragePermission() {
        AlertDialog.Builder(this)
            .setTitle("Storage Permission Required")
            .setMessage("To view WhatsApp statuses, this app needs permission to access all files.\n\n" +
                    "Steps:\n" +
                    "1. Tap 'Grant Permission' below\n" +
                    "2. Find '${getString(R.string.app_name)}' in the list\n" +
                    "3. Enable 'Allow access to manage all files'\n\n" +
                    "This permission is needed because WhatsApp stores statuses in a protected folder.")
            .setPositiveButton("Grant Permission") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    // Fallback to general settings if specific intent fails
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Permission required to view statuses", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
            .setCancelable(false)
            .show()
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
                loadStatuses()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                showEmptyState()
            }
        }
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

                // After privacy policy, check permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        requestManageStoragePermission()
                    } else {
                        loadStatuses()
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!checkBasicPermissions()) {
                        requestBasicPermissions()
                    } else {
                        loadStatuses()
                    }
                } else {
                    loadStatuses()
                }
            }
            .show()
    }

    private fun showMediaFragment(mediaType: String) {
        Log.d(TAG, "Showing $mediaType fragment")

        // Hide header and home content
        binding.header.visibility = View.GONE
        binding.homeContent.visibility = View.GONE

        // Show fragment
        val fragment = MediaListFragment.newInstance(mediaType)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun showHomeScreen() {
        Log.d(TAG, "Showing home screen")

        // Remove fragment
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        // Show header and home content
        binding.header.visibility = View.VISIBLE
        binding.homeContent.visibility = View.VISIBLE

        // Reload status counts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                loadStatuses()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkBasicPermissions()) {
                loadStatuses()
            }
        } else {
            loadStatuses()
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
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statuses", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                }
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
