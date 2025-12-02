package com.statussaver

import android.content.Context
import android.util.Log
import com.yandex.mobile.ads.common.MobileAds

/**
 * Yandex Mobile Ads SDK Manager (PURE YANDEX - NO UNITY)
 * SDK ID: 17685522
 * 
 * For RuStore distribution - Yandex ads only
 * Use TEST_MODE = true for testing in Nigeria
 * Use TEST_MODE = false for production RuStore release
 */
object YandexAdsManager {

    private const val TAG = "YandexAdsManager"
    private const val SDK_ID = "17685522"
    
    // ========== TESTING TOGGLE ==========
    // Set to TRUE for testing (uses Yandex demo ads that work worldwide)
    // Set to FALSE for production (uses your real ad units)
    const val TEST_MODE = false // <-- CHANGE THIS TO false BEFORE RUSTORE UPLOAD!
    
    @Volatile
    private var isInitialized = false

    private val initializationCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Yandex Ads already initialized")
            return
        }

        Log.d(TAG, "=== INITIALIZING YANDEX MOBILE ADS (PURE YANDEX) ===")
        Log.d(TAG, "SDK ID: $SDK_ID")
        Log.d(TAG, "TEST MODE: $TEST_MODE")
        if (TEST_MODE) {
            Log.w(TAG, "⚠️ USING TEST ADS - REMEMBER TO SET TEST_MODE = false FOR PRODUCTION!")
        }

        try {
            // Enable verbose logging for debugging
            MobileAds.enableLogging(true)
            
            // Initialize Yandex Mobile Ads SDK (NO Unity adapter)
            MobileAds.initialize(context.applicationContext) {
                Log.d(TAG, "=== YANDEX ADS INITIALIZED SUCCESSFULLY ===")
                Log.d(TAG, "Pure Yandex - No mediation")
                isInitialized = true
                
                // Notify all waiting callbacks
                synchronized(initializationCallbacks) {
                    Log.d(TAG, "Notifying ${initializationCallbacks.size} callbacks")
                    initializationCallbacks.forEach { callback ->
                        try {
                            callback.invoke()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in callback", e)
                        }
                    }
                    initializationCallbacks.clear()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== YANDEX ADS INITIALIZATION FAILED ===", e)
            isInitialized = false
        }
    }

    fun isReady(): Boolean = isInitialized

    /**
     * Register callback to be called when Yandex Ads is ready
     * If already initialized, calls immediately
     */
    fun onReady(callback: () -> Unit) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized - calling callback immediately")
            try {
                callback.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing callback", e)
            }
        } else {
            Log.d(TAG, "Not initialized yet - queueing callback")
            synchronized(initializationCallbacks) {
                initializationCallbacks.add(callback)
            }
        }
    }
    
    /**
     * Show Yandex Debug Panel for testing
     * Call this from MainActivity to see ad integration status
     */
    fun showDebugPanel(context: Context) {
        if (isInitialized) {
            MobileAds.showDebugPanel(context)
        } else {
            Log.e(TAG, "Cannot show debug panel - Yandex not initialized")
        }
    }
}
