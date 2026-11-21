package com.deckcast.app.controller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deckcast.app.R
import com.deckcast.app.model.DiscoveredHost
import com.deckcast.app.network.NsdDiscoveryManager
import com.deckcast.app.webrtc.RemoteDeviceSession

/**
 * Foreground Service (`specialUse`) managing SessionManager, OverlayManager floating windows,
 * and local LAN `_remotedisplay._tcp.` NSD discovery.
 */
class OverlayControllerService : Service() {

    companion object {
        private const val CHANNEL_ID = "deckcast_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALL = "com.deckcast.app.action.STOP_ALL_OVERLAYS"
    }

    inner class LocalBinder : Binder() {
        fun getService(): OverlayControllerService = this@OverlayControllerService
    }

    private val binder = LocalBinder()
    lateinit var sessionManager: SessionManager
        private set
    lateinit var overlayManager: OverlayManager
        private set
    lateinit var nsdDiscoveryManager: NsdDiscoveryManager
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()

        sessionManager = SessionManager(applicationContext)
        overlayManager = OverlayManager(applicationContext, sessionManager)
        nsdDiscoveryManager = NsdDiscoveryManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            overlayManager.closeAll()
            sessionManager.closeAll()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        nsdDiscoveryManager.stopDiscovery()
        overlayManager.closeAll()
        sessionManager.closeAll()
        super.onDestroy()
    }

    fun connectToHost(host: DiscoveredHost) {
        overlayManager.openFloatingWindow(host)
    }

    fun startDiscovery(onHostsUpdated: (List<DiscoveredHost>) -> Unit) {
        nsdDiscoveryManager.startDiscovery(onHostsUpdated)
    }

    fun observeSessions(onChanged: (Map<String, RemoteDeviceSession.SessionState>) -> Unit) {
        sessionManager.setSessionListListener(onChanged)
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeckCast Controller Active")
            .setContentText("Managing floating remote display overlays")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }
}
