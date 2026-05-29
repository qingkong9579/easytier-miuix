package top.easytier.miuix.jni

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.easytier.jni.EasyTierJNI
import org.json.JSONObject

class EasyTierManager(
    private val activity: Activity,
    private val instanceName: String,
    private val networkConfig: String,
) {
    companion object {
        private const val TAG = "EasyTierManager"
        private const val MONITOR_INTERVAL = 3000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var currentIpv4: String? = null
    private var currentProxyCidrs: List<String> = emptyList()
    private var vpnServiceIntent: Intent? = null
    private var onStatusChanged: ((EasyTierStatus) -> Unit)? = null

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                monitorNetworkStatus()
                handler.postDelayed(this, MONITOR_INTERVAL)
            }
        }
    }

    fun setStatusListener(listener: (EasyTierStatus) -> Unit) {
        onStatusChanged = listener
    }

    fun start() {
        if (isRunning) {
            Log.w(TAG, "EasyTier instance already running")
            return
        }

        try {
            val result = EasyTierJNI.runNetworkInstance(networkConfig)
            if (result == 0) {
                isRunning = true
                Log.i(TAG, "EasyTier instance started: $instanceName")
                handler.post(monitorRunnable)
                notifyStatus()
            } else {
                Log.e(TAG, "EasyTier instance failed to start: $result")
                val error = EasyTierJNI.getLastError()
                Log.e(TAG, "Error: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting EasyTier", e)
        }
    }

    fun stop() {
        if (!isRunning) {
            Log.w(TAG, "EasyTier instance not running")
            return
        }

        isRunning = false
        handler.removeCallbacks(monitorRunnable)

        try {
            stopVpnService()
            EasyTierJNI.stopAllInstances()
            Log.i(TAG, "EasyTier instance stopped: $instanceName")
            currentIpv4 = null
            currentProxyCidrs = emptyList()
            notifyStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping EasyTier", e)
        }
    }

    fun getStatus(): EasyTierStatus {
        return EasyTierStatus(
            isRunning = isRunning,
            instanceName = instanceName,
            currentIpv4 = currentIpv4,
            currentProxyCidrs = currentProxyCidrs.toList(),
        )
    }

    private fun monitorNetworkStatus() {
        try {
            val infosJson = EasyTierJNI.collectNetworkInfos(10)
            if (infosJson.isNullOrEmpty()) return

            val jsonObject = JSONObject(infosJson)
            val instanceInfo = jsonObject.optJSONObject(instanceName) ?: return

            val running = instanceInfo.optBoolean("running", false)
            if (!running) {
                Log.w(TAG, "Instance not running: ${instanceInfo.optString("error_msg")}")
                return
            }

            val detail = instanceInfo.optJSONObject("detail") ?: return
            val myNodeInfo = detail.optJSONObject("my_node_info") ?: return
            val virtualIpv4 = myNodeInfo.optJSONObject("virtual_ipv4") ?: return

            val addr = virtualIpv4.optInt("address", 0)
            val networkLength = virtualIpv4.optInt("network_length", 24)
            val ip = String.format(
                "%d.%d.%d.%d",
                (addr shr 24) and 0xFF,
                (addr shr 16) and 0xFF,
                (addr shr 8) and 0xFF,
                addr and 0xFF,
            )
            val newIpv4 = "$ip/$networkLength"

            val newProxyCidrs = mutableListOf<String>()
            val routes = detail.optJSONArray("routes")
            if (routes != null) {
                for (i in 0 until routes.length()) {
                    val route = routes.optJSONObject(i) ?: continue
                    val cidrs = route.optJSONArray("proxy_cidrs") ?: continue
                    for (j in 0 until cidrs.length()) {
                        cidrs.optString(j)?.let { newProxyCidrs.add(it) }
                    }
                }
            }

            val ipv4Changed = newIpv4 != currentIpv4
            val proxyCidrsChanged = newProxyCidrs != currentProxyCidrs

            if (ipv4Changed || proxyCidrsChanged) {
                Log.i(TAG, "Network changed - IPv4: $currentIpv4 -> $newIpv4")
                currentIpv4 = newIpv4
                currentProxyCidrs = newProxyCidrs.toList()

                if (newIpv4.isNotEmpty()) {
                    restartVpnService(newIpv4, newProxyCidrs)
                }
                notifyStatus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error monitoring network", e)
        }
    }

    private fun restartVpnService(ipv4: String, proxyCidrs: List<String>) {
        stopVpnService()
        startVpnService(ipv4, proxyCidrs)
    }

    private fun startVpnService(ipv4: String, proxyCidrs: List<String>) {
        try {
            val intent = Intent(activity, EasyTierVpnService::class.java).apply {
                putExtra("ipv4_address", ipv4)
                putStringArrayListExtra("proxy_cidrs", ArrayList(proxyCidrs))
                putExtra("instance_name", instanceName)
            }
            activity.startService(intent)
            vpnServiceIntent = intent
            Log.i(TAG, "VpnService started - IPv4: $ipv4")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VpnService", e)
        }
    }

    private fun stopVpnService() {
        try {
            vpnServiceIntent?.let { activity.stopService(it) }
            vpnServiceIntent = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VpnService", e)
        }
    }

    private fun notifyStatus() {
        onStatusChanged?.invoke(getStatus())
    }

    data class EasyTierStatus(
        val isRunning: Boolean,
        val instanceName: String,
        val currentIpv4: String?,
        val currentProxyCidrs: List<String>,
    )
}
