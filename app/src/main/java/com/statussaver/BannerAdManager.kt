package com.statussaver

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * PERMANENT Banner Manager - Loads once, stays forever
 * Auto-retries on failure, survives network issues
 * Unity handles 15-second refresh automatically
 */
class BannerAdManager(private val activity: Activity) : BannerView.IListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
        private const val RETRY_DELAY_MS = 5000L // Retry every 5 seconds on failure
        private const val MAX_RETRIES = 10 // Try 10 times, then give up
    }

    private var bannerView: BannerView? = null
    private var container: FrameLayout? = null
    private var retryCount = 0
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    /**
     * Initialize and load banner - call this ONCE when Unity is ready
     * Banner stays alive forever after this
     */
    fun loadBanner(adContainer: FrameLayout) {
        Log.d(TAG, "=== LOADING PERMANENT BANNER ===")
        
        container = adContainer
        container?.visibility = View.VISIBLE

        // Don't reload if already exists and loaded
        if (bannerView != null) {
            Log.d(TAG, "Banner already exists and active")
            return
        }

        // Create banner
        createBanner()
    }

    private fun createBanner() {
        Log.d(TAG, "Creating banner (attempt ${retryCount + 1})")

        try {
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
            bannerView?.setListener(this)
            
            container?.removeAllViews()
            container?.addView(bannerView)
            
            // Load the banner
            bannerView?.load()
            
            Log.d(TAG, "Banner load() called")
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating banner", e)
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max retries reached - giving up")
            container?.visibility = View.GONE
            return
        }

        retryCount++
        Log.d(TAG, "Scheduling retry in ${RETRY_DELAY_MS}ms (attempt $retryCount/$MAX_RETRIES)")

        retryRunnable = Runnable {
            Log.d(TAG, "Retrying banner load...")
            createBanner()
        }

        retryHandler.postDelayed(retryRunnable!!, RETRY_DELAY_MS)
    }

    private fun cancelRetry() {
        retryRunnable?.let {
            retryHandler.removeCallbacks(it)
            retryRunnable = null
        }
    }

    /**
     * Only call this when app is REALLY closing
     */
    fun destroy() {
        Log.d(TAG, "Destroying banner (app closing)")
        
        cancelRetry()
        container?.removeAllViews()
        container?.visibility = View.GONE
        container = null
        
        bannerView?.destroy()
        bannerView = null
    }

    // ========== BannerView.IListener Callbacks ==========

    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "=== BANNER LOADED SUCCESSFULLY ===")
        Log.d(TAG, "Placement: ${bannerAdView?.placementId}")
        
        // Reset retry count on success
        retryCount = 0
        cancelRetry()
        
        activity.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            container?.visibility = View.VISIBLE
        }
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "=== BANNER FAILED TO LOAD ===")
        Log.e(TAG, "Placement: ${bannerAdView?.placementId}")
        Log.e(TAG, "Error Code: ${errorInfo?.errorCode}")
        Log.e(TAG, "Error Message: ${errorInfo?.errorMessage}")
        
        // Schedule retry
        scheduleRetry()
    }

    override fun onBannerClick(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner clicked: ${bannerAdView?.placementId}")
    }

    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner left application: ${bannerAdView?.placementId}")
    }

    override fun onBannerShown(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner shown: ${bannerAdView?.placementId}")
        // Unity will handle 15-second refresh automatically
    }
}
