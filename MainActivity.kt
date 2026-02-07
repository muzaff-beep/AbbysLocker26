package com.iran.liberty.vpn

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iran.liberty.vpn.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Main Activity for Iran Liberty VPN (Fake VPN UI)
 * Pathway 2 - Fake UI & VPN Disguise Developer
 * 
 * This class handles all fake VPN UI behavior without any real VPN functionality.
 * All server connections, stats, and speeds are completely fake.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding
    private lateinit var binding: ActivityMainBinding
    
    // UI Components
    private lateinit var connectButton: Button
    private lateinit var connectionStatus: TextView
    private lateinit var connectionSpinner: ProgressBar
    private lateinit var connectedInfoLayout: android.view.View
    private lateinit var connectedIp: TextView
    private lateinit var connectedServer: TextView
    private lateinit var downloadSpeed: TextView
    private lateinit var uploadSpeed: TextView
    private lateinit var dataToday: TextView
    private lateinit var dataTotal: TextView
    private lateinit var connectionTime: TextView
    private lateinit var protectedSince: TextView
    private lateinit var shareButton: Button
    private lateinit var serverRecyclerView: RecyclerView
    
    // State variables
    private var isConnected = false
    private var selectedServer: Server? = null
    private var connectionStartTime: Long = 0
    private var connectionTimerHandler = Handler(Looper.getMainLooper())
    private var speedUpdateHandler = Handler(Looper.getMainLooper())
    
    // Fake data generators
    private val random = Random()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    // Server list data (static, fake)
    private val serverList = listOf(
        Server("Tehran - Fast", "Iran", 28, 20, "185.143.223.41"),
        Server("Netherlands - Secure", "Netherlands", 45, 35, "87.238.192.103"),
        Server("USA - High Speed", "USA", 120, 60, "104.248.147.84"),
        Server("Germany - Private", "Germany", 52, 42, "95.179.148.227"),
        Server("UK - Premium", "United Kingdom", 58, 55, "144.76.162.99"),
        Server("Canada - Free", "Canada", 135, 70, "66.70.189.201"),
        Server("France - Secure", "France", 62, 48, "51.158.186.118"),
        Server("Japan - Fast", "Japan", 180, 75, "133.130.98.207"),
        Server("Switzerland - Private", "Switzerland", 75, 65, "185.215.227.76"),
        Server("Sweden - Uncensored", "Sweden", 68, 45, "185.200.118.193"),
        Server("Turkey - Free", "Turkey", 95, 80, "185.161.211.154"),
        Server("Australia - Secure", "Australia", 210, 85, "45.76.238.12")
    )
    
    // Data model for server
    data class Server(
        val name: String,
        val country: String,
        val ping: Int,
        val load: Int,
        val ip: String
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize UI components
        initializeViews()
        
        // Set up fake server list
        setupServerList()
        
        // Set initial stats
        initializeFakeStats()
        
        // Set up button listeners
        setupButtonListeners()
        
        // Set initial state
        updateConnectionState()
    }
    
    private fun initializeViews() {
        connectButton = binding.connectButton
        connectionStatus = binding.connectionStatus
        connectionSpinner = binding.connectionSpinner
        connectedInfoLayout = binding.connectedInfoLayout
        connectedIp = binding.connectedIp
        connectedServer = binding.connectedServer
        downloadSpeed = binding.downloadSpeed
        uploadSpeed = binding.uploadSpeed
        dataToday = binding.dataToday
        dataTotal = binding.dataTotal
        connectionTime = binding.connectionTime
        protectedSince = binding.protectedSince
        shareButton = binding.shareButton
        serverRecyclerView = binding.serverRecyclerView
    }
    
    private fun setupServerList() {
        // Set up RecyclerView for server list
        serverRecyclerView.layoutManager = LinearLayoutManager(this)
        serverRecyclerView.adapter = ServerAdapter(serverList) { server ->
            // Server selection callback
            selectedServer = server
            updateSelectedServerDisplay(server)
        }
        
        // Select first server by default
        if (serverList.isNotEmpty()) {
            selectedServer = serverList[0]
            updateSelectedServerDisplay(serverList[0])
        }
    }
    
    private fun updateSelectedServerDisplay(server: Server) {
        // Update the IP display with selected server's IP
        connectedIp.text = getString(R.string.ip_display, server.ip)
        connectedServer.text = getString(R.string.server_display, server.name)
    }
    
    private fun initializeFakeStats() {
        // Set initial fake data usage
        val todayMB = 124.7 + random.nextDouble() * 50
        val totalGB = 2.14 + random.nextDouble() * 2
        
        dataToday.text = String.format("%.1f MB", todayMB)
        dataTotal.text = String.format("%.2f GB", totalGB)
        
        // Set protected since date (random within last 30 days)
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -random.nextInt(30))
        protectedSince.text = dateFormat.format(calendar.time)
        
        // Initialize connection time
        connectionTime.text = "00:00:00"
    }
    
    private fun setupButtonListeners() {
        // Connect/Disconnect button
        connectButton.setOnClickListener {
            if (isConnected) {
                disconnectFromVpn()
            } else {
                connectToVpn()
            }
        }
        
        // Share button
        shareButton.setOnClickListener {
            shareAppWithFriends()
        }
    }
    
    /**
     * Fake VPN connection process
     */
    private fun connectToVpn() {
        isConnected = true
        connectionStartTime = System.currentTimeMillis()
        
        // Show connecting state
        connectionStatus.text = getString(R.string.status_connecting)
        connectionStatus.setTextColor(getColor(R.color.color_connecting))
        connectionSpinner.visibility = android.view.View.VISIBLE
        connectButton.isEnabled = false
        connectButton.text = getString(R.string.connecting)
        
        // Simulate connection delay (3-8 seconds)
        val connectionDelay = 3000 + random.nextInt(5000)
        
        Handler(Looper.getMainLooper()).postDelayed({
            // Connection "established"
            connectionSpinner.visibility = android.view.View.GONE
            connectedInfoLayout.visibility = android.view.View.VISIBLE
            connectionStatus.text = getString(R.string.status_connected)
            connectionStatus.setTextColor(getColor(R.color.color_connected))
            connectButton.text = getString(R.string.disconnect)
            connectButton.isEnabled = true
            
            // Start fake stats updates
            startFakeSpeedUpdates()
            startConnectionTimer()
            
            // Update notification (would be handled by FakeVpnService in Pathway 3)
            // For now, just log
            println("Iran Liberty VPN: Fake connection established to ${selectedServer?.name}")
            
        }, connectionDelay.toLong())
    }
    
    /**
     * Fake VPN disconnection process
     */
    private fun disconnectFromVpn() {
        // Show disconnecting state
        connectionStatus.text = getString(R.string.status_disconnecting)
        connectButton.isEnabled = false
        
        // Simulate disconnection delay (1-3 seconds)
        val disconnectDelay = 1000 + random.nextInt(2000)
        
        Handler(Looper.getMainLooper()).postDelayed({
            isConnected = false
            
            // Hide connected info
            connectedInfoLayout.visibility = android.view.View.GONE
            connectionStatus.text = getString(R.string.status_disconnected)
            connectionStatus.setTextColor(getColor(R.color.color_disconnected))
            connectButton.text = getString(R.string.connect)
            connectButton.isEnabled = true
            
            // Stop fake stats updates
            stopFakeSpeedUpdates()
            stopConnectionTimer()
            
            // Update data usage (fake increment)
            incrementDataUsage()
            
            println("Iran Liberty VPN: Fake connection disconnected")
            
        }, disconnectDelay.toLong())
    }
    
    /**
     * Start fake speed updates (oscillating between 0.5-12 Mbps)
     */
    private fun startFakeSpeedUpdates() {
        val speedUpdateTask = object : Runnable {
            override fun run() {
                if (isConnected) {
                    // Generate fake download speed (0.5-12 Mbps)
                    val download = 0.5 + random.nextDouble() * 11.5
                    // Upload is typically slower (0.3-8 Mbps)
                    val upload = 0.3 + random.nextDouble() * 7.7
                    
                    downloadSpeed.text = String.format("%.2f Mbps", download)
                    uploadSpeed.text = String.format("%.2f Mbps", upload)
                    
                    // Schedule next update with random jitter (1-3 seconds)
                    speedUpdateHandler.postDelayed(this, 1000 + random.nextInt(2000))
                }
            }
        }
        
        speedUpdateHandler.post(speedUpdateTask)
    }
    
    private fun stopFakeSpeedUpdates() {
        speedUpdateHandler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Start connection timer (shows how long "connected")
     */
    private fun startConnectionTimer() {
        val timerTask = object : Runnable {
            override fun run() {
                if (isConnected && connectionStartTime > 0) {
                    val elapsed = System.currentTimeMillis() - connectionStartTime
                    val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                    
                    connectionTime.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    
                    connectionTimerHandler.postDelayed(this, 1000)
                }
            }
        }
        
        connectionTimerHandler.post(timerTask)
    }
    
    private fun stopConnectionTimer() {
        connectionTimerHandler.removeCallbacksAndMessages(null)
        connectionTime.text = "00:00:00"
    }
    
    /**
     * Increment fake data usage after disconnection
     */
    private fun incrementDataUsage() {
        // Parse current values
        val todayText = dataToday.text.toString().replace(" MB", "")
        val totalText = dataTotal.text.toString().replace(" GB", "")
        
        val todayValue = todayText.toDoubleOrNull() ?: 124.7
        val totalValue = totalText.toDoubleOrNull() ?: 2.14
        
        // Add random data usage (10-150 MB)
        val sessionUsage = 10 + random.nextDouble() * 140
        val newToday = todayValue + sessionUsage
        val newTotal = totalValue + (sessionUsage / 1024) // Convert MB to GB
        
        dataToday.text = String.format("%.1f MB", newToday)
        dataTotal.text = String.format("%.2f GB", newTotal)
    }
    
    /**
     * Share app with friends (social engineering hook)
     */
    private fun shareAppWithFriends() {
        val shareText = getString(R.string.share_message)
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
        }
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }
    
    /**
     * Update UI based on connection state
     */
    private fun updateConnectionState() {
        // This would be called by other components (like FakeVpnService)
        // For now, just ensure initial state
        if (!isConnected) {
            connectedInfoLayout.visibility = android.view.View.GONE
            connectionStatus.text = getString(R.string.status_disconnected)
            connectionStatus.setTextColor(getColor(R.color.color_disconnected))
            connectButton.text = getString(R.string.connect)
        }
    }
    
    /**
     * Simulate random disconnect (called by Pathway 3's FakeVpnService)
     * This simulates the fake "connection lost" every 4-12 hours
     */
    fun simulateRandomDisconnect() {
        if (isConnected) {
            runOnUiThread {
                // Show reconnection prompt
                android.app.AlertDialog.Builder(this)
                    .setTitle(getString(R.string.reconnect_title))
                    .setMessage(getString(R.string.reconnect_message))
                    .setPositiveButton(getString(R.string.reconnect)) { _, _ ->
                        // User tapped reconnect - maintain the illusion
                        disconnectFromVpn()
                        // Auto-reconnect after delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            connectToVpn()
                        }, 2000)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    /**
     * Server adapter for RecyclerView
     */
    private inner class ServerAdapter(
        private val servers: List<Server>,
        private val onServerSelected: (Server) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {
        
        inner class ServerViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val serverName: TextView = itemView.findViewById(R.id.serverName)
            val serverPing: TextView = itemView.findViewById(R.id.serverPing)
            val serverLoad: TextView = itemView.findViewById(R.id.serverLoad)
            val pingIndicator: android.view.View = itemView.findViewById(R.id.pingIndicator)
            val cardView: android.view.View = itemView.findViewById(R.id.serverCardItem)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ServerViewHolder {
            val view = layoutInflater.inflate(R.layout.item_server, parent, false)
            return ServerViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
            val server = servers[position]
            
            holder.serverName.text = server.name
            holder.serverPing.text = "${server.ping} ms"
            holder.serverLoad.text = "${server.load}%"
            
            // Set ping indicator color (green for <50ms, yellow for 50-100ms, red for >100ms)
            val pingColor = when {
                server.ping < 50 -> getColor(R.color.color_good_ping)
                server.ping < 100 -> getColor(R.color.color_medium_ping)
                else -> getColor(R.color.color_bad_ping)
            }
            holder.pingIndicator.setBackgroundColor(pingColor)
            
            // Set click listener
            holder.cardView.setOnClickListener {
                onServerSelected(server)
            }
        }
        
        override fun getItemCount(): Int = servers.size
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up handlers
        connectionTimerHandler.removeCallbacksAndMessages(null)
        speedUpdateHandler.removeCallbacksAndMessages(null)
    }
    
    companion object {
        // Color resources (would be defined in colors.xml)
        // These are inline for simplicity - should be moved to colors.xml
        const val COLOR_CONNECTING = 0xFFF57C00
        const val COLOR_CONNECTED = 0xFF4CAF50
        const val COLOR_DISCONNECTED = 0xFFF44336
        const val COLOR_DISCONNECTING = 0xFFFF9800
        const val COLOR_GOOD_PING = 0xFF4CAF50
        const val COLOR_MEDIUM_PING = 0xFFFFC107
        const val COLOR_BAD_PING = 0xFFF44336
    }
}