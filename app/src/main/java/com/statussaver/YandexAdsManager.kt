package com.statussaver

import android.content.Context
import android.util.Log
import com.yandex.mobile.ads.common.MobileAds

/**
 * Yandex Mobile Ads SDK Manager
 * SDK ID: 17685522
 * 
 * Automatically loads Unity Ads adapter for mediation.
 * Yandex dashboard handles geo-routing (CIS = Yandex, Rest = Unity)
 */
object YandexAdsManager {

    private const val TAG = "YandexAdsManager"

    @Volatile
    private var isInitialized = false

    private val initializationCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Yandex Ads already initialized")
            return
        }

        Log.d(TAG, "=== INITIALIZING YANDEX MOBILE ADS ===")
        Log.d(TAG, "SDK ID: 17685522 (from AndroidManifest)")

        try {
            // Initialize Yandex Mobile Ads SDK
            // SDK reads yandex_mobile_ads_sdk_id from AndroidManifest
            // Unity Ads adapter loads automatically
            MobileAds.initialize(context.applicationContext) {
                Log.d(TAG, "=== YANDEX ADS INITIALIZED SUCCESSFULLY ===")
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
}
