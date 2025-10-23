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
    private const val TEST_MODE = false  // Set to false for production release

    @Volatile
    private var isInitialized = false

    private lateinit var appContext: Context

    // Callback for when Unity Ads finishes initializing
    private val initializationCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context) {
        appContext = context.applicationContext

        Log.d(TAG, "=== INITIALIZING UNITY ADS ===")
        Log.d(TAG, "Game ID: $GAME_ID")
        Log.d(TAG, "Test Mode: $TEST_MODE")

        UnityAds.initialize(
            appContext,
            GAME_ID,
            TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    Log.d(TAG, "=== UNITY ADS INITIALIZATION SUCCESS ===")
                    isInitialized = true

                    // Notify all waiting callbacks
                    Log.d(TAG, "Notifying ${initializationCallbacks.size} waiting callbacks")
                    initializationCallbacks.forEach { 
                        try {
                            it.invoke()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in initialization callback", e)
                        }
                    }
                    initializationCallbacks.clear()
                }

                override fun onInitializationFailed(
                    reason: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    Log.e(TAG, "=== UNITY ADS INITIALIZATION FAILED ===")
                    Log.e(TAG, "Reason: $reason")
                    Log.e(TAG, "Message: $message")
                    isInitialized = false
                }
            }
        )
    }

    fun isReady(): Boolean {
        return isInitialized
    }

    /**
     * Register a callback to be called when Unity Ads is ready
     * If already initialized, calls immediately
     */
    fun onReady(callback: () -> Unit) {
        if (isInitialized) {
            Log.d(TAG, "Unity Ads already initialized - calling callback immediately")
            try {
                callback.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing immediate callback", e)
            }
        } else {
            Log.d(TAG, "Unity Ads not initialized yet - adding callback to queue")
            initializationCallbacks.add(callback)
        }
    }
}
