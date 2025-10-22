package com.statussaver

import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds

/**
 * Singleton manager for Unity Ads
 * Game ID: 5966081 (Android)
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"
    private const val GAME_ID = "5966081"
    private const val TEST_MODE = false // Set to false for production release
    
    @Volatile
    private var isInitialized = false
    
    private lateinit var appContext: Context
    private val prefs by lazy { 
        appContext.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE) 
    }
    
    // Callback for when Unity Ads finishes initializing
    private val initializationCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
        
        Log.d(TAG, "Initializing Unity Ads with Game ID: $GAME_ID (Test Mode: $TEST_MODE)")
        
        UnityAds.initialize(
            appContext,
            GAME_ID,
            TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "Unity Ads initialized successfully")
                    isInitialized = true
                    
                    // Notify all waiting callbacks
                    initializationCallbacks.forEach { it.invoke() }
                    initializationCallbacks.clear()
                }

                override fun onInitializationFailed(
                    reason: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    Log.e(TAG, "Unity Ads initialization failed: $reason - $message")
                    isInitialized = false
                }
            }
        )
    }

    fun isReady(): Boolean = isInitialized
    
    /**
     * Register a callback to be called when Unity Ads is ready
     * If already initialized, calls immediately
     */
    fun onReady(callback: () -> Unit) {
        if (isInitialized) {
            callback.invoke()
        } else {
            initializationCallbacks.add(callback)
        }
    }

    fun isAdFree(): Boolean {
        val adFreeExpiryTime = prefs.getLong("ad_free_expiry", 0)
        val currentTime = System.currentTimeMillis()
        return currentTime < adFreeExpiryTime
    }

    fun activateAdFree(durationMinutes: Long) {
        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefs.edit().putLong("ad_free_expiry", expiryTime).apply()
        Log.d(TAG, "Ad-free activated for $durationMinutes minutes")
    }

    fun getTimeUntilAdFreeEnds(): Long {
        val adFreeExpiryTime = prefs.getLong("ad_free_expiry", 0)
        val currentTime = System.currentTimeMillis()

        return if (currentTime < adFreeExpiryTime) {
            (adFreeExpiryTime - currentTime) / 1000 // Return in seconds
        } else {
            0
        }
    }
}
