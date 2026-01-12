package com.statussaver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
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

    // Track which flow user is in
    private var userHasRegularWhatsApp = false

    // SAF Folder Picker for WhatsApp
    private val whatsappFolderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            Log.d(TAG, "WhatsApp folder selected: $uri")
            saveWhatsAppUri(uri)
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            if (userHasRegularWhatsApp) {
                askAboutWhatsAppBusiness()
            } else {
                loadStatuses()
            }
        } ?: run {
            Log.e(TAG, "No folder selected for WhatsApp")
            Toast.makeText(this, "Folder access required to view statuses", Toast.LENGTH_LONG).show()
        }
    }

    // SAF Folder Picker for WhatsApp Business
    private val whatsappBusinessFolderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            Log.d(TAG, "WhatsApp Business folder selected: $uri")
            saveWhatsAppBusinessUri(uri)
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
        loadStatuses()
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

        // Set up click listeners - switch to fragments instead of new activities
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
            // Android 11+ → Need SAF folder access
            Log.d(TAG, "Android 11+ detected - using SAF")
            if (checkBasicPermissions()) {
                if (!hasFolderPermissions()) {
                    if (isFirstLaunch()) {
                        showPrivacyPolicyDialog()
                    } else {
                        requestFolderAccess()
                    }
                } else {
                    loadStatuses()
                }
            } else {
                requestBasicPermissions()
            }
        } else {
            // Android 10 and below → Use old File API
            Log.d(TAG, "Android 10 or below detected - using File API")
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

    private fun hasFolderPermissions(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val whatsappUri = prefs.getString(KEY_WHATSAPP_URI, null)
        return whatsappUri != null
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

    private fun requestFolderAccess() {
        AlertDialog.Builder(this)
            .setTitle("WhatsApp Status Access")
            .setMessage("Do you use regular WhatsApp?")
            .setPositiveButton("Yes") { _, _ ->
                userHasRegularWhatsApp = true
                showRegularWhatsAppInstructions()
            }
            .setNegativeButton("No") { _, _ ->
                userHasRegularWhatsApp = false
                showBusinessWhatsAppInstructions()
            }
            .setCancelable(false)
            .show()
    }

    private fun showRegularWhatsAppInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Grant Access to WhatsApp Statuses")
            .setMessage("To view WhatsApp statuses, grant folder access.\n\nNext steps:\n\n1️⃣ Tap 'Continue' below\n2️⃣ Tap the blue 'USE THIS FOLDER' button\n3️⃣ Tap 'ALLOW' to confirm\n\nThe correct folder is already selected for you.")
            .setPositiveButton("Continue") { _, _ ->
                requestWhatsAppAccess()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showEmptyState()
            }
            .show()
    }

    private fun showBusinessWhatsAppInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Grant Access to WhatsApp Business Statuses")
            .setMessage("To view WhatsApp Business statuses, grant folder access.\n\nNext steps:\n\n1️⃣ Tap 'Continue' below\n2️⃣ Tap the blue 'USE THIS FOLDER' button\n3️⃣ Tap 'ALLOW' to confirm\n\nThe correct folder is already selected for you.")
            .setPositiveButton("Continue") { _, _ ->
                requestWhatsAppBusinessAccess()
            }
            .setNegativeButton("Cancel") { _, _ ->
                showEmptyState()
            }
            .show()
    }

    private fun askAboutWhatsAppBusiness() {
        AlertDialog.Builder(this)
            .setTitle("WhatsApp Business")
            .setMessage("Do you also use WhatsApp Business?")
            .setPositiveButton("Yes (Grant Access)") { _, _ ->
                showBusinessWhatsAppInstructions()
            }
            .setNegativeButton("No") { _, _ ->
                loadStatuses()
            }
            .show()
    }

    private fun requestWhatsAppAccess() {
        // Point to Media folder (not .Statuses directly) to avoid Xiaomi blocks
        val folderPath = "Android/media/com.whatsapp/WhatsApp/Media"

        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$folderPath"
        )

        whatsappFolderPicker.launch(treeUri)
    }

    private fun requestWhatsAppBusinessAccess() {
        val folderPath = "Android/media/com.whatsapp.w4b/WhatsApp Business/Media"

        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:$folderPath"
        )

        whatsappBusinessFolderPicker.launch(treeUri)
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

        // Make TextView scrollable
        privacyTextView.movementMethod = ScrollingMovementMethod()
        privacyTextView.text = getString(R.string.privacy_policy_content)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.privacy_policy_title))
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.privacy_continue)) { dialog, _ ->
                dialog.dismiss()
                setFirstLaunchComplete()

                // After privacy policy, check which method to use
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestFolderAccess()
                } else {
                    loadStatuses()
                }
            }
            .show()
    }

    private fun showMediaFragment(mediaType: String) {
        Log.d(TAG, "Showing $mediaType fragment")

        // Hide ONLY the header and home content (NOT the ad container!)
        binding.header.visibility = View.GONE
        binding.homeContent.visibility = View.GONE
        // Ad container ALWAYS stays visible - never touch it!

        // Show fragment in fragmentContainer
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

        // Show header and home screen again
        binding.header.visibility = View.VISIBLE
        binding.homeContent.visibility = View.VISIBLE
        // Ad container ALWAYS stays visible!

        // Reload status counts
        if (checkBasicPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (hasFolderPermissions()) {
                    loadStatuses()
                }
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
            // Android 12 and below
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
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
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
        // If fragment is showing, go back to home
        if (supportFragmentManager.fragments.isNotEmpty()) {
            showHomeScreen()
        } else {
            // Otherwise, exit app
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "=== onDestroy ===")

        // Only destroy banner if activity is finishing (app closing)
        if (isFinishing) {
            Log.d(TAG, "App closing - destroying banner")
            bannerAdManager.destroy()
        }

        super.onDestroy()
    }
}
