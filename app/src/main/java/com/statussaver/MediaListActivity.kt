package com.statussaver

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.statussaver.core.FileSaver
import com.statussaver.core.MediaItem
import com.statussaver.core.StatusScanner
import com.statussaver.databinding.ActivityMediaListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaListBinding
    private lateinit var scanner: StatusScanner
    private lateinit var fileSaver: FileSaver
    private lateinit var adapter: MediaAdapter
    private var mediaType: String = "IMAGE"
    private var interstitialAd: InterstitialAd? = null
    private var bottomAdView: AdView? = null

    // Persistent save counter
    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scanner = StatusScanner(this)
        fileSaver = FileSaver(this)

        // Get media type from intent
        mediaType = intent.getStringExtra("MEDIA_TYPE") ?: "IMAGE"

        // Initialize AdMob
        MobileAds.initialize(this)
        setupBottomBannerAd()

        setupToolbar()
        setupRecyclerView()
        loadInterstitialAd()
        loadMedia()
    }

    private fun setupBottomBannerAd() {
        bottomAdView = AdView(this).apply {
            adUnitId = "ca-app-pub-3940256099942544/6300978111" // TEST BANNER AD
            setAdSize(AdSize.BANNER)
        }

        bottomAdView?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                // Refresh ad every 30 seconds
                binding.root.postDelayed({
                    bottomAdView?.loadAd(AdRequest.Builder().build())
                }, 30000)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                // Retry after 30 seconds on failure
                binding.root.postDelayed({
                    bottomAdView?.loadAd(AdRequest.Builder().build())
                }, 30000)
            }
        }

        binding.adContainer.removeAllViews()
        binding.adContainer.addView(bottomAdView)

        val adRequest = AdRequest.Builder().build()
        bottomAdView?.loadAd(adRequest)
    }

    private fun setupToolbar() {
        val title = when (mediaType) {
            "IMAGE" -> "Images"
            "VIDEO" -> "Videos"
            "AUDIO" -> "Audio"
            else -> "Media"
        }
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = MediaAdapter(
            onSaveClick = { item ->
                saveMedia(item)
            },
            onItemClick = { item ->
                openMediaViewer(item)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MediaListActivity)
            adapter = this@MediaListActivity.adapter
        }
    }

    private fun loadMedia() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaMap = scanner.scanAllStatus()
                val mediaList = mediaMap[mediaType] ?: emptyList()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE

                    if (mediaList.isNotEmpty()) {
                        binding.recyclerView.visibility = View.VISIBLE

                        // Insert native ads every 4 items
                        val itemsWithAds = insertNativeAds(mediaList)
                        adapter.submitList(itemsWithAds)

                        // Load native ads
                        loadNativeAds()
                    } else {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.emptyText.text = "No ${mediaType.lowercase()} found"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    Toast.makeText(this@MediaListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun insertNativeAds(mediaList: List<MediaItem>): List<Any> {
        val itemsWithAds = mutableListOf<Any>()
        var adPosition = 0

        mediaList.forEachIndexed { index, item ->
            itemsWithAds.add(item)

            // Insert ad after every 4 items
            if ((index + 1) % 4 == 0 && index < mediaList.size - 1) {
                itemsWithAds.add(NativeAdPlaceholder(adPosition))
                adPosition++
            }
        }

        return itemsWithAds
    }

    private fun loadNativeAds() {
        val adLoader = AdLoader.Builder(this, "ca-app-pub-3940256099942544/2247696110") // TEST NATIVE AD
            .forNativeAd { nativeAd ->
                adapter.addNativeAd(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Ad failed to load, continue without it
                }
            })
            .build()

        // Load multiple native ads (one for each placeholder)
        val numberOfAds = adapter.getNativeAdCount()
        for (i in 0 until numberOfAds) {
            adLoader.loadAd(AdRequest.Builder().build())
        }
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
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun saveMedia(item: MediaItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = fileSaver.saveToDownloads(item)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MediaListActivity, "Saved to Downloads/WhatsAppStatus!", Toast.LENGTH_LONG).show()

                    // Increment persistent save counter
                    incrementSaveCounter()
                } else {
                    Toast.makeText(this@MediaListActivity, "Failed to save - check logcat", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun incrementSaveCounter() {
        var saveCount = prefs.getInt("save_count", 0)
        saveCount++
        prefs.edit().putInt("save_count", saveCount).apply()

        Toast.makeText(this, "Saved $saveCount/10 - Interstitial at 10", Toast.LENGTH_SHORT).show()

        // Show interstitial after every 10 saves
        if (saveCount >= 10) {
            showInterstitialAd()
            prefs.edit().putInt("save_count", 0).apply() // Reset counter
        }
    }

    private fun showInterstitialAd() {
        if (interstitialAd != null) {
            Toast.makeText(this, "Showing interstitial ad!", Toast.LENGTH_SHORT).show()
            interstitialAd?.show(this)
            // Reload for next time
            loadInterstitialAd()
        } else {
            Toast.makeText(this, "Interstitial not ready, loading...", Toast.LENGTH_SHORT).show()
            loadInterstitialAd()
        }
    }

    private fun openMediaViewer(item: MediaItem) {
        val intent = Intent(this, MediaViewerActivity::class.java)
        // Pass media item data
        intent.putExtra("MEDIA_PATH", item.path)
        intent.putExtra("MEDIA_NAME", item.fileName)
        intent.putExtra("MEDIA_SIZE", item.size)
        intent.putExtra("MEDIA_TYPE", item.type.name)
        intent.putExtra("MEDIA_DATE", item.dateModified)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        bottomAdView?.resume()
    }

    override fun onPause() {
        bottomAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        bottomAdView?.destroy()
        adapter.destroyNativeAds()
    }
}

// Placeholder class for native ad positions
data class NativeAdPlaceholder(val position: Int)
