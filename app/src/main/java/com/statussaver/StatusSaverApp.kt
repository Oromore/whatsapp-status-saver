package com.statussaver

import android.app.Application
import android.util.Log

class StatusSaverApp : Application() {

    companion object {
        private const val TAG = "StatusSaverApp"
    }

    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "=== STATUS SAVER APP STARTING ===")
        
        // Initialize Yandex Mobile Ads SDK
        // Unity Ads adapter loads automatically
        // Yandex dashboard handles geo-routing (CIS = Yandex, Rest = Unity)
        YandexAdsManager.initialize(this)
        
        Log.d(TAG, "Yandex Ads initialization started")
    }
}
