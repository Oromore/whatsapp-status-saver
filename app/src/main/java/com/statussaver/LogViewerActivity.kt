package com.statussaver

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * In-app log viewer - shows real-time Logcat filtered for ads
 * Tap bottom-right corner 5 times to open this from MainActivity
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private val logBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create simple layout programmatically
        scrollView = ScrollView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(16, 16, 16, 16)
        }

        logTextView = TextView(this).apply {
            textSize = 10f
            setTextColor(android.graphics.Color.GREEN)
            typeface = android.graphics.Typeface.MONOSPACE
            text = "📱 Yandex Ads Live Logs\n" +
                   "Filtering: YandexAds, BannerAd, InterstitialAd, NativeAd, MediaAdapter\n" +
                   "═══════════════════════════════════════\n\n"
        }

        scrollView.addView(logTextView)
        setContentView(scrollView)

        // Start reading logs
        startLogCapture()
    }

    private fun startLogCapture() {
        isRunning = true

        Thread {
            try {
                // Clear logcat first
                Runtime.getRuntime().exec("logcat -c")
                Thread.sleep(500)

                // Start capturing filtered logs
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "time",
                        "*:S", // Suppress all
                        "YandexAds:V",
                        "YandexAdsManager:V",
                        "BannerAdManager:V",
                        "InterstitialAdManager:V",
                        "MediaAdapter:V"
                    )
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))

                while (isRunning) {
                    val line = reader.readLine() ?: break
                    
                    line.let { logLine ->
                        runOnUiThread {
                            // Add to log
                            logBuilder.append(logLine).append("\n")
                            
                            // Keep only last 500 lines
                            val lines = logBuilder.toString().split("\n")
                            if (lines.size > 500) {
                                logBuilder.clear()
                                lines.takeLast(500).forEach {
                                    logBuilder.append(it).append("\n")
                                }
                            }

                            // Highlight important messages
                            val coloredLog = highlightLogs(logBuilder.toString())
                            logTextView.text = coloredLog

                            // Auto-scroll to bottom
                            handler.post {
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    logTextView.append("\n\n❌ ERROR: ${e.message}\n")
                    logTextView.append("Make sure app has READ_LOGS permission\n")
                }
            }
        }.start()
    }

    private fun highlightLogs(log: String): String {
        // Simple text highlighting (can't use HTML in this simple implementation)
        return log
            .replace("✓✓✓", "✅✅✅")
            .replace("✗✗✗", "❌❌❌")
            .replace("LOADED SUCCESSFULLY", "🎉 LOADED SUCCESSFULLY")
            .replace("FAILED", "⚠️ FAILED")
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
