package com.statussaver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
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
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: StatusScanner
    private val PERMISSION_REQUEST_CODE = 100

    private lateinit var bannerAdManager: BannerAdManager
    private lateinit var interstitialAdManager: InterstitialAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")

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

        // Check permissions and load statuses
        if (checkPermissions()) {
            loadStatuses()
        } else {
            requestPermissions()
        }

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

    private fun showMediaFragment(mediaType: String) {
        Log.d(TAG, "Showing $mediaType fragment")

        // Hide entire home screen
        binding.homeScreen.visibility = View.GONE

        // Show fragment in mainContainer
        val fragment = MediaListFragment.newInstance(mediaType)
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, fragment)
            .commit()
    }

    fun showHomeScreen() {
        Log.d(TAG, "Showing home screen")

        // Remove fragment
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }

        // Show home screen
        binding.homeScreen.visibility = View.VISIBLE

        // Reload status counts
        if (checkPermissions()) {
            loadStatuses()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 and below
            val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            readPermission && writePermission
        }
    }

    private fun requestPermissions() {
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
        binding.contentLayout.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
    }

    private fun showEmptyState() {
        binding.contentLayout.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.statusCount.text = ""
    }

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
