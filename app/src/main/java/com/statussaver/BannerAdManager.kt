package com.statussaver

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * Manages bottom banner ads using Unity Ads 4.x API
 * Unity handles refresh automatically via dashboard settings
 * Hides during ad-free periods
 */
class BannerAdManager(private val activity: Activity) : BannerView.IListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }

    private var bannerView: BannerView? = null
    private var bannerContainer: FrameLayout? = null
    private var retryAttempts = 0
    private var retryRunnable: Runnable? = null

    fun loadBanner(container: FrameLayout) {
        bannerContainer = container
        
        // Check if Unity Ads is ready
        if (!UnityAdsManager.isReady()) {
            Log.d(TAG, "Unity Ads not initialized yet - will retry")
            container.removeAllViews()
            scheduleRetry(container)
            return
        }
        
        // Check if user is in ad-free period
        if (UnityAdsManager.isAdFree()) {
            Log.d(TAG, "Ad-free period active - not loading banner")
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        Log.d(TAG, "Loading banner ad")
        
        // Remove old banner if exists
        bannerView?.destroy()
        container.removeAllViews()
        container.visibility = View.VISIBLE

        // Create new banner
        bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
        bannerView?.listener = this

        // Add to container
        container.addView(bannerView)

        // Load the banner
        bannerView?.load()
    }

    private fun scheduleRetry(container: FrameLayout) {
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            Log.d(TAG, "Max retry attempts reached, giving up")
            return
        }
        
        retryAttempts++
        retryRunnable = Runnable {
            if (!activity.isDestroyed && !activity.isFinishing) {
                loadBanner(container)
            }
        }
        container.postDelayed(retryRunnable, RETRY_DELAY_MS)
    }

    fun destroyBanner() {
        // Cancel any pending retries
        retryRunnable?.let { bannerContainer?.removeCallbacks(it) }
        retryRunnable = null
        
        bannerView?.destroy()
        bannerView = null
        bannerContainer?.removeAllViews()
        bannerContainer?.visibility = View.GONE
        bannerContainer = null
    }

    // BannerView.IListener callbacks
    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner loaded successfully")
        retryAttempts = 0 // Reset retry counter on success
        bannerAdView?.visibility = View.VISIBLE
        bannerContainer?.visibility = View.VISIBLE
    }

    override fun onBannerClick(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner clicked")
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "Banner failed to load: ${errorInfo?.errorMessage}")
        bannerContainer?.visibility = View.GONE
    }

    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner left application")
    }

    override fun onBannerShown(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner shown")
    }
}
