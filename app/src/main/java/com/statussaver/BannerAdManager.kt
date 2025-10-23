package com.statussaver

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages bottom banner ads using Unity Ads 4.x API
 * Unity handles refresh automatically via dashboard settings
 * Hides during ad-free periods
 */
class BannerAdManager(private val activity: Activity) : BannerView.IListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
    }

    private var bannerView: BannerView? = null
    private var bannerContainer: FrameLayout? = null
    private var isLoadingBanner = false

    // File logging for debugging
    private val logFile = File(activity.getExternalFilesDir(null), "unity_ads_debug.txt")

    private fun logToFile(message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            logFile.appendText("[$timestamp] $message\n")
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun loadBanner(container: FrameLayout) {
        logToFile("=== LOAD BANNER CALLED ===")
        bannerContainer = container

        // Check if Unity Ads is ready
        if (!UnityAdsManager.isReady()) {
            logToFile("ERROR: Unity Ads not initialized yet - cannot load banner")
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        // Check if user is in ad-free period
        if (UnityAdsManager.isAdFree()) {
            logToFile("INFO: Ad-free period active - hiding banner")
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        // Prevent multiple simultaneous loads
        if (isLoadingBanner) {
            logToFile("WARNING: Banner already loading - skipping")
            return
        }

        logToFile("INFO: Starting banner load")
        logToFile("Placement ID: $BANNER_AD_UNIT_ID")

        // Remove old banner if exists
        bannerView?.destroy()
        container.removeAllViews()

        isLoadingBanner = true

        try {
            // Create new banner
            logToFile("Creating BannerView with getDynamicSize()")
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize.getDynamicSize(activity))
            bannerView?.listener = this

            logToFile("Adding BannerView to container")
            container.addView(bannerView)
            container.visibility = View.VISIBLE

            logToFile("Calling banner.load()...")
            bannerView?.load()
        } catch (e: Exception) {
            logToFile("EXCEPTION creating banner: ${e.message}")
            logToFile("Stack trace: ${e.stackTraceToString()}")
            isLoadingBanner = false
            container.visibility = View.GONE
        }
    }

    fun destroyBanner() {
        logToFile("Destroying banner")
        bannerView?.destroy()
        bannerView = null
        bannerContainer?.removeAllViews()
        bannerContainer?.visibility = View.GONE
        bannerContainer = null
        isLoadingBanner = false
    }

    // BannerView.IListener callbacks
    override fun onBannerLoaded(bannerAdView: BannerView?) {
        logToFile("=== BANNER LOADED SUCCESSFULLY ===")
        isLoadingBanner = false
        bannerAdView?.visibility = View.VISIBLE
        bannerContainer?.visibility = View.VISIBLE
    }

    override fun onBannerClick(bannerAdView: BannerView?) {
        logToFile("Banner clicked")
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        logToFile("=== BANNER FAILED TO LOAD ===")
        logToFile("Error Code: ${errorInfo?.errorCode}")
        logToFile("Error Message: ${errorInfo?.errorMessage}")
        isLoadingBanner = false
        
        // KEEP CONTAINER VISIBLE SO YOU CAN SEE IT FAILED
        // bannerContainer?.visibility = View.GONE
    }

    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
        logToFile("Banner left application")
    }

    override fun onBannerShown(bannerAdView: BannerView?) {
        logToFile("Banner shown")
    }

    fun getLogFilePath(): String = logFile.absolutePath
}
