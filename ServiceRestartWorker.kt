package com.iran.liberty.vpn

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Worker to restart the VPN service if it gets killed.
 * Runs periodically to ensure service stays alive.
 */
class ServiceRestartWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            // Check if service is running (simplified check)
            // In real implementation, you'd check if the service is actually running
            
            // Random delay to avoid pattern detection
            delay(Random.nextLong(5000, 15000))
            
            // Restart service
            val intent = Intent(applicationContext, FakeVpnService::class.java).apply {
                action = FakeVpnService.Actions.ACTION_CONNECT
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            
            Result.success()
        } catch (e: Exception) {
            Result.retry() // Retry if failed
        }
    }
    
    companion object {
        const val WORK_NAME = "vpn_service_restart_worker"
    }
}