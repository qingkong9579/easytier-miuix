package top.easytier.miuix.jni

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.easytier.jni.EasyTierJNI
import kotlin.concurrent.thread

class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var instanceName: String? = null

    companion object {
        private const val TAG = "EasyTierVpnService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop command
        val stopAction = intent?.getBooleanExtra("stop_vpn", false) ?: false
        if (stopAction) {
            Log.i(TAG, "Stop command received, shutting down VPN")
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }

        val ipv4Address = intent?.getStringExtra("ipv4_address")
        val proxyCidrs = intent?.getStringArrayListExtra("proxy_cidrs") ?: arrayListOf()
        instanceName = intent?.getStringExtra("instance_name")

        if (ipv4Address == null || instanceName == null) {
            Log.e(TAG, "Missing required params: ipv4Address=$ipv4Address, instanceName=$instanceName")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i(TAG, "Starting VPN Service - IPv4: $ipv4Address, Instance: $instanceName")

        thread {
            try {
                setupVpnInterface(ipv4Address, proxyCidrs)
            } catch (t: Throwable) {
                Log.e(TAG, "VPN setup failed", t)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun setupVpnInterface(ipv4Address: String, proxyCidrs: List<String>) {
        try {
            val (ip, networkLength) = parseIpv4Address(ipv4Address)

            val builder = Builder()
            builder.setSession("EasyTier VPN")
                .addAddress(ip, networkLength)
                .addDnsServer("223.5.5.5")
                .addDnsServer("114.114.114.114")
                .addDisallowedApplication(packageName)

            proxyCidrs.forEach { cidr ->
                try {
                    val (routeIp, routeLength) = parseCidr(cidr)
                    builder.addRoute(routeIp, routeLength)
                    Log.d(TAG, "Added route: $routeIp/$routeLength")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse CIDR: $cidr", e)
                }
            }

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                Log.e(TAG, "Failed to create VPN interface")
                return
            }

            Log.i(TAG, "VPN interface created")

            instanceName?.let { name ->
                val fd = vpnInterface!!.fd
                val result = EasyTierJNI.setTunFd(name, fd)
                if (result == 0) {
                    Log.i(TAG, "TUN fd set successfully: $fd")
                } else {
                    Log.e(TAG, "Failed to set TUN fd: $result")
                }
            }

            isRunning = true

            while (isRunning && vpnInterface != null) {
                Thread.sleep(1000)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error during VPN setup", t)
        } finally {
            cleanup()
        }
    }

    private fun parseIpv4Address(ipv4Address: String): Pair<String, Int> {
        return if (ipv4Address.contains("/")) {
            val parts = ipv4Address.split("/")
            Pair(parts[0], parts[1].toInt())
        } else {
            Pair(ipv4Address, 24)
        }
    }

    private fun parseCidr(cidr: String): Pair<String, Int> {
        val parts = cidr.split("/")
        if (parts.size != 2) throw IllegalArgumentException("Invalid CIDR: $cidr")
        return Pair(parts[0], parts[1].toInt())
    }

    private fun cleanup() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        Log.i(TAG, "VPN interface cleaned up")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
        cleanup()
    }
}
