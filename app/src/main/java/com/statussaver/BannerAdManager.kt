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
 * Yandex Banner Ad Manager
 * Ad Unit ID: R-M-17685522-2
 *
 * Permanent bottom banner with health check
 */
class BannerAdManager(private val activity: Activity) : BannerAdEventListener {

    companion object {
        private const val TAG = "BannerAdManager"
        private const val AD_UNIT_ID = "R-M-17685522-2"
        private const val RETRY_DELAY_MS = 2000L
        private const val HEALTH_CHECK_INTERVAL_MS = 5000L
    }

    private var bannerAdView: BannerAdView? = null
    private var container: FrameLayout? = null
    private var isLoaded = false
    private var isDestroyed = false

    private val retryHandler = Handler(Looper.getMainLooper())
    private val healthCheckHandler = Handler(Looper.getMainLooper())

    fun loadBanner(adContainer: FrameLayout) {
        Log.d(TAG, "=== LOADING BANNER ===")

        if (isDestroyed) return

        container = adContainer
        container?.visibility = View.VISIBLE

        if (bannerAdView != null && isLoaded) {
            Log.d(TAG, "Banner already active")
            return
        }

        YandexAdsManager.onReady {
            activity.runOnUiThread { createBanner() }
        }
    }

    private fun createBanner() {
        Log.d(TAG, "Creating banner")

        if (isDestroyed) return

        try {
            bannerAdView?.destroy()
            container?.removeAllViews()

            bannerAdView = BannerAdView(activity).apply {
                setAdUnitId(AD_UNIT_ID)
                setAdSize(BannerAdSize.stickySize(320)) // 320x50 sticky banner
                setBannerAdEventListener(this@BannerAdManager)
            }

            container?.addView(bannerAdView)

            val adRequest = AdRequest.Builder().build()
            bannerAdView?.loadAd(adRequest)
            isLoaded = false

            Log.d(TAG, "Banner loadAd() called")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating banner", e)
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (isDestroyed) return

        Log.d(TAG, "Scheduling retry in ${RETRY_DELAY_MS}ms")
        retryHandler.postDelayed({ createBanner() }, RETRY_DELAY_MS)
    }

    private fun startHealthCheck() {
        Log.d(TAG, "Starting health check")

        healthCheckHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isDestroyed) return

                if (bannerAdView == null || bannerAdView?.parent == null) {
                    Log.w(TAG, "Health check failed - reloading")
                    isLoaded = false
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
        Log.d(TAG, "✓ Banner loaded")
        isLoaded = true

        activity.runOnUiThread {
            bannerAdView?.visibility = View.VISIBLE
            container?.visibility = View.VISIBLE
        }

        startHealthCheck()
    }

    override fun onAdFailedToLoad(error: AdRequestError) {
        Log.e(TAG, "✗ Banner failed: ${error.description}")
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
    }
}
