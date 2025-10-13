package com.statussaver

import android.app.Application
import com.google.android.gms.ads.MobileAds

class StatusSaverApp : Application() {

    private lateinit var appOpenAdManager: AppOpenAdManager

    override fun onCreate() {
        super.onCreate()

        // Initialize Google Mobile Ads
        MobileAds.initialize(this) { initializationStatus ->
            // Initialization complete
        }

        // Initialize App Open Ad Manager
        appOpenAdManager = AppOpenAdManager(this)
        appOpenAdManager.fetchAd()
    }
}
