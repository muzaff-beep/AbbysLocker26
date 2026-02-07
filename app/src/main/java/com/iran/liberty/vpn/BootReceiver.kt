package com.iran.liberty.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Boot completed receiver to restart services after reboot
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.i(TAG, "Device boot completed, restarting services")
                restartServices(context)
            }
            Intent.ACTION_REBOOT -> {
                Log.i(TAG, "Device reboot detected")
            }
            Intent.ACTION_SHUTDOWN -> {
                Log.i(TAG, "Device shutdown detected, saving state")
            }
        }
    }

    /**
     * Restart all necessary services after boot
     */
    private fun restartServices(context: Context) {
        try {
            // Start foreground service if it's not running
            val serviceIntent = Intent(context, FakeVpnService::class.java)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            Log.d(TAG, "Foreground service restart attempted")
            
            // Check and request battery optimization exemption
            requestBatteryOptimizationExemption(context)
            
            // Check if accessibility service needs re-enabling
            checkAccessibilityService(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting services after boot", e)
        }
    }

    /**
     * Request exemption from battery optimizations
     */
    private fun requestBatteryOptimizationExemption(context: Context) {
        try {
            val packageName = context.packageName
            val intent = Intent()
            
            when {
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                    intent.action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    intent.data = android.net.Uri.parse("package:$packageName")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP -> {
                    // Alternative method for Lollipop
                    intent.action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
            
            Log.d(TAG, "Battery optimization exemption requested")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting battery optimization exemption", e)
        }
    }

    /**
     * Check and prompt for accessibility service if disabled
     */
    private fun checkAccessibilityService(context: Context) {
        try {
            // Check if accessibility service is enabled
            val accessibilityEnabled = isAccessibilityServiceEnabled(context)
            
            if (!accessibilityEnabled) {
                // Use overlay or notification to prompt user
                // This would be implemented with OverlayHelper
                Log.w(TAG, "Accessibility service not enabled after boot")
                
                // Could show a notification prompting user to enable
                showAccessibilityPromptNotification(context)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
        }
    }

    /**
     * Check if our accessibility service is enabled
     */
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/${LibertyAccess::class.java.name}"
        val accessibilityEnabled = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        return accessibilityEnabled?.contains(serviceName) == true
    }

    /**
     * Show notification prompting user to enable accessibility
     */
    private fun showAccessibilityPromptNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) 
                as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channelId = "accessibility_prompt"
                val channelName = "Service Required"
                val importance = android.app.NotificationManager.IMPORTANCE_HIGH
                
                val channel = android.app.NotificationChannel(
                    channelId, channelName, importance
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or 
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = android.app.Notification.Builder(context)
                .setContentTitle("Iran Liberty VPN Service Required")
                .setContentText("Tap to enable accessibility service for VPN protection")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                notification.setChannelId("accessibility_prompt")
            }
            
            notificationManager.notify(1001, notification.build())
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing accessibility prompt notification", e)
        }
    }
}
