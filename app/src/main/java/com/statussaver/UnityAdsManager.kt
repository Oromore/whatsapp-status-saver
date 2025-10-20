package com.statussaver

import android.app.Activity
import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

/**
 * Main coordinator for all Unity Ads
 */
class UnityAdsManager(private val context: Context) {

    companion object {
        private const val TAG = "UnityAdsManager"
        private const val GAME_ID = "5966081"
    }

    fun initialize() {
        Log.d(TAG, "Initializing Unity Ads with Game ID: $GAME_ID")
        
        UnityAds.initialize(
            context,
            GAME_ID,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "Unity Ads initialized successfully")
                }

                override fun onInitializationFailed(reason: UnityAds.UnityAdsInitializationError, message: String) {
                    Log.e(TAG, "Unity Ads initialization failed: $reason - $message")
                }
            }
        )
    }

    fun isAdFree(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        val adFreeExpiryTime = prefs.getLong("ad_free_expiry", 0)
        val currentTime = System.currentTimeMillis()
        return currentTime < adFreeExpiryTime
    }

    fun activateAdFree(context: Context, durationMinutes: Long) {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefs.edit().putLong("ad_free_expiry", expiryTime).apply()
        Log.d(TAG, "Ad-free activated for $durationMinutes minutes")
    }

    fun getTimeUntilAdFreeEnds(context: Context): Long {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        val adFreeExpiryTime = prefs.getLong("ad_free_expiry", 0)
        val currentTime = System.currentTimeMillis()
        
        return if (currentTime < adFreeExpiryTime) {
            (adFreeExpiryTime - currentTime) / 1000 // Return in seconds
        } else {
            0
        }
    }
}
