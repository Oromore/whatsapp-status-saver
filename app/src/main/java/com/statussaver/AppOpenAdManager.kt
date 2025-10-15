package com.statussaver

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.*

/**
 * Manages App Open Ads that show when app comes to foreground
 */
class AppOpenAdManager(private val application: Application) : 
    LifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var currentActivity: Activity? = null

    // Test App Open Ad ID - Replace with real ID after testing
    private val adUnitId = "ca-app-pub-3940256099942544/9257395921" // TEST ID
    
    // Real ID (use after testing): ca-app-pub-5419078989451944/8224717447

    private val prefs by lazy { 
        application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) 
    }

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_TIMEOUT = 4 * 3600 * 1000 // 4 hours in milliseconds
    }

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Check if ad is available and not expired
     */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo()
    }

    /**
     * Check if ad was loaded less than 4 hours ago
     */
    private fun wasLoadTimeLessThanNHoursAgo(): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < numMilliSecondsPerHour * 4
    }

    /**
     * Load the App Open Ad
     */
    fun fetchAd() {
        if (isAdAvailable()) {
            Log.d(TAG, "Ad already loaded")
            return
        }

        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            application,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open Ad loaded successfully")
                    appOpenAd = ad
                    loadTime = Date().time
                    
                    currentActivity?.let {
                        Toast.makeText(it, "App Open Ad loaded!", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Failed to load App Open Ad: ${error.message}")
                    appOpenAd = null
                    
                    currentActivity?.let {
                        Toast.makeText(it, "App Open Ad failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    /**
     * Show the App Open Ad if conditions are met
     */
    fun showAdIfAvailable(activity: Activity) {
        // Don't show if already showing or not available
        if (isShowingAd || !isAdAvailable()) {
            Log.d(TAG, "Ad not ready. Loading new ad...")
            fetchAd()
            return
        }

        // Check if we should show based on open count (every 3rd open)
        var openCount = prefs.getInt("app_open_count", 0)
        openCount++
        prefs.edit().putInt("app_open_count", openCount).apply()

        Log.d(TAG, "App opened $openCount times")
        Toast.makeText(activity, "App opened $openCount times", Toast.LENGTH_SHORT).show()

        if (openCount % 2 != 0) {
            Log.d(TAG, "Not showing ad - wait for 2nd open")
            return
        }

        // Show the ad
        Log.d(TAG, "Showing App Open Ad now!")
        Toast.makeText(activity, "Showing App Open Ad now!", Toast.LENGTH_SHORT).show()

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed")
                appOpenAd = null
                isShowingAd = false
                fetchAd() // Load next ad
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Ad failed to show: ${error.message}")
                appOpenAd = null
                isShowingAd = false
                fetchAd()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed successfully")
                isShowingAd = true
            }
        }

        appOpenAd?.show(activity)
    }

    /**
     * Lifecycle method to detect when app comes to foreground
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onMoveToForeground() {
        currentActivity?.let { activity ->
            Log.d(TAG, "App moved to foreground")
            showAdIfAvailable(activity)
        }
    }

    // Activity lifecycle callbacks to track current activity
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
