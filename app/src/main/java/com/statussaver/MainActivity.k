package com.statussaver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.statussaver.core.MediaType
import com.statussaver.core.StatusScanner
import com.statussaver.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var scanner: StatusScanner
    private val PERMISSION_REQUEST_CODE = 100
    private var bottomAdView: AdView? = null
    private var interstitialAd: InterstitialAd? = null

    // SharedPreferences for app open counter
    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanner = StatusScanner(this)

        // Initialize AdMob
        MobileAds.initialize(this)
        setupBottomBannerAd()
        loadInterstitialAd()

        // Handle interstitial ad on app open (every 2nd open)
        handleAppOpenInterstitial()

        // Check permissions and load statuses
        if (checkPermissions()) {
            loadStatuses()
        } else {
            requestPermissions()
        }

        // Set up click listeners
        binding.btnImages.setOnClickListener {
            openMediaList("IMAGE")
        }

        binding.btnVideos.setOnClickListener {
            openMediaList("VIDEO")
        }

        binding.btnAudio.setOnClickListener {
            openMediaList("AUDIO")
        }
    }

    private fun setupBottomBannerAd() {
        bottomAdView = AdView(this).apply {
            adUnitId = "ca-app-pub-3940256099942544/6300978111" // TEST BANNER AD
            setAdSize(AdSize.BANNER)
        }

        // Add listener to debug ad loading
        bottomAdView?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Toast.makeText(this@MainActivity, "Banner ad loaded!", Toast.LENGTH_SHORT).show()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Toast.makeText(this@MainActivity, "Banner failed: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.adContainer.removeAllViews()
        binding.adContainer.addView(bottomAdView)

        val adRequest = AdRequest.Builder().build()
        bottomAdView?.loadAd(adRequest)
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712", // TEST INTERSTITIAL AD
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Toast.makeText(this@MainActivity, "Interstitial loaded!", Toast.LENGTH_SHORT).show()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Toast.makeText(this@MainActivity, "Interstitial failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun handleAppOpenInterstitial() {
        // Get app open count
        var openCount = prefs.getInt("app_open_count", 0)
        openCount++
        prefs.edit().putInt("app_open_count", openCount).apply()

        // Show interstitial on every 2nd open (after app loads)
        if (openCount % 2 == 0) {
            // Delay slightly so app loads first, then show ad
            binding.root.postDelayed({
                interstitialAd?.show(this) ?: loadInterstitialAd()
            }, 1000) // 1 second delay
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
        // Show explanation dialog first
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
        // Show loading
        binding.statusCount.text = getString(R.string.loading)

        // Load statuses in background
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
        bottomAdView?.resume()
        // Refresh counts when returning to this screen
        if (checkPermissions()) {
            loadStatuses()
        }
    }

    override fun onPause() {
        bottomAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        bottomAdView?.destroy()
        super.onDestroy()
    }
}
