package com.statussaver

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds

/**
 * Manages rewarded video ads
 * 90-minute ad-free period after watch
 * 30-hour cooldown before next reward can be earned
 */
class RewardedVideoManager(private val context: Context) {

    companion object {
        private const val TAG = "RewardedVideoManager"
        private const val REWARDED_AD_UNIT_ID = "Rewarded_Android"
        private const val LAST_REWARD_TIME_KEY = "last_reward_time"
        private const val REWARD_COOLDOWN_HOURS = 30
        private const val REWARD_COOLDOWN_MS = REWARD_COOLDOWN_HOURS * 60 * 60 * 1000L
        private const val AD_FREE_DURATION_MINUTES = 90L
    }

    private val unityAdsManager = UnityAdsManager(context)
    private val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)

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
    fun showRewardedVideo(activity: Activity) {
        if (!canWatchReward()) {
            val minutesLeft = getMinutesUntilNextReward()
            val hoursLeft = minutesLeft / 60
            Toast.makeText(
                context,
                "Reward available in $hoursLeft hours ${ minutesLeft % 60} minutes",
                Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "Reward on cooldown - $minutesLeft minutes remaining")
            return
        }

        if (!UnityAds.isReady(REWARDED_AD_UNIT_ID)) {
            Toast.makeText(context, "Rewarded video not ready", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Rewarded video ad not ready")
            return
        }

        Log.d(TAG, "Showing rewarded video ad")
        Toast.makeText(context, "Loading rewarded video...", Toast.LENGTH_SHORT).show()

        UnityAds.show(
            activity,
            REWARDED_AD_UNIT_ID,
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(adUnitId: String, error: UnityAds.UnityAdsShowError, message: String) {
                    Log.e(TAG, "Rewarded video show failed: $error - $message")
                    Toast.makeText(context, "Failed to load video", Toast.LENGTH_SHORT).show()
                }

                override fun onUnityAdsShowStart(adUnitId: String) {
                    Log.d(TAG, "Rewarded video show started")
                }

                override fun onUnityAdsShowClick(adUnitId: String) {
                    Log.d(TAG, "Rewarded video clicked")
                }

                override fun onUnityAdsShowComplete(adUnitId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    Log.d(TAG, "Rewarded video completed: $state")

                    // Check if video was watched completely
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        rewardUser()
                    } else {
                        Log.d(TAG, "Video skipped - no reward")
                        Toast.makeText(context, "Please watch the full video to get ad-free time", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun rewardUser() {
        // Activate ad-free period (90 minutes)
        unityAdsManager.activateAdFree(context, AD_FREE_DURATION_MINUTES)

        // Set cooldown for next reward
        prefs.edit().putLong(LAST_REWARD_TIME_KEY, System.currentTimeMillis()).apply()

        Log.d(TAG, "User rewarded: $AD_FREE_DURATION_MINUTES minutes ad-free")
        Toast.makeText(
            context,
            "Enjoy $AD_FREE_DURATION_MINUTES minutes without ads!",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Get time remaining in current ad-free period (in seconds)
     */
    fun getAdFreeTimeRemaining(): Long {
        return unityAdsManager.getTimeUntilAdFreeEnds(context)
    }
}
