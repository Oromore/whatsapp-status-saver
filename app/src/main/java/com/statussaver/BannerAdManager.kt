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
 * PERMANENT Banner Manager - ONE reliable instance that never dies
 * - Loads once when Unity is ready
 * - Health check every 5 seconds to verify banner is alive
 * - Auto-reloads if banner disappears or fails
 * - Survives network issues and Unity refresh failures
 */
class BannerAdManager(private val activity: Activity) : BannerView.IListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
        private const val RETRY_DELAY_MS = 5000L // Retry every 5 seconds on failure
        private const val HEALTH_CHECK_INTERVAL_MS = 5000L // Check health every 5 seconds
        private const val MAX_RETRIES = 10 // Try 10 times, then give up
    }

    private var bannerView: BannerView? = null
    private var container: FrameLayout? = null
    private var retryCount = 0
    private var isLoaded = false
    private var isDestroyed = false
    
    private val retryHandler = Handler(Looper.getMainLooper())
    private val healthCheckHandler = Handler(Looper.getMainLooper())
    
    private var retryRunnable: Runnable? = null
    private var healthCheckRunnable: Runnable? = null

    /**
     * Initialize and load banner - call this ONCE when Unity is ready
     * Banner stays alive forever after this
     */
    fun loadBanner(adContainer: FrameLayout) {
        Log.d(TAG, "=== LOADING PERMANENT BANNER ===")

        if (isDestroyed) {
            Log.w(TAG, "BannerAdManager is destroyed - cannot load")
            return
        }

        container = adContainer
        container?.visibility = View.VISIBLE

        // Don't reload if already exists and loaded
        if (bannerView != null && isLoaded) {
            Log.d(TAG, "Banner already exists and active")
            return
        }

        // Create banner
        createBanner()
    }

    private fun createBanner() {
        Log.d(TAG, "Creating banner (attempt ${retryCount + 1})")

        if (isDestroyed) {
            Log.w(TAG, "BannerAdManager is destroyed - aborting create")
            return
        }

        try {
            // Clean up old banner if exists
            bannerView?.destroy()
            container?.removeAllViews()

            // Create new banner
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
            bannerView?.setListener(this)

            container?.addView(bannerView)

            // Load the banner
            bannerView?.load()
            isLoaded = false

            Log.d(TAG, "Banner load() called")

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating banner", e)
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (isDestroyed) {
            return
        }

        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max retries reached - giving up")
            container?.visibility = View.GONE
            return
        }

        retryCount++
        Log.d(TAG, "Scheduling retry in ${RETRY_DELAY_MS}ms (attempt $retryCount/$MAX_RETRIES)")

        cancelRetry()
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
     * Start health check - verifies banner is alive every 5 seconds
     */
    private fun startHealthCheck() {
        Log.d(TAG, "Starting health check (every ${HEALTH_CHECK_INTERVAL_MS}ms)")

        cancelHealthCheck()
        
        healthCheckRunnable = object : Runnable {
            override fun run() {
                if (isDestroyed) {
                    Log.d(TAG, "Health check stopped - manager destroyed")
                    return
                }

                performHealthCheck()
                
                // Schedule next health check
                healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
            }
        }

        healthCheckHandler.postDelayed(healthCheckRunnable!!, HEALTH_CHECK_INTERVAL_MS)
    }

    private fun performHealthCheck() {
        Log.d(TAG, "=== HEALTH CHECK ===")

        // Check if banner view still exists
        if (bannerView == null) {
            Log.w(TAG, "Health check FAILED: Banner view is null - reloading")
            isLoaded = false
            retryCount = 0
            createBanner()
            return
        }

        // Check if banner is attached to container
        if (bannerView?.parent == null) {
            Log.w(TAG, "Health check FAILED: Banner not attached to parent - reloading")
            isLoaded = false
            retryCount = 0
            createBanner()
            return
        }

        // Check if banner is visible
        if (bannerView?.visibility != View.VISIBLE) {
            Log.w(TAG, "Health check FAILED: Banner not visible - making visible")
            activity.runOnUiThread {
                bannerView?.visibility = View.VISIBLE
                container?.visibility = View.VISIBLE
            }
        }

        Log.d(TAG, "Health check PASSED: Banner is alive and showing")
    }

    private fun cancelHealthCheck() {
        healthCheckRunnable?.let {
            healthCheckHandler.removeCallbacks(it)
            healthCheckRunnable = null
        }
    }

    /**
     * Only call this when app is REALLY closing
     */
    fun destroy() {
        Log.d(TAG, "Destroying banner (app closing)")

        isDestroyed = true
        isLoaded = false

        cancelRetry()
        cancelHealthCheck()

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
        isLoaded = true
        cancelRetry()

        activity.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            container?.visibility = View.VISIBLE
        }

        // Start health monitoring AFTER successful load
        startHealthCheck()
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "=== BANNER FAILED TO LOAD ===")
        Log.e(TAG, "Placement: ${bannerAdView?.placementId}")
        Log.e(TAG, "Error Code: ${errorInfo?.errorCode}")
        Log.e(TAG, "Error Message: ${errorInfo?.errorMessage}")

        isLoaded = false

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
