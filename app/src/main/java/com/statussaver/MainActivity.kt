package com.statussaver

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.statussaver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== MainActivity onCreate ===")

        // Load persistent banner - it handles everything internally
        Log.d(TAG, "Loading persistent banner")
        BannerAdManager.loadBanner(this, binding.adContainer)

        // Your other initialization code here
        setupUI()
    }

    private fun setupUI() {
        // Your UI setup code
        // E.g., button click listeners, etc.
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "=== onResume ===")

        // Just ensure banner is in our container
        // Banner is already loaded and streaming, just moves to our container
        BannerAdManager.loadBanner(this, binding.adContainer)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "=== onPause ===")
        // DON'T hide banner - it stays visible 24/7
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
