package com.deckcast.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.deckcast.app.model.DiscoveredHost
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Advertises and discovers DeckCast hosts on the local LAN/hotspot under `_remotedisplay._tcp.`.
 */
class NsdDiscoveryManager(context: Context) {

    companion object {
        const val SERVICE_TYPE = "_remotedisplay._tcp."
        private const val TAG = "DeckCastNSD"
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var activeServiceCallbacks: MutableMap<String, Any> = ConcurrentHashMap()
    private val discoveredHosts = ConcurrentHashMap<String, DiscoveredHost>()

    fun registerHost(deviceName: String, wsPort: Int, onRegistered: (String) -> Unit = {}) {
        unregisterHost()
        acquireMulticastLock()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "DeckCast-${deviceName.take(20)}"
            serviceType = SERVICE_TYPE
            port = wsPort
            setAttribute("deviceName", deviceName)
            setAttribute("osType", "android")
            setAttribute("wsPort", wsPort.toString())
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD registered: ${info.serviceName} on port $wsPort")
                onRegistered(info.serviceName)
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "NSD unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "NSD unregistration failed: $errorCode")
            }
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregisterHost() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
    }

    fun startDiscovery(onHostsUpdated: (List<DiscoveredHost>) -> Unit) {
        stopDiscovery()
        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "Discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_remotedisplay")) return
                resolveServiceCompat(serviceInfo, onHostsUpdated)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val key = serviceInfo.serviceName
                discoveredHosts.remove(key)
                unregisterInfoCallbackIfPresent(key)
                onHostsUpdated(discoveredHosts.values.toList())
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        activeServiceCallbacks.keys.toList().forEach { unregisterInfoCallbackIfPresent(it) }
        discoveredHosts.clear()
        releaseMulticastLock()
    }

    private fun resolveServiceCompat(
        serviceInfo: NsdServiceInfo,
        onHostsUpdated: (List<DiscoveredHost>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    Log.w(TAG, "ServiceInfoCallback failed: $errorCode")
                }

                override fun onServiceUpdated(updated: NsdServiceInfo) {
                    handleResolvedInfo(updated, onHostsUpdated)
                }

                override fun onServiceLost() {
                    discoveredHosts.remove(serviceInfo.serviceName)
                    onHostsUpdated(discoveredHosts.values.toList())
                }

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            activeServiceCallbacks[serviceInfo.serviceName] = cb
            nsdManager.registerServiceInfoCallback(serviceInfo, callbackExecutor, cb)
        } else {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    handleResolvedInfo(info, onHostsUpdated)
                }
            })
        }
    }

    private fun unregisterInfoCallbackIfPresent(serviceName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val cb = activeServiceCallbacks.remove(serviceName) as? NsdManager.ServiceInfoCallback
            if (cb != null) {
                runCatching { nsdManager.unregisterServiceInfoCallback(cb) }
            }
        }
    }

    private fun handleResolvedInfo(
        info: NsdServiceInfo,
        onHostsUpdated: (List<DiscoveredHost>) -> Unit
    ) {
        val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }?.hostAddress
                ?: info.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            info.host?.hostAddress
        } ?: return

        val attrs = info.attributes
        val deviceName = attrs["deviceName"]?.let { String(it, StandardCharsets.UTF_8) }
            ?: info.serviceName
        val osType = attrs["osType"]?.let { String(it, StandardCharsets.UTF_8) } ?: "unknown"
        val wsPort = attrs["wsPort"]?.let { String(it, StandardCharsets.UTF_8).toIntOrNull() }
            ?: info.port

        val host = DiscoveredHost(
            serviceName = info.serviceName,
            deviceName = deviceName,
            osType = osType,
            hostAddress = hostAddress,
            wsPort = wsPort
        )
        discoveredHosts[info.serviceName] = host
        onHostsUpdated(discoveredHosts.values.sortedBy { it.deviceName })
    }

    private fun acquireMulticastLock() {
        if (multicastLock == null) {
            multicastLock = wifiManager.createMulticastLock("DeckCastNsdLock").apply {
                setReferenceCounted(false)
            }
        }
        if (multicastLock?.isHeld == false) {
            runCatching { multicastLock?.acquire() }
        }
    }

    private fun releaseMulticastLock() {
        if (multicastLock?.isHeld == true) {
            runCatching { multicastLock?.release() }
        }
    }
}
