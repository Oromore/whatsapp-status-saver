package com.statussaver

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.statussaver.databinding.ActivitySplashBinding

/**
 * Splash screen that displays while Unity Ads initializes
 * Shows app icon with fade-in animation, then navigates to MainActivity
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SplashActivity"
        private const val SPLASH_DISPLAY_TIME = 2500L // 2.5 seconds
        private const val MIN_DISPLAY_TIME = 1500L // Minimum 1.5 seconds even if ads ready
    }

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startTime = System.currentTimeMillis()
        Log.d(TAG, "=== Splash Screen Started ===")

        // Start fade-in animation for logo
        animateLogo()

        // Wait for Unity Ads initialization
        UnityAdsManager.onReady {
            Log.d(TAG, "Unity Ads ready in splash screen")
            navigateToMainWithDelay()
        }

        // Fallback: Navigate after max time even if Unity Ads not ready
        handler.postDelayed({
            if (!hasNavigated) {
                Log.d(TAG, "Max splash time reached - navigating anyway")
                navigateToMain()
            }
        }, SPLASH_DISPLAY_TIME)
    }

    private fun animateLogo() {
        // Start invisible
        binding.appIcon.alpha = 0f
        binding.appName.alpha = 0f

        // Fade in logo
        ObjectAnimator.ofFloat(binding.appIcon, View.ALPHA, 0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            start()
        }

        // Fade in app name slightly delayed
        handler.postDelayed({
            ObjectAnimator.ofFloat(binding.appName, View.ALPHA, 0f, 1f).apply {
                duration = 600
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 300)
    }

    private fun navigateToMainWithDelay() {
        val elapsedTime = System.currentTimeMillis() - startTime
        val remainingTime = MIN_DISPLAY_TIME - elapsedTime

        if (remainingTime > 0) {
            // Wait for minimum display time
            Log.d(TAG, "Waiting ${remainingTime}ms for minimum display time")
            handler.postDelayed({
                navigateToMain()
            }, remainingTime)
        } else {
            // Minimum time already passed, navigate now
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        if (hasNavigated) return
        hasNavigated = true

        Log.d(TAG, "Navigating to MainActivity")

        // Fade out and navigate
        ObjectAnimator.ofFloat(binding.root, View.ALPHA, 1f, 0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            start()
        }

        handler.postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            // Smooth transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 300)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
