package com.statussaver

import android.content.Context
import android.util.Log
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.UnityAds
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton manager for Unity Ads
 * Game ID: 5966081 (Android)
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"
    private const val GAME_ID = "5966081"
    private const val TEST_MODE = false

    @Volatile
    private var isInitialized = false

    private lateinit var appContext: Context
    private val prefs by lazy {
        appContext.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
    }

    private val logFile by lazy {
        File(appContext.getExternalFilesDir(null), "unity_ads_debug.txt")
    }

    private fun logToFile(message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            logFile.appendText("[$timestamp] $message\n")
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    // Callback for when Unity Ads finishes initializing
    private val initializationCallbacks = mutableListOf<() -> Unit>()

    fun initialize(context: Context) {
        appContext = context.applicationContext

        logToFile("=== INITIALIZING UNITY ADS ===")
        logToFile("Game ID: $GAME_ID")
        logToFile("Test Mode: $TEST_MODE")

        UnityAds.initialize(
            appContext,
            GAME_ID,
            TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    logToFile("=== UNITY ADS INITIALIZATION SUCCESS ===")
                    isInitialized = true

                    // Notify all waiting callbacks
                    logToFile("Notifying ${initializationCallbacks.size} waiting callbacks")
                    initializationCallbacks.forEach { 
                        try {
                            it.invoke()
                        } catch (e: Exception) {
                            logToFile("ERROR in initialization callback: ${e.message}")
                        }
                    }
                    initializationCallbacks.clear()
                }

                override fun onInitializationFailed(
                    reason: UnityAds.UnityAdsInitializationError,
                    message: String
                ) {
                    logToFile("=== UNITY ADS INITIALIZATION FAILED ===")
                    logToFile("Reason: $reason")
                    logToFile("Message: $message")
                    isInitialized = false
                }
            }
        )
    }

    fun isReady(): Boolean {
        val ready = isInitialized
        logToFile("isReady() called - Result: $ready")
        return ready
    }

    /**
     * Register a callback to be called when Unity Ads is ready
     * If already initialized, calls immediately
     */
    fun onReady(callback: () -> Unit) {
        if (isInitialized) {
            logToFile("Unity Ads already initialized - calling callback immediately")
            try {
                callback.invoke()
            } catch (e: Exception) {
                logToFile("ERROR executing immediate callback: ${e.message}")
            }
        } else {
            logToFile("Unity Ads not initialized yet - adding callback to queue")
            initializationCallbacks.add(callback)
        }
    }

    fun isAdFree(): Boolean {
        val adFreeExpiryTime = prefs.getLong("ad_free_expiry", 0)
        val currentTime = System.currentTimeMillis()
        val isAdFree = currentTime < adFreeExpiryTime
        
        if (isAdFree) {
            val remainingSeconds = (adFreeExpiryTime - currentTime) / 1000
            logToFile("User is ad-free for $remainingSeconds more seconds")
        }
        
        return isAdFree
    }

    fun activateAdFree(durationMinutes: Long) {
        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefs.edit().putLong("ad_free_expiry", expiryTime).apply()
        logToFile("Ad-free activated for $durationMinutes minutes")
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
