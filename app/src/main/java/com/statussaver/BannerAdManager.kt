package com.statussaver

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData

/**
 * Yandex Banner Ad Manager (PURE YANDEX)
 * 
 * TEST MODE: Uses demo-banner-yandex (works worldwide)
 * PRODUCTION: Uses R-M-17685522-2 (your real ad unit)
 */
class BannerAdManager(private val activity: Activity) : BannerAdEventListener {

    companion object {
        private const val TAG = "BannerAdManager"
        
        // Test ad unit (works anywhere in the world)
        private const val TEST_AD_UNIT_ID = "demo-banner-yandex"
        
        // Production ad unit (your real Yandex ad unit)
        private const val PROD_AD_UNIT_ID = "R-M-17685522-2"
        
        private const val RETRY_DELAY_MS = 3000L
        private const val HEALTH_CHECK_INTERVAL_MS = 10000L
        private const val MAX_RETRIES = 5
    }

    private var bannerAdView: BannerAdView? = null
    private var container: FrameLayout? = null
    private var isLoaded = false
    private var isDestroyed = false
    private var retryCount = 0

    private val retryHandler = Handler(Looper.getMainLooper())
    private val healthCheckHandler = Handler(Looper.getMainLooper())

    // Get the appropriate ad unit ID based on test mode
    private val adUnitId: String
        get() = if (YandexAdsManager.TEST_MODE) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID

    fun loadBanner(adContainer: FrameLayout) {
        Log.d(TAG, "=== LOADING BANNER ===")
        Log.d(TAG, "Ad Unit ID: $adUnitId")
        Log.d(TAG, "Test Mode: ${YandexAdsManager.TEST_MODE}")

        if (isDestroyed) return

        container = adContainer
        container?.visibility = View.VISIBLE

        if (bannerAdView != null && isLoaded) {
            Log.d(TAG, "Banner already active and loaded")
            return
        }

        YandexAdsManager.onReady {
            activity.runOnUiThread { createBanner() }
        }
    }

    private fun createBanner() {
        Log.d(TAG, "Creating banner (attempt ${retryCount + 1}/$MAX_RETRIES)")

        if (isDestroyed) return

        try {
            // Cleanup old banner
            bannerAdView?.destroy()
            container?.removeAllViews()

            // Calculate banner width in dp
            val displayMetrics = activity.resources.displayMetrics
            val screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            
            Log.d(TAG, "Screen width: ${screenWidthDp}dp")

            // Create banner
            bannerAdView = BannerAdView(activity).apply {
                setAdUnitId(adUnitId)
                setAdSize(BannerAdSize.stickySize(activity, screenWidthDp))
                setBannerAdEventListener(this@BannerAdManager)
            }

            // Add to container
            container?.addView(bannerAdView)

            // Load ad
            val adRequest = AdRequest.Builder().build()
            
            Log.d(TAG, "Calling loadAd()...")
            bannerAdView?.loadAd(adRequest)
            
            isLoaded = false

        } catch (e: Exception) {
            Log.e(TAG, "Error creating banner", e)
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (isDestroyed) return
        
        retryCount++
        
        if (retryCount >= MAX_RETRIES) {
            Log.e(TAG, "Max retries reached - banner loading failed")
            container?.visibility = View.GONE
            return
        }

        Log.d(TAG, "Scheduling retry ${retryCount + 1}/$MAX_RETRIES in ${RETRY_DELAY_MS}ms")
        retryHandler.postDelayed({ 
            createBanner() 
        }, RETRY_DELAY_MS)
    }

    private fun startHealthCheck() {
        Log.d(TAG, "Starting health check")
        
        healthCheckHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isDestroyed) return

                if (bannerAdView == null || bannerAdView?.parent == null) {
                    Log.w(TAG, "Health check failed - reloading")
                    isLoaded = false
                    retryCount = 0
                    createBanner()
                } else {
                    activity.runOnUiThread {
                        bannerAdView?.visibility = View.VISIBLE
                        container?.visibility = View.VISIBLE
                    }
                }

                healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS)
            }
        }, HEALTH_CHECK_INTERVAL_MS)
    }

    fun destroy() {
        Log.d(TAG, "Destroying banner")
        isDestroyed = true
        isLoaded = false

        retryHandler.removeCallbacksAndMessages(null)
        healthCheckHandler.removeCallbacksAndMessages(null)

        container?.removeAllViews()
        container?.visibility = View.GONE
        container = null

        bannerAdView?.destroy()
        bannerAdView = null
    }

    // ========== BannerAdEventListener ==========
    override fun onAdLoaded() {
        Log.d(TAG, "✓✓✓ BANNER LOADED SUCCESSFULLY ✓✓✓")
        isLoaded = true
        retryCount = 0

        activity.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            container?.visibility = View.VISIBLE
        }

        startHealthCheck()
    }

    override fun onAdFailedToLoad(error: AdRequestError) {
        Log.e(TAG, "✗✗✗ BANNER FAILED TO LOAD ✗✗✗")
        Log.e(TAG, "Error Code: ${error.code}")
        Log.e(TAG, "Error Description: ${error.description}")
        
        isLoaded = false
        scheduleRetry()
    }

    override fun onAdClicked() {
        Log.d(TAG, "Banner clicked")
    }

    override fun onLeftApplication() {
        Log.d(TAG, "Left application")
    }

    override fun onReturnedToApplication() {
        Log.d(TAG, "Returned to application")
    }

    override fun onImpression(impressionData: ImpressionData?) {
        Log.d(TAG, "Banner impression")
        impressionData?.let {
            Log.d(TAG, "Impression data: ${it.rawData}")
        }
    }
}
