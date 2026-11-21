package com.deckcast.app.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.deckcast.app.R
import com.deckcast.app.controller.OverlayControllerService
import com.deckcast.app.databinding.ActivityMainBinding
import com.deckcast.app.host.RemoteAccessibilityService
import com.deckcast.app.host.ScreenHostService
import com.deckcast.app.model.DiscoveredHost
import com.deckcast.app.network.LocalSignalingServer
import java.net.Inet4Address

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var controllerService: OverlayControllerService? = null
    private var isBound = false

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val use1080p = binding.rb1080p30.isChecked
                val w = if (use1080p) 1920 else 1280
                val h = if (use1080p) 1080 else 720
                val fps = if (use1080p) 30 else 60

                val intent = Intent(this, ScreenHostService::class.java).apply {
                    putExtra(ScreenHostService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenHostService.EXTRA_RESULT_DATA, result.data)
                    putExtra(ScreenHostService.EXTRA_WIDTH, w)
                    putExtra(ScreenHostService.EXTRA_HEIGHT, h)
                    putExtra(ScreenHostService.EXTRA_FPS, fps)
                }
                ContextCompat.startForegroundService(this, intent)
                refreshHostStatus(forceRunning = true)
            } else {
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshStatusViews()
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OverlayControllerService.LocalBinder
            controllerService = binder.getService()
            isBound = true
            controllerService?.observeSessions { sessions ->
                runOnUiThread {
                    val count = sessions.size
                    binding.tvActiveSessions.text = if (count == 1) "1 Window" else "$count Windows"
                }
            }
            controllerService?.startDiscovery { hosts ->
                runOnUiThread { renderDiscoveredHosts(hosts) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            controllerService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        wireUi()
        ensureControllerServiceBound()
    }

    override fun onResume() {
        super.onResume()
        refreshStatusViews()
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun wireUi() {
        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnStartHost.setOnClickListener {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        binding.btnStopHost.setOnClickListener {
            val stopIntent = Intent(this, ScreenHostService::class.java).apply {
                action = ScreenHostService.ACTION_STOP_HOST
            }
            startService(stopIntent)
            refreshHostStatus(forceRunning = false)
        }

        binding.btnScanHosts.setOnClickListener {
            binding.tvNoHostsHint.visibility = View.VISIBLE
            binding.tvNoHostsHint.text = "Scanning LAN (_remotedisplay._tcp.)…"
            controllerService?.startDiscovery { hosts ->
                runOnUiThread { renderDiscoveredHosts(hosts) }
            }
        }

        binding.btnCloseAll.setOnClickListener {
            controllerService?.overlayManager?.closeAll()
        }

        binding.btnConnectManual.setOnClickListener {
            val raw = binding.etManualHost.text.toString().trim()
            if (raw.isEmpty()) return@setOnClickListener
            val parts = raw.split(":")
            val ip = parts[0].trim()
            val port = parts.getOrNull(1)?.toIntOrNull() ?: LocalSignalingServer.DEFAULT_WS_PORT
            val manualHost = DiscoveredHost(
                serviceName = "Manual-$ip",
                deviceName = ip,
                osType = "remote",
                hostAddress = ip,
                wsPort = port
            )
            openFloatingHost(manualHost)
        }
    }

    private fun renderDiscoveredHosts(hosts: List<DiscoveredHost>) {
        binding.llDiscoveredHosts.removeAllViews()
        binding.tvNoHostsHint.visibility = if (hosts.isEmpty()) View.VISIBLE else View.GONE
        if (hosts.isEmpty()) {
            binding.tvNoHostsHint.text = "No LAN hosts found yet — make sure both devices share Wi-Fi/hotspot."
            return
        }

        val dp = resources.displayMetrics.density
        for (host in hosts) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_host_item)
                setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * dp).toInt()
                }
                setOnClickListener { openFloatingHost(host) }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(this).apply {
                text = host.deviceName
                setTextColor(0xFFF0F6FC.toInt())
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val metaView = TextView(this).apply {
                text = "${host.osType.uppercase()} • ${host.hostAddress}:${host.wsPort}"
                setTextColor(0xFF94A3B8.toInt())
                textSize = 12f
            }
            textCol.addView(nameView)
            textCol.addView(metaView)

            val connectPill = TextView(this).apply {
                text = "Open"
                setTextColor(0xFF00E5FF.toInt())
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding((10 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            }

            card.addView(textCol)
            card.addView(connectPill)
            binding.llDiscoveredHosts.addView(card)
        }
    }

    private fun openFloatingHost(host: DiscoveredHost) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant Overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        controllerService?.connectToHost(host)
    }

    private fun ensureControllerServiceBound() {
        val intent = Intent(this, OverlayControllerService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun refreshStatusViews() {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasA11y = RemoteAccessibilityService.isEnabled()
        binding.tvPermissionStatus.text =
            "Overlay Windows: ${if (hasOverlay) "GRANTED" else "ACTION NEEDED"}\n" +
                "Remote Touch Injection: ${if (hasA11y) "ENABLED" else "DISABLED (Host only)"}"
        binding.btnGrantOverlay.isEnabled = !hasOverlay
        refreshHostStatus(forceRunning = ScreenHostService.isRunning)
    }

    private fun refreshHostStatus(forceRunning: Boolean) {
        val lanIp = getLocalLanIp() ?: "<LAN IP>"
        if (forceRunning) {
            binding.tvHostBadge.text = "● LIVE"
            binding.tvHostBadge.setTextColor(0xFF2EF2A0.toInt())
            binding.tvHostStatus.text =
                "Streaming at ws://$lanIp:${LocalSignalingServer.DEFAULT_WS_PORT}/signal"
            binding.btnStartHost.isEnabled = false
            binding.btnStopHost.isEnabled = true
        } else {
            binding.tvHostBadge.text = "● IDLE"
            binding.tvHostBadge.setTextColor(0xFF94A3B8.toInt())
            binding.tvHostStatus.text =
                "Local address: $lanIp:${LocalSignalingServer.DEFAULT_WS_PORT}"
            binding.btnStartHost.isEnabled = true
            binding.btnStopHost.isEnabled = false
        }
    }

    private fun getLocalLanIp(): String? {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val linkProps = cm.getLinkProperties(network) ?: return null
        return linkProps.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }
}
