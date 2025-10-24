package com.statussaver

import android.app.Activity
import android.util.Log
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions

/**
 * Manages interstitial ad triggers using Unity Ads 4.x API:
 * 1. After 7 status saves (no cooldown)
 * 2. After user interaction with app (3-minute cooldown)
 */
class InterstitialAdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val INTERSTITIAL_AD_UNIT_ID = "Interstitial_Android"
        private const val SAVE_COUNT_KEY = "interstitial_save_count"
        private const val LAST_APP_INTERACTION_AD_KEY = "last_app_interaction_ad_time"
        private const val COOLDOWN_MINUTES = 3  // Changed from 10 to 3 minutes
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
     * No cooldown - shows every 7 saves
     */
    fun trackSave() {
        if (!UnityAdsManager.isReady()) {
            Log.d(TAG, "Unity Ads not ready - skipping save tracking")
            return
        }

        var saveCount = prefs.getInt(SAVE_COUNT_KEY, 0)
        saveCount++
        prefs.edit().putInt(SAVE_COUNT_KEY, saveCount).apply()

        Log.d(TAG, "Save tracked: $saveCount/7")

        // Show interstitial every 7 saves
        if (saveCount >= 7) {
            showInterstitial(updateCooldown = false)
            prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply() // Reset counter
        }
    }

    /**
     * Show interstitial after user interaction with app
     * Has 3-minute cooldown
     */
    fun trackAppInteraction() {
        if (!UnityAdsManager.isReady()) {
            Log.d(TAG, "Unity Ads not ready - skipping app interaction tracking")
            return
        }

        // Check if 3-minute cooldown has passed
        val lastInterstitialTime = prefs.getLong(LAST_APP_INTERACTION_AD_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAd = currentTime - lastInterstitialTime

        if (timeSinceLastAd < COOLDOWN_MS) {
            val minutesUntilNextAd = ((COOLDOWN_MS - timeSinceLastAd) / 1000 / 60).toInt()
            Log.d(TAG, "App interaction interstitial on cooldown - $minutesUntilNextAd minutes remaining")
            return
        }

        showInterstitial(updateCooldown = true)
    }

    private fun showInterstitial(updateCooldown: Boolean) {
        if (!isAdLoaded) {
            Log.d(TAG, "Interstitial ad not loaded yet")
            if (!isLoadingAd) {
                loadInterstitial() // Try loading
            }
            return
        }

        Log.d(TAG, "Showing interstitial ad (updateCooldown: $updateCooldown)")

        // Create UnityAdsShowOptions as required by Unity Ads 4.x
        val showOptions = UnityAdsShowOptions()

        UnityAds.show(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            showOptions,
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

                    // Only update cooldown timestamp for app interaction ads
                    if (updateCooldown) {
                        prefs.edit()
                            .putLong(LAST_APP_INTERACTION_AD_KEY, System.currentTimeMillis())
                            .apply()
                        Log.d(TAG, "App interaction cooldown timestamp updated")
                    }

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
