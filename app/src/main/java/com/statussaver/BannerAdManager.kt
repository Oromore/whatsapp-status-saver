package com.statussaver

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * Singleton BannerAdManager - ONE banner shared across all activities
 * Banner persists and moves between screens for instant display
 */
object BannerAdManager : BannerView.IListener {

    private const val TAG = "BannerAdManager"
    private const val BANNER_AD_UNIT_ID = "Banner_Android"

    private var bannerView: BannerView? = null
    private var currentContainer: FrameLayout? = null
    private var currentActivity: Activity? = null

    /**
     * Load and display banner in the specified container
     * Reuses existing banner - just moves it to new container
     */
    fun loadBanner(activity: Activity, container: FrameLayout) {
        Log.d(TAG, "=== loadBanner() called for ${activity.javaClass.simpleName} ===")

        // Check if Unity Ads is ready
        if (!UnityAdsManager.isReady()) {
            Log.w(TAG, "Unity Ads not initialized yet - cannot load banner")
            container.visibility = View.GONE
            return
        }

        // Update current references
        currentActivity = activity
        currentContainer = container

        // If banner already exists, just move it to new container
        if (bannerView != null) {
            Log.d(TAG, "Banner exists - moving to new container")

            // Remove from old parent
            (bannerView?.parent as? FrameLayout)?.removeView(bannerView)

            // RE-REGISTER LISTENER (critical for maintaining connection!)
            bannerView?.setListener(this)

            // REFRESH/RELOAD (uses cached ad, displays instantly)
            bannerView?.load()

            // Add to new container
            container.addView(bannerView)
            container.visibility = View.VISIBLE

            Log.d(TAG, "Banner moved successfully with refreshed listener")
            return
        }

        // Create new banner (first time only)
        Log.d(TAG, "Creating new banner with placement: $BANNER_AD_UNIT_ID")

        try {
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
            bannerView?.setListener(this)

            // Load the banner
            Log.d(TAG, "Loading banner...")
            bannerView?.load()

            // Add to container
            container.addView(bannerView)
            container.visibility = View.VISIBLE

            Log.d(TAG, "Banner created and loading")

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating banner", e)
            container.visibility = View.GONE
        }
    }

    /**
     * Hide banner without removing it
     */
    fun hideBanner() {
        Log.d(TAG, "Hiding banner")
        currentContainer?.visibility = View.GONE
    }

    /**
     * Show banner if it exists
     */
    fun showBanner() {
        Log.d(TAG, "Showing banner")
        if (bannerView != null && currentContainer != null) {
            currentContainer?.visibility = View.VISIBLE
        }
    }

    /**
     * ONLY destroy when app is completely closing
     * DON'T call this in onDestroy() of activities
     */
    fun destroyBanner() {
        Log.d(TAG, "Destroying banner (should only happen on app close)")

        currentContainer?.removeAllViews()
        currentContainer?.visibility = View.GONE
        currentContainer = null
        currentActivity = null

        bannerView?.destroy()
        bannerView = null
    }

    // ========== BannerView.IListener Callbacks ==========

    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "=== BANNER LOADED SUCCESSFULLY ===")
        Log.d(TAG, "Placement: ${bannerAdView?.placementId}")

        currentActivity?.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            currentContainer?.visibility = View.VISIBLE
        }
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "=== BANNER FAILED TO LOAD ===")
        Log.e(TAG, "Placement: ${bannerAdView?.placementId}")
        Log.e(TAG, "Error Code: ${errorInfo?.errorCode}")
        Log.e(TAG, "Error Message: ${errorInfo?.errorMessage}")

        currentActivity?.runOnUiThread {
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
