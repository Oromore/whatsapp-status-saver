package com.statussaver

import android.app.Activity
import android.util.Log
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/**
 * Yandex Interstitial Ad Manager (PURE YANDEX)
 * 
 * TEST MODE: Uses demo-interstitial-yandex (works worldwide)
 * PRODUCTION: Uses R-M-17685522-3 (your real ad unit)
 * 
 * Triggers:
 * 1. App interaction (90 second cooldown)
 * 2. Every 7 status saves (no cooldown)
 */
class InterstitialAdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "InterstitialAdManager"
        
        // Test ad unit (works anywhere in the world)
        private const val TEST_AD_UNIT_ID = "demo-interstitial-yandex"
        
        // Production ad unit (your real Yandex ad unit)
        private const val PROD_AD_UNIT_ID = "R-M-17685522-3"
        
        private const val SAVE_COUNT_KEY = "interstitial_save_count"
        private const val LAST_INTERACTION_AD_KEY = "last_interaction_ad_time"
        private const val COOLDOWN_SECONDS = 90
        private const val COOLDOWN_MS = COOLDOWN_SECONDS * 1000L
    }

    private val prefs = activity.getSharedPreferences("ad_prefs", Activity.MODE_PRIVATE)

    private var interstitialAdLoader: InterstitialAdLoader? = null
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoaded = false
    private var isLoadingAd = false
    private var isShowingAd = false

    // Get the appropriate ad unit ID based on test mode
    private val adUnitId: String
        get() = if (YandexAdsManager.TEST_MODE) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID

    init {
        Log.d(TAG, "=== InterstitialAdManager initialized ===")
        Log.d(TAG, "Ad Unit ID: $adUnitId")
        Log.d(TAG, "Test Mode: ${YandexAdsManager.TEST_MODE}")
        
        YandexAdsManager.onReady {
            Log.d(TAG, "Yandex ready - loading first interstitial")
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        if (!YandexAdsManager.isReady()) {
            Log.w(TAG, "Yandex not ready")
            return
        }
        
        if (isLoadingAd) {
            Log.d(TAG, "Already loading")
            return
        }

        isLoadingAd = true
        Log.d(TAG, "Loading interstitial: $adUnitId")

        val adRequestConfiguration = AdRequestConfiguration.Builder(adUnitId).build()

        interstitialAdLoader = InterstitialAdLoader(activity).apply {
            setAdLoadListener(object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "✓✓✓ INTERSTITIAL LOADED SUCCESSFULLY ✓✓✓")
                    interstitialAd = ad
                    isAdLoaded = true
                    isLoadingAd = false

                    ad.setAdEventListener(object : InterstitialAdEventListener {
                        override fun onAdShown() {
                            Log.d(TAG, "Interstitial shown")
                            isShowingAd = true
                        }

                        override fun onAdFailedToShow(adError: AdError) {
                            Log.e(TAG, "✗ Failed to show: ${adError.description}")
                            isAdLoaded = false
                            isShowingAd = false
                            interstitialAd = null
                            loadInterstitial()
                        }

                        override fun onAdDismissed() {
                            Log.d(TAG, "Interstitial dismissed")
                            isAdLoaded = false
                            isShowingAd = false
                            interstitialAd = null
                            loadInterstitial()
                        }

                        override fun onAdClicked() {
                            Log.d(TAG, "Interstitial clicked")
                        }

                        override fun onAdImpression(impressionData: ImpressionData?) {
                            Log.d(TAG, "Impression recorded")
                            impressionData?.let {
                                Log.d(TAG, "Impression data: ${it.rawData}")
                            }
                        }
                    })
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    Log.e(TAG, "✗✗✗ INTERSTITIAL FAILED TO LOAD ✗✗✗")
                    Log.e(TAG, "Error Code: ${error.code}")
                    Log.e(TAG, "Error Description: ${error.description}")
                    
                    isAdLoaded = false
                    isLoadingAd = false
                    interstitialAd = null
                    
                    // Retry after 5 seconds
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadInterstitial()
                    }, 5000)
                }
            })

            loadAd(adRequestConfiguration)
        }
    }

    /**
     * Track save - show ad every 7 saves (no cooldown)
     */
    fun trackSave() {
        if (!YandexAdsManager.isReady()) return

        var saveCount = prefs.getInt(SAVE_COUNT_KEY, 0)
        saveCount++
        prefs.edit().putInt(SAVE_COUNT_KEY, saveCount).apply()

        Log.d(TAG, "Save tracked: $saveCount/7")

        if (saveCount >= 7) {
            Log.d(TAG, "7 saves reached - showing interstitial")
            showInterstitial(updateCooldown = false)
            prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply()
        }
    }

    /**
     * Track app interaction - show ad with 90s cooldown
     */
    fun trackAppInteraction() {
        if (!YandexAdsManager.isReady()) return

        val lastAdTime = prefs.getLong(LAST_INTERACTION_AD_KEY, 0)
        val timeSinceLastAd = System.currentTimeMillis() - lastAdTime

        if (timeSinceLastAd < COOLDOWN_MS) {
            val secondsRemaining = ((COOLDOWN_MS - timeSinceLastAd) / 1000).toInt()
            Log.d(TAG, "On cooldown - ${secondsRemaining}s remaining")
            return
        }

        Log.d(TAG, "Cooldown expired - showing interstitial")
        showInterstitial(updateCooldown = true)
    }

    private fun showInterstitial(updateCooldown: Boolean) {
        if (isShowingAd) {
            Log.d(TAG, "Already showing an ad")
            return
        }
        
        if (!isAdLoaded || interstitialAd == null) {
            Log.d(TAG, "Ad not ready - isLoaded: $isAdLoaded")
            if (!isLoadingAd) {
                loadInterstitial()
            }
            return
        }

        Log.d(TAG, "Showing interstitial")

        if (updateCooldown) {
            prefs.edit()
                .putLong(LAST_INTERACTION_AD_KEY, System.currentTimeMillis())
                .apply()
        }

        try {
            interstitialAd?.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing interstitial", e)
            isAdLoaded = false
            isShowingAd = false
            interstitialAd = null
            loadInterstitial()
        }
    }

    fun resetSaveCount() {
        prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply()
    }
}
