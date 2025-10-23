package com.statussaver

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    // File logging for debugging
    private val logFile = File(activity.getExternalFilesDir(null), "unity_ads_debug.txt")

    private fun logToFile(message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            logFile.appendText("[$timestamp] [REWARDED] $message\n")
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log", e)
        }
    }

    fun loadRewardedVideo() {
        if (!UnityAdsManager.isReady()) {
            logToFile("ERROR: Unity Ads not ready - cannot load rewarded video")
            return
        }

        if (isLoadingAd) {
            logToFile("WARNING: Rewarded video already loading - skipping")
            return
        }

        isLoadingAd = true
        logToFile("=== LOADING REWARDED VIDEO ===")
        logToFile("Placement: $REWARDED_AD_UNIT_ID")

        UnityAds.load(REWARDED_AD_UNIT_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                logToFile("=== REWARDED VIDEO LOADED SUCCESSFULLY ===")
                logToFile("Placement: $placementId")
                isAdLoaded = true
                isLoadingAd = false
            }

            override fun onUnityAdsFailedToLoad(
                placementId: String,
                error: UnityAds.UnityAdsLoadError,
                message: String
            ) {
                logToFile("=== REWARDED VIDEO FAILED TO LOAD ===")
                logToFile("Placement: $placementId")
                logToFile("Error: $error")
                logToFile("Message: $message")
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

        val canWatch = timeSinceLastReward >= REWARD_COOLDOWN_MS
        
        if (!canWatch) {
            val minutesRemaining = ((REWARD_COOLDOWN_MS - timeSinceLastReward) / 1000 / 60)
            logToFile("Reward on cooldown - $minutesRemaining minutes remaining")
        }

        return canWatch
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
     * Check if rewarded video is ready to show
     */
    fun isRewardedVideoReady(): Boolean {
        return isAdLoaded && UnityAdsManager.isReady()
    }

    /**
     * Show rewarded video ad
     */
    fun showRewardedVideo() {
        logToFile("=== SHOW REWARDED VIDEO REQUESTED ===")

        // Pre-flight checks
        if (!UnityAdsManager.isReady()) {
            Toast.makeText(activity, "Ads not ready yet, please try again", Toast.LENGTH_SHORT).show()
            logToFile("ERROR: Unity Ads not ready yet")
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
            logToFile("Reward on cooldown - $minutesLeft minutes remaining")
            return
        }

        if (!isAdLoaded) {
            Toast.makeText(activity, "Rewarded video not ready, loading...", Toast.LENGTH_SHORT).show()
            logToFile("ERROR: Rewarded video ad not loaded yet")
            if (!isLoadingAd) {
                loadRewardedVideo() // Try loading
            }
            return
        }

        // All checks passed - show the ad
        logToFile("Showing rewarded video ad - Placement: $REWARDED_AD_UNIT_ID")

        UnityAds.show(
            activity,
            REWARDED_AD_UNIT_ID,
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String
                ) {
                    logToFile("=== REWARDED VIDEO SHOW FAILED ===")
                    logToFile("Placement: $placementId")
                    logToFile("Error: $error")
                    logToFile("Message: $message")
                    Toast.makeText(activity, "Failed to load video", Toast.LENGTH_SHORT).show()
                    isAdLoaded = false
                    loadRewardedVideo() // Reload for next time
                }

                override fun onUnityAdsShowStart(placementId: String) {
                    logToFile("Rewarded video show started - Placement: $placementId")
                }

                override fun onUnityAdsShowClick(placementId: String) {
                    logToFile("Rewarded video clicked - Placement: $placementId")
                }

                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState
                ) {
                    logToFile("=== REWARDED VIDEO COMPLETED ===")
                    logToFile("Placement: $placementId")
                    logToFile("State: $state")

                    // Check if video was watched completely
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        rewardUser()
                    } else {
                        logToFile("Video skipped - no reward")
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

        logToFile("=== USER REWARDED ===")
        logToFile("Ad-free duration: $AD_FREE_DURATION_MINUTES minutes")
        logToFile("Next reward available in: $REWARD_COOLDOWN_HOURS hours")
        
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
