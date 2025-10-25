package com.statussaver

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * Manages bottom banner ads using Unity Ads 4.16.1 API
 * Follows official Unity documentation pattern
 * Creates banner once and reuses it instead of destroying/recreating
 */
class BannerAdManager(private val activity: Activity) : BannerView.IListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
    }

    private var bannerView: BannerView? = null
    private var currentContainer: FrameLayout? = null

    /**
     * Load and display banner ad in the specified container
     * Reuses existing banner if already created
     */
    fun loadBanner(container: FrameLayout) {
        Log.d(TAG, "=== loadBanner() called ===")

        // Check if Unity Ads is ready
        if (!UnityAdsManager.isReady()) {
            Log.w(TAG, "Unity Ads not initialized yet - cannot load banner")
            container.visibility = View.GONE
            return
        }

        // Store the container reference
        currentContainer = container

        // If banner already exists, just ensure it's in the right container
        if (bannerView != null) {
            Log.d(TAG, "Banner already exists - reusing it")

            // Remove from old container if it was in a different one
            (bannerView?.parent as? FrameLayout)?.removeView(bannerView)

            // Add to new container if not already there
            if (bannerView?.parent == null) {
                container.addView(bannerView)
                Log.d(TAG, "Banner moved to new container")
            }

            container.visibility = View.VISIBLE
            return
        }

        // Create new banner - Standard Mobile Banner 320x50
        Log.d(TAG, "Creating new banner with placement: $BANNER_AD_UNIT_ID")

        try {
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
            bannerView?.setListener(this)

            // Load the banner first
            Log.d(TAG, "Loading banner...")
            bannerView?.load()

            // Add to container after load() is called
            container.addView(bannerView)
            container.visibility = View.VISIBLE

            Log.d(TAG, "Banner created and added to container")

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating banner", e)
            container.visibility = View.GONE
        }
    }

    /**
     * Hide banner without destroying it (can be shown again)
     */
    fun hideBanner() {
        Log.d(TAG, "Hiding banner")
        currentContainer?.visibility = View.GONE
    }

    /**
     * Show banner if it exists and is loaded
     */
    fun showBanner() {
        Log.d(TAG, "Showing banner")
        if (bannerView != null && currentContainer != null) {
            currentContainer?.visibility = View.VISIBLE
        }
    }

    /**
     * Destroy banner - only call this when activity is being destroyed
     */
    fun destroyBanner() {
        Log.d(TAG, "Destroying banner")

        currentContainer?.removeAllViews()
        currentContainer?.visibility = View.GONE
        currentContainer = null

        bannerView?.destroy()
        bannerView = null
    }

    // ========== BannerView.IListener Callbacks ==========

    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "=== BANNER LOADED SUCCESSFULLY ===")
        Log.d(TAG, "Placement: ${bannerAdView?.placementId}")

        activity.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            currentContainer?.visibility = View.VISIBLE
        }
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "=== BANNER FAILED TO LOAD ===")
        Log.e(TAG, "Placement: ${bannerAdView?.placementId}")
        Log.e(TAG, "Error Code: ${errorInfo?.errorCode}")
        Log.e(TAG, "Error Message: ${errorInfo?.errorMessage}")

        activity.runOnUiThread {
            currentContainer?.visibility = View.GONE
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
