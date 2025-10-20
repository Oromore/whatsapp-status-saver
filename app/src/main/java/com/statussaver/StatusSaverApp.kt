package com.statussaver

import android.app.Application

class StatusSaverApp : Application() {

    private lateinit var unityAdsManager: UnityAdsManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Unity Ads
        unityAdsManager = UnityAdsManager(this)
        unityAdsManager.initialize()
    }
}
