package com.iran.liberty.vpn

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Helper class to manage silent audio playback for Doze resistance.
 */
class AudioHelper(private val context: Context) {
    
    private var exoPlayer: ExoPlayer? = null
    private var audioJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * Initializes and starts silent audio playback.
     */
    fun startSilentAudio() {
        if (exoPlayer != null && exoPlayer?.isPlaying == true) {
            return // Already playing
        }
        
        try {
            // Create and configure ExoPlayer
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                // Configure for silent playback
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(audioAttributes, false)
                volume = 0f // Completely silent
                repeatMode = Player.REPEAT_MODE_ALL // Loop indefinitely
                
                // Set media item (silent audio from raw resources)
                val mediaItem = MediaItem.fromUri(
                    Uri.parse("android.resource://${context.packageName}/raw/${Constants.SILENT_AUDIO_FILE_NAME}")
                )
                setMediaItem(mediaItem)
                
                // Add error listener
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Restart audio if it fails
                        restartSilentAudio()
                    }
                })
                
                // Prepare and play
                prepare()
                play()
            }
            
            // Monitor and restart audio periodically to ensure it stays alive
            startAudioMonitoring()
            
        } catch (e: Exception) {
            // Audio playback failed, but continue without it
            // Service will still run in foreground
        }
    }
    
    /**
     * Stops silent audio playback.
     */
    fun stopSilentAudio() {
        audioJob?.cancel()
        audioJob = null
        
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            player.release()
        }
        exoPlayer = null
    }
    
    /**
     * Monitors audio playback and restarts if needed.
     */
    private fun startAudioMonitoring() {
        audioJob?.cancel()
        audioJob = scope.launch {
            while (true) {
                delay(Constants.SILENT_AUDIO_LOOP_DURATION_MS)
                
                // Check if audio is still playing
                if (exoPlayer?.isPlaying != true) {
                    restartSilentAudio()
                }
            }
        }
    }
    
    /**
     * Restarts silent audio playback.
     */
    private fun restartSilentAudio() {
        stopSilentAudio()
        delay(1000) // Brief delay
        startSilentAudio()
    }
}
