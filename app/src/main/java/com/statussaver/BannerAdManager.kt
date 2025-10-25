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
        private const val BANNER_AD_UNIT_ID = "WhatsApp_status_saver_banner"
        private const val BASE_RETRY_DELAY_MS = 2000L // Base retry delay: 2 seconds
        private const val MAX_RETRY_DELAY_MS = 10000L // Max retry delay: 10 seconds
        private const val HEALTH_CHECK_INTERVAL_MS = 2000L // Check health every 2 seconds
        private const val MAX_RETRIES = -1 // Unlimited retries - NEVER GIVE UP!
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

        // Unlimited retries - never give up!
        retryCount++
        
        // Calculate exponential backoff delay: 2s, 4s, 6s, 8s, 10s, then reset
        val currentDelay = (BASE_RETRY_DELAY_MS * retryCount).coerceAtMost(MAX_RETRY_DELAY_MS)
        
        // Reset retry count if we've reached max delay (creates the cycle)
        if (currentDelay >= MAX_RETRY_DELAY_MS) {
            retryCount = 0
        }
        
        Log.d(TAG, "Scheduling retry in ${currentDelay}ms (attempt $retryCount)")

        cancelRetry()
        retryRunnable = Runnable {
            Log.d(TAG, "Retrying banner load...")
            createBanner()
        }

        retryHandler.postDelayed(retryRunnable!!, currentDelay)
    }

    private fun cancelRetry() {
        retryRunnable?.let {
            retryHandler.removeCallbacks(it)
            retryRunnable = null
        }
    }

    /**
     * Start health check - verifies banner is alive every 2 seconds
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

        // Check if container is attached to window
        if (container?.isAttachedToWindow == false) {
            Log.w(TAG, "Health check WARNING: Container not attached to window")
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

        // Check if container is visible
        if (container?.visibility != View.VISIBLE) {
            Log.w(TAG, "Health check FAILED: Container not visible - making visible")
            activity.runOnUiThread {
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
