package top.easytier.miuix.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import top.easytier.miuix.data.model.toJSON
import top.easytier.miuix.data.model.toNetworkConfig
import java.io.File
import top.easytier.miuix.data.model.Ipv4Addr
import top.easytier.miuix.data.model.Ipv4Inet
import top.easytier.miuix.data.model.Mode
import top.easytier.miuix.data.model.NetworkConfig
import top.easytier.miuix.data.model.NetworkInstance
import top.easytier.miuix.data.model.NetworkInstanceRunningInfo
import top.easytier.miuix.data.model.NodeInfo
import top.easytier.miuix.data.model.PeerConnInfo
import top.easytier.miuix.data.model.PeerConnStats
import top.easytier.miuix.data.model.PeerInfo
import top.easytier.miuix.data.model.PeerRoutePair
import top.easytier.miuix.data.model.Route
import top.easytier.miuix.data.model.StunInfo
import top.easytier.miuix.data.model.TunnelInfo
import top.easytier.miuix.data.model.Url
import com.easytier.jni.EasyTierJNI
import top.easytier.miuix.jni.EasyTierVpnService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealNetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkRepository {

    companion object {
        private const val TAG = "RealNetworkRepository"
    }

    val isNativeReady: Boolean get() = EasyTierJNI.isNativeLoaded

    private val _mode = MutableStateFlow<Mode>(Mode.Normal())
    private val _clientRunning = MutableStateFlow(false)
    private val _configs = MutableStateFlow<List<NetworkConfig>>(emptyList())
    private val _instances = MutableStateFlow<Map<String, NetworkInstance>>(emptyMap())
    @Volatile private var _activePollingId: String? = null
    private var _runningInstanceId: String? = null
    private var _currentIpv4: String? = null
    private var _proxyCidrs: List<String> = emptyList()
    private var _pendingVpnIpv4: String? = null
    private var _pendingVpnProxyCidrs: List<String> = emptyList()
    val vpnPermissionNeeded = kotlinx.coroutines.flow.MutableStateFlow<Intent?>(null)

    private val configsFile: File
        get() = File(context.filesDir, "network_configs.json")

    init {
        loadConfigsFromFile()
    }

    private fun loadConfigsFromFile() {
        try {
            val file = configsFile
            if (!file.exists()) return
            val json = file.readText()
            val arr = JSONArray(json)
            val configs = mutableListOf<NetworkConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val config = try {
                    obj.toNetworkConfig()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse config at index $i", e)
                    null
                }
                if (config != null) configs.add(config)
            }
            _configs.value = configs
            // Create instances from loaded configs
            val instances = mutableMapOf<String, NetworkInstance>()
            configs.forEach { c ->
                instances[c.instanceId] = NetworkInstance(
                    instanceId = c.instanceId,
                    name = c.instanceName.ifEmpty { c.networkName },
                    running = false,
                )
            }
            _instances.value = instances
            Log.i(TAG, "Loaded ${configs.size} configs from file")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading configs", e)
        }
    }

    private fun saveConfigsToFile() {
        try {
            val arr = JSONArray()
            _configs.value.forEach { config ->
                arr.put(config.toJSON())
            }
            configsFile.writeText(arr.toString())
            Log.d(TAG, "Saved ${_configs.value.size} configs to file")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving configs", e)
        }
    }

    override fun getNetworkInstanceIds(): Flow<List<String>> = flow {
        while (true) {
            emit(_instances.value.keys.toList())
            delay(1000)
        }
    }

    override fun getNetworkInstance(instanceId: String): Flow<NetworkInstance?> = flow {
        while (true) {
            emit(_instances.value[instanceId])
            delay(1000)
        }
    }

    override fun getAllNetworkInstances(): Flow<List<NetworkInstance>> = flow {
        while (true) {
            emit(_instances.value.values.toList())
            delay(1000)
        }
    }

    override suspend fun loadConfigs(): List<NetworkConfig> {
        return _configs.value
    }

    override suspend fun saveConfigs(configs: List<NetworkConfig>) {
        _configs.value = configs
        saveConfigsToFile()
        val currentInstances = _instances.value.toMutableMap()
        configs.forEach { config ->
            val existing = currentInstances[config.instanceId]
            if (existing == null || existing.name != config.instanceName.ifEmpty { config.networkName }) {
                currentInstances[config.instanceId] = NetworkInstance(
                    instanceId = config.instanceId,
                    name = config.instanceName.ifEmpty { config.networkName },
                    running = existing?.running ?: false,
                    errorMsg = existing?.errorMsg ?: "",
                    detail = existing?.detail,
                )
            }
        }
        _instances.value = currentInstances
    }

    override suspend fun runNetworkInstance(config: NetworkConfig) {
        Log.d(TAG, "runNetworkInstance called: networkName=${config.networkName}, instanceName=${config.instanceName}, peerUrls=${config.peerUrls}")
        if (!EasyTierJNI.isNativeLoaded) {
            Log.e(TAG, "Native library not loaded")
            _instances.value = _instances.value.toMutableMap().apply {
                put(config.instanceId, NetworkInstance(
                    instanceId = config.instanceId,
                    name = config.instanceName.ifEmpty { config.networkName },
                    running = false,
                    errorMsg = "Native library not loaded. Build native libs first.",
                ))
            }
            return
        }
        try {
            // Stop any existing running instance first
            val runningId = _instances.value.entries
                .firstOrNull { it.value.running }?.key
            if (runningId != null && runningId != config.instanceId) {
                Log.i(TAG, "Stopping currently running instance: $runningId")
                stopNetworkInstance(runningId)
            }

            // Ensure clean state
            _clientRunning.value = false
            EasyTierJNI.stopAllInstances()
            kotlinx.coroutines.delay(300)
            stopVpnService()
            _currentIpv4 = null
            _proxyCidrs = emptyList()

            val toml = generateTomlConfig(config)
            Log.d(TAG, "Running instance with config:\n$toml")
            val result = EasyTierJNI.runNetworkInstance(toml)
            Log.d(TAG, "runNetworkInstance result: $result")
            if (result == 0) {
                _clientRunning.value = true
                _runningInstanceId = config.instanceName.ifEmpty { config.networkName }
                _instances.value = _instances.value.toMutableMap().apply {
                    put(config.instanceId, NetworkInstance(
                        instanceId = config.instanceId,
                        name = config.instanceName.ifEmpty { config.networkName },
                        running = true,
                    ))
                }
                startPolling(config.instanceId, config.instanceName.ifEmpty { config.networkName })
            } else {
                val error = EasyTierJNI.getLastError()
                Log.e(TAG, "Failed to run instance: $error")
                _instances.value = _instances.value.toMutableMap().apply {
                    put(config.instanceId, NetworkInstance(
                        instanceId = config.instanceId,
                        name = config.instanceName.ifEmpty { config.networkName },
                        running = false,
                        errorMsg = error ?: "Unknown error",
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception running instance", e)
        }
    }

    override suspend fun stopNetworkInstance(instanceId: String) {
        try {
            _clientRunning.value = false
            // Stop EasyTier first so it can gracefully release the TUN fd
            EasyTierJNI.stopAllInstances()
            // Small delay to let EasyTier cleanup complete
            kotlinx.coroutines.delay(500)
            stopVpnService()
            _instances.value = _instances.value.toMutableMap().apply {
                this[instanceId]?.let { inst ->
                    put(instanceId, inst.copy(running = false, detail = null))
                }
            }
            _runningInstanceId = null
            _currentIpv4 = null
            _proxyCidrs = emptyList()
            _activePollingId = null
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping instance", e)
        }
    }

    override suspend fun deleteNetworkInstance(instanceId: String) {
        stopNetworkInstance(instanceId)
        _instances.value = _instances.value.toMutableMap().apply { remove(instanceId) }
        _configs.value = _configs.value.filter { it.instanceId != instanceId }
    }

    override suspend fun collectNetworkInfo(instanceId: String): NetworkInstance? {
        return _instances.value[instanceId]
    }

    override fun getCurrentMode(): Flow<Mode> = _mode.asStateFlow()

    override suspend fun setMode(mode: Mode) {
        _mode.value = mode
    }

    override suspend fun startClient(mode: Mode) {
        _clientRunning.value = true
        _mode.value = mode
    }

    override suspend fun stopClient() {
        stopVpnService()
        EasyTierJNI.stopAllInstances()
        _clientRunning.value = false
        _runningInstanceId = null
        _currentIpv4 = null
    }

    override fun isClientRunning(): Flow<Boolean> = _clientRunning.asStateFlow()

    private fun startPolling(instanceId: String, instanceName: String) {
        _activePollingId = instanceId
        Thread {
            while (_clientRunning.value && _activePollingId == instanceId) {
                try {
                    val json = EasyTierJNI.collectNetworkInfos(10)
                    Log.d(TAG, "Poll result: ${json?.take(200)}")
                    if (!json.isNullOrEmpty()) {
                        val instance = parseNetworkInfo(json, instanceName)
                        if (instance != null) {
                            _instances.value = _instances.value.toMutableMap().apply {
                                val existing = this[instanceId]
                                put(instanceId, instance.copy(
                                    instanceId = instanceId,
                                    name = existing?.name ?: instance.name,
                                ))
                            }
                            // Check if we got a virtual IP and should start VPN
                            checkAndStartVpn(instance.copy(instanceId = instanceId))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling", e)
                }
                Thread.sleep(3000)
            }
        }.start()
    }

    private fun checkAndStartVpn(instance: NetworkInstance) {
        val detail = instance.detail ?: return
        val virtualIpv4 = detail.myNodeInfo.virtualIpv4
        val addr = virtualIpv4.address.addr
        if (addr == 0) return

        val ip = String.format(
            "%d.%d.%d.%d",
            (addr shr 24) and 0xFF, (addr shr 16) and 0xFF,
            (addr shr 8) and 0xFF, addr and 0xFF,
        )
        val newIpv4 = "$ip/${virtualIpv4.networkLength}"

        // Collect routes: direct routes + peer route proxy CIDRs
        val newProxyCidrs = mutableListOf<String>()
        detail.routes.forEach { route ->
            route.ipv4Addr?.let { newProxyCidrs.add(it) }
            route.proxyCidrs.forEach { cidr -> newProxyCidrs.add(cidr) }
        }
        detail.peerRoutePairs.forEach { pair ->
            pair.route.proxyCidrs.forEach { cidr -> newProxyCidrs.add(cidr) }
        }

        val cidrsChanged = newProxyCidrs.toSet() != _proxyCidrs.toSet()
        if (newIpv4 != _currentIpv4 || cidrsChanged) {
            Log.i(TAG, "VPN update - IPv4: $newIpv4, CIDRs: $newProxyCidrs")
            _currentIpv4 = newIpv4
            _proxyCidrs = newProxyCidrs
            startVpnService(newIpv4, newProxyCidrs)
        }
    }

    private fun startVpnService(ipv4: String, proxyCidrs: List<String>) {
        try {
            // Check VPN permission first
            val prepareIntent = android.net.VpnService.prepare(context)
            if (prepareIntent != null) {
                Log.w(TAG, "VPN permission not granted, cannot start VPN service")
                // Store pending VPN params for when permission is granted
                _pendingVpnIpv4 = ipv4
                _pendingVpnProxyCidrs = proxyCidrs
                vpnPermissionNeeded.value = prepareIntent
                return
            }

            val vpnIntent = Intent(context, EasyTierVpnService::class.java).apply {
                putExtra("ipv4_address", ipv4)
                putStringArrayListExtra("proxy_cidrs", ArrayList(proxyCidrs))
                putExtra("instance_name", _runningInstanceId ?: return)
            }
            context.startService(vpnIntent)
            Log.i(TAG, "VPN service started - IPv4: $ipv4")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN service", e)
        }
    }

    fun onVpnPermissionGranted() {
        // Start VPN service with pending params
        val ipv4 = _pendingVpnIpv4 ?: return
        val proxyCidrs = _pendingVpnProxyCidrs
        _pendingVpnIpv4 = null
        _pendingVpnProxyCidrs = emptyList()
        vpnPermissionNeeded.value = null
        startVpnService(ipv4, proxyCidrs)
    }

    private fun stopVpnService() {
        try {
            val vpnIntent = Intent(context, EasyTierVpnService::class.java).apply {
                putExtra("stop_vpn", true)
            }
            context.startService(vpnIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN service", e)
        }
    }

    private fun parseNetworkInfo(json: String, lookupKey: String): NetworkInstance? {
        return try {
            val root = JSONObject(json)
            val map = root.optJSONObject("map") ?: root
            val info = map.optJSONObject(lookupKey)
            if (info == null) {
                Log.w(TAG, "parseNetworkInfo: key '$lookupKey' not found in map. Available keys: ${map.keys().asSequence().toList()}")
                return null
            }

            // collect_network_infos returns the instance detail directly (no running/detail wrapper)
            val detail = parseRunningInfo(info)

            NetworkInstance(
                instanceId = lookupKey,
                running = true,
                errorMsg = detail.errorMsg ?: "",
                detail = detail,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing network info", e)
            null
        }
    }

    private fun parseRunningInfo(obj: JSONObject): NetworkInstanceRunningInfo {
        val devName = obj.optString("dev_name", "")
        val myNodeInfo = obj.optJSONObject("my_node_info")?.let { parseNodeInfo(it) } ?: NodeInfo()
        val running = obj.optBoolean("running", false)
        val errorMsg = obj.optString("error_msg", "").takeIf { it.isNotEmpty() && it != "null" }

        val events = mutableListOf<String>()
        val parsedEvents = mutableListOf<top.easytier.miuix.data.model.EventInfo>()
        val eventsArr = obj.optJSONArray("events")
        if (eventsArr != null) {
            for (i in 0 until eventsArr.length()) {
                val eventObj = eventsArr.optJSONObject(i)
                if (eventObj != null) {
                    val level = eventObj.optString("level", "info")
                    val message = eventObj.optString("message", eventObj.optString("msg", ""))
                    val peerId = eventObj.optLong("peer_id", 0)
                    val timestamp = eventObj.optLong("timestamp", System.currentTimeMillis())
                    val raw = eventObj.toString()
                    events.add(raw)
                    parsedEvents.add(
                        top.easytier.miuix.data.model.EventInfo(
                            level = level,
                            message = message.ifEmpty { raw },
                            peerId = peerId,
                            timestamp = timestamp,
                            raw = raw,
                        )
                    )
                } else {
                    val str = eventsArr.optString(i)
                    if (!str.isNullOrEmpty()) {
                        events.add(str)
                        parsedEvents.add(top.easytier.miuix.data.model.EventInfo(message = str, raw = str))
                    }
                }
            }
        }

        val routes = mutableListOf<Route>()
        val routesArr = obj.optJSONArray("routes")
        if (routesArr != null) {
            for (i in 0 until routesArr.length()) {
                routesArr.optJSONObject(i)?.let { routes.add(parseRoute(it)) }
            }
        }

        val peers = mutableListOf<PeerInfo>()
        val peersArr = obj.optJSONArray("peers")
        if (peersArr != null) {
            for (i in 0 until peersArr.length()) {
                peersArr.optJSONObject(i)?.let { peers.add(parsePeerInfo(it)) }
            }
        }

        val peerRoutePairs = mutableListOf<PeerRoutePair>()
        val pairsArr = obj.optJSONArray("peer_route_pairs")
        if (pairsArr != null) {
            for (i in 0 until pairsArr.length()) {
                val pairObj = pairsArr.optJSONObject(i) ?: continue
                val route = pairObj.optJSONObject("route")?.let { parseRoute(it) } ?: Route()
                val peer = pairObj.optJSONObject("peer")?.let { parsePeerInfo(it) }
                peerRoutePairs.add(PeerRoutePair(route, peer))
            }
        }

        return NetworkInstanceRunningInfo(
            devName = devName,
            myNodeInfo = myNodeInfo,
            events = events,
            parsedEvents = parsedEvents,
            routes = routes,
            peers = peers,
            peerRoutePairs = peerRoutePairs,
            running = running,
            errorMsg = errorMsg,
        )
    }

    private fun parseNodeInfo(obj: JSONObject): NodeInfo {
        val virtualIpv4 = obj.optJSONObject("virtual_ipv4")?.let {
            val addr = it.optJSONObject("address")?.optInt("addr", 0) ?: 0
            val len = it.optInt("network_length", 24)
            Ipv4Inet(Ipv4Addr(addr), len)
        } ?: Ipv4Inet()

        val hostname = obj.optString("hostname", "")
        val version = obj.optString("version", "")
        val peerId = obj.optLong("peer_id", 0)

        val ips = obj.optJSONObject("ips")
        val publicIpv4 = ips?.optJSONObject("public_ipv4")?.optInt("addr", 0)?.let { Ipv4Addr(it) } ?: Ipv4Addr()
        val listeners = mutableListOf<Url>()
        val listenersArr = obj.optJSONArray("listeners")
        if (listenersArr != null) {
            for (i in 0 until listenersArr.length()) {
                listenersArr.optJSONObject(i)?.optString("url")?.let { listeners.add(Url(it)) }
            }
        }

        val stunInfo = obj.optJSONObject("stun_info")?.let {
            StunInfo(
                udpNatType = it.optInt("udp_nat_type", 0),
                tcpNatType = it.optInt("tcp_nat_type", 0),
                lastUpdateTime = it.optLong("last_update_time", 0),
            )
        } ?: StunInfo()

        return NodeInfo(
            virtualIpv4 = virtualIpv4,
            hostname = hostname,
            version = version,
            publicIpv4 = publicIpv4,
            listeners = listeners,
            stunInfo = stunInfo,
            peerId = peerId,
        )
    }

    private fun parseRoute(obj: JSONObject): Route {
        val ipv4Addr = obj.optJSONObject("ipv4_addr")?.let {
            val addr = it.optJSONObject("address")?.optInt("addr", 0) ?: 0
            val len = it.optInt("network_length", 24)
            val ip = String.format(
                "%d.%d.%d.%d",
                (addr shr 24) and 0xFF, (addr shr 16) and 0xFF,
                (addr shr 8) and 0xFF, addr and 0xFF,
            )
            "$ip/$len"
        }

        val proxyCidrs = mutableListOf<String>()
        val cidrsArr = obj.optJSONArray("proxy_cidrs")
        if (cidrsArr != null) {
            for (i in 0 until cidrsArr.length()) {
                cidrsArr.optString(i)?.let { proxyCidrs.add(it) }
            }
        }

        return Route(
            peerId = obj.optLong("peer_id", 0),
            ipv4Addr = ipv4Addr,
            nextHopPeerId = obj.optLong("next_hop_peer_id", 0),
            cost = obj.optInt("cost", 0),
            hostname = obj.optString("hostname", ""),
            version = obj.optString("version", ""),
            proxyCidrs = proxyCidrs,
        )
    }

    private fun parsePeerInfo(obj: JSONObject): PeerInfo {
        val conns = mutableListOf<PeerConnInfo>()
        val connsArr = obj.optJSONArray("conns")
        if (connsArr != null) {
            for (i in 0 until connsArr.length()) {
                connsArr.optJSONObject(i)?.let { conns.add(parsePeerConnInfo(it)) }
            }
        }
        return PeerInfo(peerId = obj.optLong("peer_id", 0), conns = conns)
    }

    private fun parsePeerConnInfo(obj: JSONObject): PeerConnInfo {
        val tunnel = obj.optJSONObject("tunnel")?.let {
            TunnelInfo(
                tunnelType = it.optString("tunnel_type", ""),
                localAddr = Url(it.optJSONObject("local_addr")?.optString("url", "") ?: ""),
                remoteAddr = Url(it.optJSONObject("remote_addr")?.optString("url", "") ?: ""),
            )
        }

        val stats = obj.optJSONObject("stats")?.let {
            PeerConnStats(
                rxBytes = it.optLong("rx_bytes", 0),
                txBytes = it.optLong("tx_bytes", 0),
                rxPackets = it.optLong("rx_packets", 0),
                txPackets = it.optLong("tx_packets", 0),
                latencyUs = it.optLong("latency_us", 0),
            )
        }

        return PeerConnInfo(
            connId = obj.optString("conn_id", ""),
            myPeerId = obj.optLong("my_peer_id", 0),
            peerId = obj.optLong("peer_id", 0),
            tunnel = tunnel,
            stats = stats,
            lossRate = obj.optDouble("loss_rate", 0.0).toFloat(),
        )
    }

    private fun generateTomlConfig(config: NetworkConfig): String {
        return buildString {
            // Match the exact format from easytier-gui's gen_config() + dump()
            // Top-level fields (only non-default values)
            val instName = config.instanceName.ifEmpty { config.networkName }
            appendLine("instance_name = \"$instName\"")
            appendLine("instance_id = \"${config.instanceId}\"")
            config.hostname?.let { appendLine("hostname = \"$it\"") }

            // DHCP or static IP
            if (config.dhcp) {
                appendLine("dhcp = true")
            } else {
                val ipv4 = config.virtualIpv4.ifEmpty { "10.144.144.1/24" }
                appendLine("ipv4 = \"$ipv4\"")
            }

            appendLine("listeners = [")
            config.listenerUrls.filter { it.isNotBlank() }.forEach { url ->
                appendLine("    \"$url\",")
            }
            appendLine("]")
            appendLine("rpc_portal = \"0.0.0.0:0\"")

            appendLine()
            appendLine("[network_identity]")
            appendLine("network_name = \"${config.networkName}\"")
            if (config.networkSecret.isNotEmpty()) {
                appendLine("network_secret = \"${config.networkSecret}\"")
            }

            config.peerUrls.filter { it.isNotBlank() }.forEach { url ->
                val cleanUrl = url.trim()
                if (cleanUrl.isNotEmpty()) {
                    appendLine()
                    appendLine("[[peer]]")
                    appendLine("uri = \"$cleanUrl\"")
                }
            }

            // Only include flags that differ from defaults
            val flags = mutableListOf<String>()
            flags.add("no_tun = true")  // Android: VPN service provides TUN fd
            if (!config.bindDevice) flags.add("bind_device = false")
            if (config.devName.isNotEmpty()) flags.add("dev_name = \"${config.devName}\"")
            config.mtu?.let { flags.add("mtu = $it") }
            if (config.latencyFirst) flags.add("latency_first = true")
            if (config.disableIpv6) flags.add("enable_ipv6 = false")
            if (config.disableP2p) flags.add("disable_p2p = true")
            if (config.noTun) flags.add("no_tun = true")
            if (config.enableExitNode) flags.add("enable_exit_node = true")
            if (!config.multiThread) flags.add("multi_thread = false")
            if (config.enableKcpProxy) flags.add("enable_kcp_proxy = true")
            if (config.disableEncryption) flags.add("enable_encryption = false")
            if (config.disableTcpHolePunching) flags.add("disable_tcp_hole_punching = true")
            if (config.disableUdpHolePunching) flags.add("disable_udp_hole_punching = true")
            if (flags.isNotEmpty()) {
                appendLine()
                appendLine("[flags]")
                flags.forEach { flag -> appendLine(flag) }
            }

            if (config.enableVpnPortal) {
                appendLine()
                appendLine("[vpn_portal_config]")
                val cidr = "${config.vpnPortalClientNetworkAddr}/${config.vpnPortalClientNetworkLen}"
                appendLine("client_cidr = \"$cidr\"")
                appendLine("wireguard_listen = \"0.0.0.0:${config.vpnPortalListenPort}\"")
            }

            // Proxy CIDRs
            config.proxyCidrs.filter { it.isNotBlank() }.forEach { cidr ->
                appendLine()
                appendLine("[[proxy_network]]")
                appendLine("cidr = \"$cidr\"")
                appendLine("allow = true")
            }

            // Port forwards
            config.portForwards.filter { it.bindIp.isNotEmpty() && it.dstIp.isNotEmpty() }.forEach { pf ->
                appendLine()
                appendLine("[[port_forward]]")
                appendLine("bind_addr = \"${pf.bindIp}:${pf.bindPort}\"")
                appendLine("dst_addr = \"${pf.dstIp}:${pf.dstPort}\"")
                appendLine("proto = \"${pf.proto}\"")
            }
        }
    }
}
