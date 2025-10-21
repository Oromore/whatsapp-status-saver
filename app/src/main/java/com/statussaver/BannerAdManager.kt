package com.statussaver

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize

/**
 * Manages bottom banner ads using Unity Ads 4.x API
 * Unity handles refresh automatically via dashboard settings (20 seconds)
 * Hides during ad-free periods
 */
class BannerAdManager(private val activity: Activity) : BannerView.IListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
    }

    private var bannerView: BannerView? = null
    private var bannerContainer: FrameLayout? = null
    private val unityAdsManager = UnityAdsManager(activity)

    fun loadBanner(container: FrameLayout) {
        bannerContainer = container

        // Check if user is in ad-free period
        if (unityAdsManager.isAdFree(activity)) {
            Log.d(TAG, "Ad-free period active - not loading banner")
            container.removeAllViews()
            return
        }

        Log.d(TAG, "Loading banner ad")

        // Remove old banner if exists
        bannerView?.destroy()
        container.removeAllViews()

        // Create new banner - Unity Ads requires Activity, not Context
        bannerView = BannerView(activity, BANNER_AD_UNIT_ID, UnityBannerSize(320, 50))
        bannerView?.listener = this

        // Add to container
        container.addView(bannerView)

        // Load the banner
        bannerView?.load()
    }

    fun destroyBanner() {
        bannerView?.destroy()
        bannerView = null
        bannerContainer?.removeAllViews()
    }

    // BannerView.IListener callbacks
    override fun onBannerLoaded(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner loaded")
        bannerAdView?.visibility = View.VISIBLE
    }

    override fun onBannerClick(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner clicked")
    }

    override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
        Log.e(TAG, "Banner failed to load: ${errorInfo?.errorMessage}")
    }

    override fun onBannerLeftApplication(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner left application")
    }

    override fun onBannerShown(bannerAdView: BannerView?) {
        Log.d(TAG, "Banner shown")
    }
}
