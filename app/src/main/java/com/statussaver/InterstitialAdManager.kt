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
 * Yandex Interstitial Ad Manager
 * Ad Unit ID: R-M-17685522-3
 * 
 * Triggers:
 * 1. App interaction (90 second cooldown)
 * 2. Every 7 status saves (no cooldown)
 */
class InterstitialAdManager(private val activity: Activity) {

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val AD_UNIT_ID = "R-M-17685522-3"
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

    init {
        YandexAdsManager.onReady {
            loadInterstitial()
        }
    }

    private fun loadInterstitial() {
        if (!YandexAdsManager.isReady() || isLoadingAd) return

        isLoadingAd = true
        Log.d(TAG, "Loading interstitial: $AD_UNIT_ID")

        val adRequestConfiguration = AdRequestConfiguration.Builder(AD_UNIT_ID).build()

        interstitialAdLoader = InterstitialAdLoader(activity).apply {
            setAdLoadListener(object : InterstitialAdLoadListener {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "✓ Interstitial loaded")
                    interstitialAd = ad
                    isAdLoaded = true
                    isLoadingAd = false

                    ad.setAdEventListener(object : InterstitialAdEventListener {
                        override fun onAdShown() {
                            Log.d(TAG, "Interstitial shown")
                        }

                        override fun onAdFailedToShow(adError: AdError) {
                            Log.e(TAG, "Failed to show: ${adError.description}")
                            isAdLoaded = false
                            interstitialAd = null
                            loadInterstitial()
                        }

                        override fun onAdDismissed() {
                            Log.d(TAG, "Interstitial dismissed")
                            isAdLoaded = false
                            interstitialAd = null
                            loadInterstitial()
                        }

                        override fun onAdClicked() {
                            Log.d(TAG, "Interstitial clicked")
                        }

                        override fun onAdImpression(impressionData: ImpressionData?) {
                            Log.d(TAG, "Impression recorded")
                        }
                    })
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    Log.e(TAG, "✗ Failed to load: ${error.description}")
                    isAdLoaded = false
                    isLoadingAd = false
                    interstitialAd = null
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

        showInterstitial(updateCooldown = true)
    }

    private fun showInterstitial(updateCooldown: Boolean) {
        if (!isAdLoaded || interstitialAd == null) {
            Log.d(TAG, "Ad not ready")
            if (!isLoadingAd) loadInterstitial()
            return
        }

        Log.d(TAG, "Showing interstitial (cooldown: $updateCooldown)")

        if (updateCooldown) {
            prefs.edit()
                .putLong(LAST_INTERACTION_AD_KEY, System.currentTimeMillis())
                .apply()
        }

        interstitialAd?.show(activity)
    }

    fun resetSaveCount() {
        prefs.edit().putInt(SAVE_COUNT_KEY, 0).apply()
    }
}
