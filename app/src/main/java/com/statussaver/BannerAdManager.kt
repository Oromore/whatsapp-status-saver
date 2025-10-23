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
    }

    private var bannerView: BannerView? = null
    private var bannerContainer: FrameLayout? = null
    private var isLoadingBanner = false

    fun loadBanner(container: FrameLayout) {
        Log.d(TAG, "=== LOAD BANNER CALLED ===")
        bannerContainer = container

        // Check if Unity Ads is ready
        if (!UnityAdsManager.isReady()) {
            Log.w(TAG, "Unity Ads not initialized yet - cannot load banner")
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        // Check if user is in ad-free period
        if (UnityAdsManager.isAdFree()) {
            Log.d(TAG, "Ad-free period active - hiding banner")
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        // Prevent multiple simultaneous loads
        if (isLoadingBanner) {
            Log.d(TAG, "Banner already loading - skipping")
            return
        }

        Log.d(TAG, "Loading banner ad with placement: $BANNER_AD_UNIT_ID")

        // Remove old banner if exists
        bannerView?.destroy()
        container.removeAllViews()
        container.visibility = View.VISIBLE

        isLoadingBanner = true

        try {
            // Create new banner - using standard size
            bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
            bannerView?.listener = this

            Log.d(TAG, "BannerView created, adding to container")
            
            // Add to container
            container.addView(bannerView)

            // Load the banner
            Log.d(TAG, "Calling banner.load()")
            bannerView?.load()
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating banner", e)
            isLoadingBanner = false
            container.visibility = View.GONE
        }
    }

    fun destroyBanner() {
        Log.d(TAG, "Destroying banner")
        bannerView?.destroy()
        bannerView = null
        bannerContainer?.removeAllViews()
        bannerContainer?.visibility = View.GONE
        bannerContainer = null
        isLoadingBanner = false
    }

    // BannerView.IListener callbacks
    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "=== BANNER LOADED SUCCESSFULLY ===")
        isLoadingBanner = false
        bannerAdView?.visibility = View.VISIBLE
        bannerContainer?.visibility = View.VISIBLE
    }

    override fun onBannerClick(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner clicked")
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "=== BANNER FAILED TO LOAD ===")
        Log.e(TAG, "Error Code: ${errorInfo?.errorCode}")
        Log.e(TAG, "Error Message: ${errorInfo?.errorMessage}")
        isLoadingBanner = false
        bannerContainer?.visibility = View.GONE
    }

    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner left application")
    }

    override fun onBannerShown(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner shown")
    }
}
