package com.statussaver

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds

/**
 * Manages rewarded video ads using Unity Ads 4.x API
 * 90-minute ad-free period after watch
 * 30-hour cooldown before next reward can be earned
 */
class RewardedVideoManager(private val activity: Activity) {

    companion object {
        private const val TAG = "RewardedVideoManager"
        private const val REWARDED_AD_UNIT_ID = "Rewarded_Android"
        private const val LAST_REWARD_TIME_KEY = "last_reward_time"
        private const val REWARD_COOLDOWN_HOURS = 30
        private const val REWARD_COOLDOWN_MS = REWARD_COOLDOWN_HOURS * 60 * 60 * 1000L
        private const val AD_FREE_DURATION_MINUTES = 90L
    }

    private val prefs = activity.getSharedPreferences("ad_prefs", Activity.MODE_PRIVATE)
    private var isAdLoaded = false
    private var isLoadingAd = false

    init {
        // Load rewarded video when Unity Ads is ready
        if (UnityAdsManager.isReady()) {
            loadRewardedVideo()
        }
    }

    private fun loadRewardedVideo() {
        if (!UnityAdsManager.isReady() || isLoadingAd) {
            return
        }

        isLoadingAd = true
        Log.d(TAG, "Loading rewarded video ad")

        UnityAds.load(REWARDED_AD_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                Log.d(TAG, "Rewarded video loaded: $placementId")
                isAdLoaded = true
                isLoadingAd = false
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAds.UnityAdsLoadError,
                message: String
            ) {
                Log.e(TAG, "Rewarded video failed to load: $error - $message")
                isAdLoaded = false
                isLoadingAd = false
            }
        })
    }

    /**
     * Check if user can watch rewarded video
     */
    fun canWatchReward(): Boolean {
        val lastRewardTime = prefs.getLong(LAST_REWARD_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastReward = currentTime - lastRewardTime

        return timeSinceLastReward >= REWARD_COOLDOWN_MS
    }

    /**
     * Get minutes until next reward is available
     */
    fun getMinutesUntilNextReward(): Long {
        val lastRewardTime = prefs.getLong(LAST_REWARD_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastReward = currentTime - lastRewardTime

        return if (timeSinceLastReward < REWARD_COOLDOWN_MS) {
            ((REWARD_COOLDOWN_MS - timeSinceLastReward) / 1000 / 60)
        } else {
            0
        }
    }

    /**
     * Show rewarded video ad
     */
    fun showRewardedVideo() {
        // Pre-flight checks
        if (!UnityAdsManager.isReady()) {
            Toast.makeText(activity, "Ads not ready yet, please try again", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Unity Ads not ready yet")
            return
        }

        if (!canWatchReward()) {
            val minutesLeft = getMinutesUntilNextReward()
            val hoursLeft = minutesLeft / 60
            Toast.makeText(
                activity,
                "Reward available in $hoursLeft hours ${minutesLeft % 60} minutes",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Reward on cooldown - $minutesLeft minutes remaining")
            return
        }

        if (!isAdLoaded) {
            Toast.makeText(activity, "Rewarded video not ready, loading...", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Rewarded video ad not loaded yet")
            if (!isLoadingAd) {
                loadRewardedVideo() // Try loading
            }
            return
        }

        // All checks passed - show the ad
        Log.d(TAG, "Showing rewarded video ad")

        UnityAds.show(
            activity,
            REWARDED_AD_UNIT_ID,
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    Log.e(TAG, "Rewarded video show failed: $error - $message")
                    Toast.makeText(activity, "Failed to load video", Toast.LENGTH_SHORT).show()
                    isAdLoaded = false
                    loadRewardedVideo() // Reload for next time
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    Log.d(TAG, "Rewarded video show started")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    Log.d(TAG, "Rewarded video clicked")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    Log.d(TAG, "Rewarded video completed: $state")

                    // Check if video was watched completely
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        rewardUser()
                    } else {
                        Log.d(TAG, "Video skipped - no reward")
                        Toast.makeText(
                            activity,
                            "Please watch the full video to get ad-free time",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Reload ad for next time
                    isAdLoaded = false
                    loadRewardedVideo()
                }
            }
        )
    }

    private fun rewardUser() {
        // Activate ad-free period (90 minutes)
        UnityAdsManager.activateAdFree(AD_FREE_DURATION_MINUTES)

        // Set cooldown for next reward
        prefs.edit().putLong(LAST_REWARD_TIME_KEY, System.currentTimeMillis()).apply()

        Log.d(TAG, "User rewarded: $AD_FREE_DURATION_MINUTES minutes ad-free")
        Toast.makeText(
            activity,
            "Enjoy $AD_FREE_DURATION_MINUTES minutes without ads!",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Get time remaining in current ad-free period (in seconds)
     */
    fun getAdFreeTimeRemaining(): Long {
        return UnityAdsManager.getTimeUntilAdFreeEnds()
    }
}
