package com.statussaver

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.statussaver.core.FileSaver
import com.statussaver.core.MediaItem
import com.statussaver.core.MediaType
import com.statussaver.core.getFormattedSize
import com.statussaver.databinding.ActivityMediaViewerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMediaViewerBinding
    private lateinit var fileSaver: FileSaver
    private var player: ExoPlayer? = null
    private lateinit var mediaItem: MediaItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileSaver = FileSaver(this)

        // Get media item from intent
        val path = intent.getStringExtra("MEDIA_PATH") ?: ""
        val name = intent.getStringExtra("MEDIA_NAME") ?: ""
        val size = intent.getLongExtra("MEDIA_SIZE", 0L)
        val typeString = intent.getStringExtra("MEDIA_TYPE") ?: "IMAGE"
        val date = intent.getLongExtra("MEDIA_DATE", 0L)
        
        val type = MediaType.valueOf(typeString)
        mediaItem = MediaItem(path, name, type, size, date)
        
        displayMedia(mediaItem)
        
        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveMedia()
        }
    }

    private fun displayMedia(item: MediaItem) {
        this.mediaItem = item
        
        binding.fileName.text = item.fileName
        binding.fileInfo.text = item.getFormattedSize()

        when (item.type) {
            MediaType.IMAGE -> displayImage(item)
            MediaType.VIDEO -> displayVideo(item)
            MediaType.AUDIO -> displayAudio(item)
        }

        // Check if already saved
        if (fileSaver.isAlreadySaved(item)) {
            binding.btnSave.text = getString(R.string.already_saved)
            binding.btnSave.isEnabled = false
        }
    }

    private fun displayImage(item: MediaItem) {
        binding.imageView.visibility = View.VISIBLE
        binding.videoPlayer.visibility = View.GONE
        binding.audioPlayer.visibility = View.GONE

        Glide.with(this)
            .load(item.path)
            .into(binding.imageView)
    }

    private fun displayVideo(item: MediaItem) {
        binding.imageView.visibility = View.GONE
        binding.videoPlayer.visibility = View.VISIBLE
        binding.audioPlayer.visibility = View.GONE

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.videoPlayer.player = exoPlayer
            
            val mediaItem = ExoMediaItem.fromUri(item.path)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    private fun displayAudio(item: MediaItem) {
        binding.imageView.visibility = View.GONE
        binding.videoPlayer.visibility = View.GONE
        binding.audioPlayer.visibility = View.VISIBLE

        binding.audioFileName.text = item.fileName
        binding.audioFileSize.text = item.getFormattedSize()
        binding.audioStatus.text = "Tap save to download this audio"
    }

    private fun saveMedia() {
        binding.btnSave.isEnabled = false
        binding.btnSave.text = getString(R.string.saving)

        CoroutineScope(Dispatchers.IO).launch {
            val success = fileSaver.saveToDownloads(mediaItem)

            withContext(Dispatchers.Main) {
                if (success) {
                    binding.btnSave.text = getString(R.string.saved)
                    Toast.makeText(this@MediaViewerActivity, "Saved to Downloads!", Toast.LENGTH_SHORT).show()
                    
                    // Increment save counter for interstitial
                    incrementSaveCounter()
                } else {
                    binding.btnSave.text = getString(R.string.save)
                    binding.btnSave.isEnabled = true
                    Toast.makeText(this@MediaViewerActivity, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun incrementSaveCounter() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        var saveCount = prefs.getInt("save_count", 0)
        saveCount++
        prefs.edit().putInt("save_count", saveCount).apply()

        // Note: Save counter is shared - interstitial will show in MediaListActivity when it reaches 10
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
