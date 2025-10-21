package com.statussaver

import android.app.Activity
import android.util.Log
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds

/**
 * Manages interstitial ad triggers using Unity Ads 4.x API:
 * 1. After 7 status saves
 * 2. After user interaction with app (10-minute cooldown)
 */
class InterstitialAdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val INTERSTITIAL_AD_UNIT_ID = "Interstitial_Android"
        private const val SAVE_COUNT_KEY = "interstitial_save_count"
        private const val LAST_INTERSTITIAL_TIME_KEY = "last_interstitial_time"
        private const val COOLDOWN_MINUTES = 10
        private const val COOLDOWN_MS = COOLDOWN_MINUTES * 60 * 1000L
    }

    private val prefs = activity.getSharedPreferences("ad_prefs", Activity.MODE_PRIVATE)
    private var isAdLoaded = false
    private var isLoadingAd = false

    init {
        // Load interstitial ad when Unity Ads is ready
        if (UnityAdsManager.isReady()) {
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        if (!UnityAdsManager.isReady() || isLoadingAd) {
            return
        }

        isLoadingAd = true
        Log.d(TAG, "Loading interstitial ad")

        UnityAds.load(INTERSTITIAL_AD_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                Log.d(TAG, "Interstitial loaded: $placementId")
                isAdLoaded = true
                isLoadingAd = false
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAds.UnityAdsLoadError,
                message: String
            ) {
                Log.e(TAG, "Interstitial failed to load: $error - $message")
                isAdLoaded = false
                isLoadingAd = false
            }
        })
    }

    /**
     * Track a status save and show interstitial if threshold reached
     */
    fun trackSave() {
        if (!UnityAdsManager.isReady()) {
            Log.d(TAG, "Unity Ads not ready - skipping save tracking")
            return
        }

        if (UnityAdsManager.isAdFree()) {
            Log.d(TAG, "Ad-free period active - skipping save interstitial")
            return
        }

        var saveCount = prefs.getInt(SAVE_COUNT_KEY, 0)
        saveCount++
        prefs.edit().putInt(SAVE_COUNT_KEY, saveCount).apply()

        Log.d(TAG, "Save tracked: $saveCount/7")

        // Show interstitial every 7 saves
        if (saveCount >= 7) {
            showInterstitial()
            prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply() // Reset counter
        }
    }

    /**
     * Show interstitial after user interaction with app
     */
    fun trackAppInteraction() {
        if (!UnityAdsManager.isReady()) {
            Log.d(TAG, "Unity Ads not ready - skipping app interaction tracking")
            return
        }

        if (UnityAdsManager.isAdFree()) {
            Log.d(TAG, "Ad-free period active - skipping app open interstitial")
            return
        }

        // Check if 10-minute cooldown has passed
        val lastInterstitialTime = prefs.getLong(LAST_INTERSTITIAL_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastInterstitialTime

        if (timeSinceLastAd < COOLDOWN_MS) {
            val minutesUntilNextAd = ((COOLDOWN_MS - timeSinceLastAd) / 1000 / 60).toInt()
            Log.d(TAG, "App open interstitial on cooldown - $minutesUntilNextAd minutes remaining")
            return
        }

        showInterstitial()
    }

    private fun showInterstitial() {
        if (!isAdLoaded) {
            Log.d(TAG, "Interstitial ad not loaded yet")
            if (!isLoadingAd) {
                loadInterstitial() // Try loading
            }
            return
        }

        Log.d(TAG, "Showing interstitial ad")

        UnityAds.show(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    Log.e(TAG, "Interstitial show failed: $error - $message")
                    isAdLoaded = false
                    loadInterstitial() // Reload for next time
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Interstitial show started")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Interstitial clicked")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    Log.d(TAG, "Interstitial show completed: $state")

                    // Update last show time
                    prefs.edit()
                        .putLong(LAST_INTERSTITIAL_TIME_KEY, System.currentTimeMillis())
                        .apply()

                    // Reload ad for next time
                    isAdLoaded = false
                    loadInterstitial()
                }
            }
        )
    }

    fun resetSaveCount() {
        prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply()
    }
}
