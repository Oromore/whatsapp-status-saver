package com.statussaver

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * In-app log viewer for debugging Yandex native ads
 * Shows filtered logs related to ads and MediaAdapter
 */
object DebugLogViewer {
    
    private const val TAG = "DebugLogViewer"
    
    /**
     * Show filtered logs in a dialog
     * Filters for: YandexAds, MediaAdapter, StatusSaverApp
     */
    fun showLogs(context: Context) {
        try {
            val logs = getFilteredLogs()
            
            val dialog = Dialog(context)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            val scrollView = ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(32, 32, 32, 32)
                setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
            
            val textView = TextView(context).apply {
                text = logs
                textSize = 10f
                setTextColor(Color.BLACK)
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.WHITE)
            }
            
            scrollView.addView(textView)
            dialog.setContentView(scrollView)
            
            dialog.window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
                (context.resources.displayMetrics.heightPixels * 0.8).toInt()
            )
            
            dialog.show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing logs", e)
        }
    }
    
    /**
     * Get filtered logcat output
     */
    private fun getFilteredLogs(): String {
        val stringBuilder = StringBuilder()
        
        try {
            // Clear logcat first, then get fresh logs
            Runtime.getRuntime().exec("logcat -c")
            Thread.sleep(100)
            
            // Get logs filtered by our tags
            val process = Runtime.getRuntime().exec(
                "logcat -d -v time *:S " +
                "YandexAds:* " +
                "MediaAdapter:* " +
                "StatusSaverApp:* " +
                "YandexAdsManager:* " +
                "MediaListFragment:*"
            )
            
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            
            var line: String?
            var lineCount = 0
            val maxLines = 200 // Limit to last 200 lines
            
            while (bufferedReader.readLine().also { line = it } != null && lineCount < maxLines) {
                stringBuilder.append(line).append("\n")
                lineCount++
            }
            
            if (stringBuilder.isEmpty()) {
                stringBuilder.append("=== NO LOGS FOUND ===\n\n")
                stringBuilder.append("This could mean:\n")
                stringBuilder.append("1. Yandex Ads not initialized\n")
                stringBuilder.append("2. Native ads not being loaded\n")
                stringBuilder.append("3. No errors occurred\n\n")
                stringBuilder.append("Check if:\n")
                stringBuilder.append("- You clicked into Images/Videos?\n")
                stringBuilder.append("- Yandex initialized in StatusSaverApp?\n")
                stringBuilder.append("- Network connection available?\n")
            }
            
        } catch (e: Exception) {
            stringBuilder.append("ERROR GETTING LOGS:\n")
            stringBuilder.append(e.message)
            stringBuilder.append("\n\n")
            stringBuilder.append("Note: READ_LOGS permission may be needed\n")
            Log.e(TAG, "Error reading logs", e)
        }
        
        return stringBuilder.toString()
    }
    
    /**
     * Alternative: Get logs from memory buffer (if logcat fails)
     */
    fun showMemoryLogs(context: Context) {
        val logs = buildString {
            append("=== YANDEX ADS DEBUG INFO ===\n\n")
            
            append("Yandex Ready: ${YandexAdsManager.isReady()}\n")
            append("Test Mode: ${YandexAdsManager.TEST_MODE}\n\n")
            
            append("If Yandex is ready but ads not showing:\n")
            append("1. Check MediaAdapter logs for 'Loading native ad'\n")
            append("2. Look for 'NATIVE AD LOADED' or 'NATIVE AD FAILED'\n")
            append("3. Check for 'Error binding native ad'\n\n")
            
            append("Expected flow:\n")
            append("1. StatusSaverApp: Yandex initialization started\n")
            append("2. YandexAdsManager: YANDEX ADS INITIALIZED SUCCESSFULLY\n")
            append("3. MediaAdapter: Loading native ad for position X\n")
            append("4. MediaAdapter: ✓✓✓ NATIVE AD LOADED for position X\n")
            append("5. MediaAdapter: ✓ Native ad bound successfully\n\n")
            
            append("Try using 'adb logcat' from PC for full logs\n")
        }
        
        val dialog = Dialog(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        val textView = TextView(context).apply {
            text = logs
            textSize = 12f
            setTextColor(Color.BLACK)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }
        
        scrollView.addView(textView)
        dialog.setContentView(scrollView)
        
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.7).toInt()
        )
        
        dialog.show()
    }
}
