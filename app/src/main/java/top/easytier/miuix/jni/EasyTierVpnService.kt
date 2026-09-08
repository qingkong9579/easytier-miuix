package top.easytier.miuix.jni

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.easytier.jni.EasyTierJNI
import top.easytier.miuix.MainActivity
import top.easytier.miuix.R
import kotlin.concurrent.thread

class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var instanceName: String? = null

    companion object {
        private const val TAG = "EasyTierVpnService"
        private const val CHANNEL_ID = "easytier_vpn"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_HOLD = "hold_vpn"

        /**
         * 服务存活标志（对齐上游 TauriVpnService.self）：
         * 磁贴等外部入口用它判定 VPN 是否在运行，进程死亡即视为停止。
         */
        @Volatile
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
        isServiceRunning = true
        EasyTierTileService.requestStateUpdate(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 由 startForegroundService 启动时必须先调用 startForeground，再处理停止逻辑
        startVpnForeground()

        // Handle stop command
        val stopAction = intent?.getBooleanExtra("stop_vpn", false) ?: false
        if (stopAction) {
            Log.i(TAG, "Stop command received, shutting down VPN")
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }

        // Hold 模式：磁贴/后台启动时先占住前台进程（核心启动期间进程无系统可见组件，
        // 会被立即回收导致 VPN 永远起不来），待仓库层以真实参数再次启动后建立 TUN
        if (intent?.getBooleanExtra(EXTRA_HOLD, false) == true && !isRunning) {
            instanceName = intent?.getStringExtra("instance_name")
            Log.i(TAG, "Hold mode: protecting process while instance starts")
            updateForegroundNotification(getString(R.string.config_starting))
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
        updateForegroundNotification(instanceName!!)

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

    private fun startVpnForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.tile_label), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = buildForegroundNotification(instanceName ?: "")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun updateForegroundNotification(instance: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(instance))
    }

    private fun buildForegroundNotification(instance: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_vpn)
            .setContentTitle(getString(R.string.tile_label))
            .setContentText(instance.ifEmpty { getString(R.string.network_running) })
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "VPN interface cleaned up")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "VPN Service destroyed")
        cleanup()
        isServiceRunning = false
        EasyTierTileService.requestStateUpdate(this)
    }
}
