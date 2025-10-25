package com.statussaver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.statussaver.core.FileSaver
import com.statussaver.core.MediaItem
import com.statussaver.core.StatusScanner
import com.statussaver.databinding.ActivityMediaListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MediaListActivity"
        // Get reference to the shared banner manager from MainActivity
        private val bannerAdManager: BannerAdManager?
            get() = MainActivity.Companion::class.java.getDeclaredField("bannerAdManager").let { field ->
                field.isAccessible = true
                field.get(null) as? BannerAdManager
            }
    }

    private lateinit var binding: ActivityMediaListBinding
    private lateinit var scanner: StatusScanner
    private lateinit var fileSaver: FileSaver
    private lateinit var adapter: MediaAdapter
    private var mediaType: String = "IMAGE"

    private lateinit var interstitialAdManager: InterstitialAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MediaListActivity onCreate ===")

        scanner = StatusScanner(this)
        fileSaver = FileSaver(this)

        // Get media type from intent
        mediaType = intent.getStringExtra("MEDIA_TYPE") ?: "IMAGE"
        Log.d(TAG, "Media type: $mediaType")

        // Initialize interstitial ad manager
        interstitialAdManager = InterstitialAdManager(this)

        // Move the existing banner to this activity's container
        Log.d(TAG, "Moving banner to MediaListActivity")
        bannerAdManager?.loadBanner(binding.adContainer)

        setupToolbar()
        setupRecyclerView()
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
                        adapter.submitList(mediaList)
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

    private fun saveMedia(item: MediaItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = fileSaver.saveToDownloads(item)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@MediaListActivity, "Saved to Downloads/WhatsAppStatus!", Toast.LENGTH_LONG).show()

                    // Track save for interstitial
                    interstitialAdManager.trackSave()
                } else {
                    Toast.makeText(this@MediaListActivity, "Failed to save", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openMediaViewer(item: MediaItem) {
        val intent = Intent(this, MediaViewerActivity::class.java)
        intent.putExtra("MEDIA_PATH", item.path)
        intent.putExtra("MEDIA_NAME", item.fileName)
        intent.putExtra("MEDIA_SIZE", item.size)
        intent.putExtra("MEDIA_TYPE", item.type.name)
        intent.putExtra("MEDIA_DATE", item.dateModified)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume ===")

        // Ensure banner is in our container
        bannerAdManager?.loadBanner(binding.adContainer)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== onPause ===")
        // DON'T hide banner - let it stay visible
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== onDestroy ===")
        // DON'T destroy banner - it belongs to MainActivity lifecycle
    }
}
