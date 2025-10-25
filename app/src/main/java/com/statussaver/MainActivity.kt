package com.statussaver

import android.Manifest
import android.content.Intent
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

    private lateinit var interstitialAdManager: InterstitialAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")

        scanner = StatusScanner(this)

        // Initialize interstitial ad manager
        interstitialAdManager = InterstitialAdManager(this)

        // Wait for Unity Ads to be ready before loading banner
        Log.d(TAG, "Registering Unity Ads ready callback")
        UnityAdsManager.onReady {
            Log.d(TAG, "Unity Ads ready - loading banner")
            runOnUiThread {
                BannerAdManager.loadBanner(this@MainActivity, binding.adContainer)
            }
        }

        // Check permissions and load statuses
        if (checkPermissions()) {
            loadStatuses()
        } else {
            requestPermissions()
        }

        // Set up click listeners
        binding.btnImages.setOnClickListener {
            interstitialAdManager.trackAppInteraction()
            openMediaList("IMAGE")
        }

        binding.btnVideos.setOnClickListener {
            interstitialAdManager.trackAppInteraction()
            openMediaList("VIDEO")
        }

        binding.btnAudio.setOnClickListener {
            interstitialAdManager.trackAppInteraction()
            openMediaList("AUDIO")
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 and below - CHECK BOTH READ AND WRITE
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

    private fun openMediaList(type: String) {
        val intent = Intent(this, MediaListActivity::class.java)
        intent.putExtra("MEDIA_TYPE", type)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume ===")

        // Load banner if Unity is ready (singleton handles persistence)
        if (UnityAdsManager.isReady()) {
            Log.d(TAG, "Unity Ads ready - loading banner")
            BannerAdManager.loadBanner(this, binding.adContainer)
        }

        // Refresh counts when returning to this screen
        if (checkPermissions()) {
            loadStatuses()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== onPause ===")
        // DON'T hide banner - let it stay visible 24/7
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy ===")
        
        // CRITICAL: Only destroy banner when MainActivity is truly finishing
        // (user pressed back to exit app, NOT configuration change like rotation)
        if (isFinishing && !isChangingConfigurations) {
            Log.d(TAG, "MainActivity is truly FINISHING (App closing) - DESTROYING Global Banner")
            BannerAdManager.destroyBanner()
        } else {
            Log.d(TAG, "MainActivity being recreated or navigating away - keeping banner alive")
        }
    }
}
