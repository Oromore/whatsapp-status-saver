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
 * Singleton BannerAdManager - 24/7 Persistent Banner
 * Banner stays alive across all activities with automatic retry and refresh
 * Never hides - just moves between containers seamlessly
 */
object BannerAdManager : BannerView.IListener {

    private const val TAG = "BannerAdManager"
    private const val BANNER_AD_UNIT_ID = "Banner_Android"
    
    // Retry configuration
    private const val INITIAL_RETRY_DELAY = 1000L // 1 second
    private const val MAX_RETRY_DELAY = 3000L // 3 seconds max
    private const val MAX_RETRY_ATTEMPTS = 15
    
    private var bannerView: BannerView? = null
    private var currentContainer: FrameLayout? = null
    private var currentActivity: Activity? = null
    
    private var retryCount = 0
    private var currentRetryDelay = INITIAL_RETRY_DELAY
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null
    
    private var isLoading = false
    private var isInitialized = false

    /**
     * Load and display banner in the specified container
     * Reuses existing banner - just moves it to new container
     * Banner stays connected and refreshes automatically
     */
    fun loadBanner(activity: Activity, container: FrameLayout) {
        Log.d(TAG, "=== loadBanner() called for ${activity.javaClass.simpleName} ===")

        // Update current references
        currentActivity = activity
        currentContainer = container

        // Check if Unity Ads is ready
        if (!UnityAdsManager.isReady()) {
            Log.w(TAG, "Unity Ads not initialized yet - scheduling retry")
            scheduleRetry(activity, container)
            return
        }

        // If banner already exists and is healthy, just move it
        if (bannerView != null && isInitialized) {
            Log.d(TAG, "Banner exists and healthy - moving to new container")
            moveBannerToContainer(container)
            return
        }

        // Create new banner if doesn't exist or needs recreation
        if (bannerView == null) {
            createAndLoadBanner(activity, container)
        }
    }

    /**
     * Move existing banner to new container without reloading
     */
    private fun moveBannerToContainer(container: FrameLayout) {
        try {
            // Remove from old parent (if any)
            val oldParent = bannerView?.parent as? FrameLayout
            if (oldParent != null && oldParent != container) {
                Log.d(TAG, "Removing banner from old container")
                oldParent.removeView(bannerView)
            }

            // Add to new container if not already there
            if (bannerView?.parent != container) {
                Log.d(TAG, "Adding banner to new container")
                container.addView(bannerView)
            }

            // Keep container visible - banner handles its own refresh
            container.visibility = View.VISIBLE
            bannerView?.visibility = View.VISIBLE

            Log.d(TAG, "Banner moved successfully - staying connected")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error moving banner", e)
            // If move fails, try to recreate
            bannerView = null
            isInitialized = false
            currentActivity?.let { createAndLoadBanner(it, container) }
        }
    }

    /**
     * Create new banner and load it
     */
    private fun createAndLoadBanner(activity: Activity, container: FrameLayout) {
        if (isLoading) {
            Log.d(TAG, "Already loading banner - skipping")
            return
        }

        Log.d(TAG, "Creating new banner with placement: $BANNER_AD_UNIT_ID")
        isLoading = true

        try {
            // Create banner view
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
            bannerView?.setListener(this)

            // Add to container immediately (even before load completes)
            container.removeAllViews()
            container.addView(bannerView)
            container.visibility = View.VISIBLE

            // Load the banner
            Log.d(TAG, "Loading banner ad...")
            bannerView?.load()

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating banner", e)
            isLoading = false
            scheduleRetry(activity, container)
        }
    }

    /**
     * Schedule retry with exponential backoff
     */
    private fun scheduleRetry(activity: Activity, container: FrameLayout) {
        // Cancel any existing retry
        retryRunnable?.let { retryHandler.removeCallbacks(it) }

        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Max retry attempts reached - stopping retries")
            retryCount = 0
            currentRetryDelay = INITIAL_RETRY_DELAY
            return
        }

        retryCount++
        Log.d(TAG, "Scheduling retry #$retryCount in ${currentRetryDelay}ms")

        retryRunnable = Runnable {
            Log.d(TAG, "Executing retry #$retryCount")
            isLoading = false
            loadBanner(activity, container)
        }

        retryHandler.postDelayed(retryRunnable!!, currentRetryDelay)

        // Exponential backoff
        currentRetryDelay = (currentRetryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
    }

    /**
     * Cancel any pending retries
     */
    private fun cancelRetry() {
        retryRunnable?.let { 
            retryHandler.removeCallbacks(it)
            Log.d(TAG, "Cancelled pending retry")
        }
        retryRunnable = null
    }

    /**
     * DON'T USE THIS - Banner should stay visible
     * Kept for compatibility but does nothing
     */
    @Deprecated("Banner should never hide - it's 24/7", ReplaceWith(""))
    fun hideBanner() {
        Log.d(TAG, "hideBanner() called but ignored - banner stays visible 24/7")
        // DO NOTHING - banner stays visible
    }

    /**
     * DON'T USE THIS - Banner is always showing
     * Kept for compatibility
     */
    @Deprecated("Banner is always visible", ReplaceWith(""))
    fun showBanner() {
        Log.d(TAG, "showBanner() called - banner is already visible")
        // Banner is always visible, but ensure container is visible
        currentContainer?.visibility = View.VISIBLE
        bannerView?.visibility = View.VISIBLE
    }

    /**
     * ONLY destroy when app is completely closing
     * DON'T call this in onDestroy() of activities
     */
    fun destroyBanner() {
        Log.d(TAG, "Destroying banner (app closing)")
        
        // Cancel retries
        cancelRetry()
        
        // Clean up
        currentContainer?.removeAllViews()
        currentContainer = null
        currentActivity = null

        bannerView?.destroy()
        bannerView = null
        
        isInitialized = false
        isLoading = false
        retryCount = 0
        currentRetryDelay = INITIAL_RETRY_DELAY
    }

    // ========== BannerView.IListener Callbacks ==========

    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "=== BANNER LOADED SUCCESSFULLY ===")
        Log.d(TAG, "Placement: ${bannerAdView?.placementId}")

        isLoading = false
        isInitialized = true
        retryCount = 0
        currentRetryDelay = INITIAL_RETRY_DELAY
        
        // Cancel any pending retries
        cancelRetry()

        currentActivity?.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            currentContainer?.visibility = View.VISIBLE
        }

        Log.d(TAG, "Banner now streaming 24/7 - will auto-refresh")
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "=== BANNER FAILED TO LOAD ===")
        Log.e(TAG, "Placement: ${bannerAdView?.placementId}")
        Log.e(TAG, "Error Code: ${errorInfo?.errorCode}")
        Log.e(TAG, "Error Message: ${errorInfo?.errorMessage}")
        
        isLoading = false
        isInitialized = false

        // Schedule retry with backoff
        currentActivity?.let { activity ->
            currentContainer?.let { container ->
                scheduleRetry(activity, container)
            }
        }
    }

    override fun onBannerClick(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner clicked: ${bannerAdView?.placementId}")
    }

    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner left application: ${bannerAdView?.placementId}")
    }

    override fun onBannerShown(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner shown: ${bannerAdView?.placementId}")
    }
}
