package com.statussaver

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds

/**
 * Manages both interstitial ad triggers:
 * 1. After 7 status saves
 * 2. After user interaction with app (10-minute cooldown)
 */
class InterstitialAdManager(private val context: Context) {

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val INTERSTITIAL_AD_UNIT_ID = "Interstitial_Android"
        private const val SAVE_COUNT_KEY = "interstitial_save_count"
        private const val LAST_INTERSTITIAL_TIME_KEY = "last_interstitial_time"
        private const val COOLDOWN_MINUTES = 10
        private const val COOLDOWN_MS = COOLDOWN_MINUTES * 60 * 1000L
    }

    private val unityAdsManager = UnityAdsManager(context)
    private val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)

    /**
     * Track a status save and show interstitial if threshold reached
     */
    fun trackSave(activity: Activity) {
        if (unityAdsManager.isAdFree(context)) {
            Log.d(TAG, "Ad-free period active - skipping save interstitial")
            return
        }

        var saveCount = prefs.getInt(SAVE_COUNT_KEY, 0)
        saveCount++
        prefs.edit().putInt(SAVE_COUNT_KEY, saveCount).apply()

        Log.d(TAG, "Save tracked: $saveCount/7")

        // Show interstitial every 7 saves
        if (saveCount >= 7) {
            showInterstitial(activity)
            prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply() // Reset counter
        }
    }

    /**
     * Show interstitial after user first interaction with app
     */
    fun trackAppInteraction(activity: Activity) {
        if (unityAdsManager.isAdFree(context)) {
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

        showInterstitial(activity)
    }

    private fun showInterstitial(activity: Activity) {
        if (!UnityAds.isReady(INTERSTITIAL_AD_UNIT_ID)) {
            Log.d(TAG, "Interstitial ad not ready")
            return
        }

        Log.d(TAG, "Showing interstitial ad")
        
        UnityAds.show(
            activity,
            INTERSTITIAL_AD_UNIT_ID,
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(adUnitId: String, error: UnityAds.UnityAdsShowError, message: String) {
                    Log.e(TAG, "Interstitial show failed: $error - $message")
                }

                override fun onUnityAdsShowStart(adUnitId: String) {
                    Log.d(TAG, "Interstitial show started")
                }

                override fun onUnityAdsShowClick(adUnitId: String) {
                    Log.d(TAG, "Interstitial clicked")
                }

                override fun onUnityAdsShowComplete(adUnitId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    Log.d(TAG, "Interstitial show completed: $state")
                    
                    // Update last show time
                    prefs.edit().putLong(LAST_INTERSTITIAL_TIME_KEY, System.currentTimeMillis()).apply()
                }
            }
        )
    }

    fun resetSaveCount() {
        prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply()
    }
}
