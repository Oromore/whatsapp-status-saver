package com.statussaver

import android.app.Application
import android.util.Log
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

class StatusSaverApp : Application() {

    companion object {
        private const val TAG = "StatusSaverApp"
        
        // AppMetrica API Key
        private const val APPMETRICA_API_KEY = "c5dbb9b3-7e1a-4c6b-8b25-902a2133789b"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "=== STATUS SAVER APP STARTING ===")
        
        // Initialize AppMetrica Analytics
        initializeAppMetrica()
        
        // Initialize Yandex Mobile Ads SDK
        // AppMetrica automatically integrates with Yandex Ads
        YandexAdsManager.initialize(this)

        Log.d(TAG, "AppMetrica and Yandex Ads initialization started")
    }
    
    private fun initializeAppMetrica() {
        Log.d(TAG, "Initializing AppMetrica...")
        
        // Create AppMetrica configuration
        val config = AppMetricaConfig.newConfigBuilder(APPMETRICA_API_KEY)
            .withLogs() // Enable logs for debugging (disable in production)
            .withLocationTracking(false) // Disable location tracking for privacy
            .withSessionTimeout(30) // Session timeout in seconds
            .build()

        // Initialize AppMetrica
        AppMetrica.activate(applicationContext, config)
        
        // Enable activity auto-tracking (optional but recommended)
        AppMetrica.enableActivityAutoTracking(this)
        
        Log.d(TAG, "AppMetrica initialized successfully")
    }
}
