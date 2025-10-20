package com.statussaver

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import com.unity3d.ads.UnityAds
import com.unity3d.ads.banner.BannerErrorInfo
import com.unity3d.ads.banner.IBannerListener

/**
 * Manages bottom banner ads
 * Unity handles refresh automatically via dashboard settings (20 seconds)
 * Hides during ad-free periods
 */
class BannerAdManager(private val context: Context) : IBannerListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val BANNER_AD_UNIT_ID = "Banner_Android"
    }

    private var bannerContainer: FrameLayout? = null
    private val unityAdsManager = UnityAdsManager(context)

    fun loadBanner(container: FrameLayout) {
        bannerContainer = container
        
        // Check if user is in ad-free period
        if (unityAdsManager.isAdFree(context)) {
            Log.d(TAG, "Ad-free period active - not loading banner")
            container.removeAllViews()
            return
        }

        Log.d(TAG, "Loading banner ad")
        
        UnityAds.setBannerListener(this)
        UnityAds.loadBanner(context, BANNER_AD_UNIT_ID)
    }

    fun destroyBanner() {
        bannerContainer?.removeAllViews()
        UnityAds.destroy(BANNER_AD_UNIT_ID)
    }

    // IBannerListener callbacks
    override fun onBannerLoaded(adUnitId: String?) {
        Log.d(TAG, "Banner loaded: $adUnitId")
    }

    override fun onBannerShown(adUnitId: String?) {
        Log.d(TAG, "Banner shown: $adUnitId")
    }

    override fun onBannerClick(adUnitId: String?) {
        Log.d(TAG, "Banner clicked: $adUnitId")
    }

    override fun onBannerFailedToLoad(adUnitId: String?, error: BannerErrorInfo?) {
        Log.e(TAG, "Banner failed to load: $adUnitId - ${error?.errorMessage}")
    }

    override fun onBannerLeftApplication(adUnitId: String?) {
        Log.d(TAG, "Banner left application: $adUnitId")
    }
}
