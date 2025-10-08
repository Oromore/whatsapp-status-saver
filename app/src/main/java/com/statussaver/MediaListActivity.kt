package com.statussaver

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
import com.google.android.gms.ads.LoadAdError
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
        
        setupToolbar()
        setupRecyclerView()
        loadInterstitialAd()
        loadMedia()
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
                        
                        // Insert native ads every 3 items
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
        val adLoader = AdLoader.Builder(this, "ca-app-pub-5419078989451944/1000290974")
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
            "ca-app-pub-5419078989451944/4796614493", // Your Interstitial Ad ID
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
                    Toast.makeText(this@MediaListActivity, "Saved!", Toast.LENGTH_SHORT).show()
                    
                    // Increment persistent save counter
                    incrementSaveCounter()
                } else {
                    Toast.makeText(this@MediaListActivity, "Failed to save", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun incrementSaveCounter() {
        var saveCount = prefs.getInt("save_count", 0)
        saveCount++
        prefs.edit().putInt("save_count", saveCount).apply()

        // Show interstitial after every 10 saves
        if (saveCount >= 10) {
            showInterstitialAd()
            prefs.edit().putInt("save_count", 0).apply() // Reset counter
        }
    }

    private fun showInterstitialAd() {
        interstitialAd?.show(this) ?: run {
            // Ad not ready, load a new one
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

    override fun onDestroy() {
        super.onDestroy()
        adapter.destroyNativeAds()
    }
}

// Placeholder class for native ad positions
data class NativeAdPlaceholder(val position: Int)
