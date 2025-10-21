package com.statussaver

import android.app.Application

class StatusSaverApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Unity Ads singleton
        UnityAdsManager.initialize(this)
    }
}
