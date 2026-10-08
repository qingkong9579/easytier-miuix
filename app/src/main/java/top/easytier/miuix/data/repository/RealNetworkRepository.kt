package top.easytier.miuix.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.easytier.miuix.data.model.toJSON
import top.easytier.miuix.data.model.toNetworkConfig
import java.io.File
import top.easytier.miuix.data.model.CompressionAlgo
import top.easytier.miuix.data.model.EncryptionAlgorithm
import top.easytier.miuix.data.model.EventInfo
import top.easytier.miuix.data.model.EventLevel
import top.easytier.miuix.data.model.Ipv4Addr
import top.easytier.miuix.data.model.Ipv4Inet
import top.easytier.miuix.data.model.Ipv6Addr
import top.easytier.miuix.data.model.Mode
import top.easytier.miuix.data.model.NatType
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
import top.easytier.miuix.jni.EasyTierTileService
import top.easytier.miuix.jni.EasyTierVpnService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealNetworkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkRepository {

    companion object {
        private const val TAG = "RealNetworkRepository"
        private const val PREFS_NAME = "easytier_state"
        private const val KEY_LAST_INSTANCE = "last_instance_id"
        private const val KEY_CONFIG_SERVER_URL = "config_server_url"
        private const val KEY_CONFIG_SERVER_MACHINE_ID = "config_server_machine_id"
        private const val KEY_TILE_RUNNING = "tile_running"
    }

    val isNativeReady: Boolean get() = EasyTierJNI.isNativeLoaded

    private val statePrefs
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否有任一网络实例在运行（供快捷磁贴查询） */
    val isAnyNetworkRunning: Boolean
        get() = _instances.value.values.any { it.running }

    /** 当前运行实例的 instanceId，无则 null */
    val runningInstanceId: String?
        get() = _instances.value.values.firstOrNull { it.running }?.instanceId

    /** 磁贴等外部入口使用的启动配置：
     *  优先取上一次运行的配置；无运行记录时使用最后创建的配置 */
    fun getLastRunConfig(): NetworkConfig? {
        val lastId = statePrefs.getString(KEY_LAST_INSTANCE, null)
        val configs = _configs.value
        return configs.firstOrNull { it.instanceId == lastId }
            ?: configs.lastOrNull()
    }

    // ---------- 配置服务器客户端 ----------

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _configServerConnected = MutableStateFlow(false)

    /** 配置服务器客户端是否已连接（未启用时为 false） */
    val configServerConnected: StateFlow<Boolean> = _configServerConnected.asStateFlow()

    @Volatile
    private var configServerWatchJob: Job? = null

    /** 当前已保存的配置服务器 URL，未配置返回 null */
    fun getConfigServerUrl(): String? = statePrefs.getString(KEY_CONFIG_SERVER_URL, null)

    /**
     * 连接配置服务器。远程配置由核心在 FFI 层直接应用/删除，
     * 回调事件仅用于日志与状态刷新。返回 null 表示启动成功，否则为错误信息。
     */
    fun connectConfigServer(url: String): String? {
        val machineId = getOrCreateConfigServerMachineId()
        val result = try {
            EasyTierJNI.startConfigServerClient(
                url,
                null,
                machineId,
                false,
            ) { eventJson ->
                Log.i(TAG, "ConfigServer event: $eventJson")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start config server client", e)
            e.message
        }
        if (result != 0) {
            return EasyTierJNI.getLastError() ?: "start failed ($result)"
        }
        statePrefs.edit().putString(KEY_CONFIG_SERVER_URL, url).apply()
        watchConfigServerConnection()
        return null
    }

    /** 断开并清除配置服务器配置 */
    fun disconnectConfigServer() {
        configServerWatchJob?.cancel()
        configServerWatchJob = null
        try {
            EasyTierJNI.stopConfigServerClient()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop config server client", e)
        }
        statePrefs.edit().remove(KEY_CONFIG_SERVER_URL).apply()
        _configServerConnected.value = false
    }

    private fun getOrCreateConfigServerMachineId(): String {
        statePrefs.getString(KEY_CONFIG_SERVER_MACHINE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        statePrefs.edit().putString(KEY_CONFIG_SERVER_MACHINE_ID, id).apply()
        return id
    }

    private fun watchConfigServerConnection() {
        configServerWatchJob?.cancel()
        configServerWatchJob = repoScope.launch {
            while (isActive) {
                _configServerConnected.value = try {
                    EasyTierJNI.isConfigServerClientConnected()
                } catch (e: Exception) {
                    Log.w(TAG, "isConfigServerClientConnected failed", e)
                    false
                }
                delay(3000)
            }
        }
    }

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

    /** 同步磁贴状态：实例运行态变化时落盘（磁贴读取同一份状态）并请求刷新磁贴 */
    private fun setTileRunning(running: Boolean) {
        statePrefs.edit().putBoolean(KEY_TILE_RUNNING, running).commit()
        EasyTierTileService.requestStateUpdate(context)
    }

    /** 磁贴读取的持久化运行状态（与应用内实例状态同源） */
    val isTileRunning: Boolean
        get() = statePrefs.getBoolean(KEY_TILE_RUNNING, false)

    init {
        loadConfigsFromFile()
        // 核心随进程消亡：进程重启后持久化的运行状态必然已失效
        setTileRunning(false)
        // 已配置过配置服务器则自动重连
        getConfigServerUrl()?.let { url ->
            val error = connectConfigServer(url)
            if (error != null) Log.w(TAG, "Auto reconnect config server failed: $error")
        }
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

    /** 启动网络实例。返回 null 表示成功，否则为错误信息（供 UI 展示启动结果）。 */
    override suspend fun runNetworkInstance(config: NetworkConfig): String? {
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
            return "Native library not loaded"
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
                statePrefs.edit().putString(KEY_LAST_INSTANCE, config.instanceId).apply()
                _instances.value = _instances.value.toMutableMap().apply {
                    put(config.instanceId, NetworkInstance(
                        instanceId = config.instanceId,
                        name = config.instanceName.ifEmpty { config.networkName },
                        running = true,
                    ))
                }
                startPolling(config.instanceId, config.instanceName.ifEmpty { config.networkName })
                setTileRunning(true)
                return null
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
                setTileRunning(false)
                return error ?: "Unknown error"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception running instance", e)
            return e.message ?: "Exception"
        }
    }

    override suspend fun stopNetworkInstance(instanceId: String) {
        try {
            _clientRunning.value = false
            // Stop EasyTier first so it can gracefully release the TUN fd.
            // 优先用上游新增的 deleteNetworkInstance 精确停止本实例，不影响其他实例
            val runningName = _runningInstanceId
            Log.i(TAG, "stopNetworkInstance: runningName=$runningName")
            if (runningName != null) {
                val rc = EasyTierJNI.deleteInstance(runningName)
                Log.i(TAG, "deleteInstance($runningName) -> $rc, error=${EasyTierJNI.getLastError()}")
            } else {
                EasyTierJNI.stopAllInstances()
            }
            setTileRunning(false)
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

    /**
     * 磁贴等外部入口的异步启停入口：跑在与进程同寿的 repoScope 上，
     * 避免 TileService 被系统回收时取消执行中的启停（对齐上游 #2511 的教训）。
     */
    fun startNetworkInstanceAsync(config: NetworkConfig) {
        // 磁贴/后台启动：立即拉起前台服务占住进程——核心启动期间进程无任何系统可见组件，
        // 会被系统回收，导致轮询线程死亡、VPN 永远等不到参数
        try {
            androidx.core.content.ContextCompat.startForegroundService(
                context,
                Intent(context, EasyTierVpnService::class.java).apply {
                    putExtra(EasyTierVpnService.EXTRA_HOLD, true)
                    putExtra("instance_name", config.instanceName.ifEmpty { config.networkName })
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Hold service start failed", e)
        }
        repoScope.launch {
            val error = runNetworkInstance(config)
            if (error != null) {
                // 启动失败：释放 hold 前台服务，磁贴/界面随之回到停止态
                Log.w(TAG, "startNetworkInstanceAsync failed: $error")
                stopVpnService()
            }
        }
    }

    fun stopNetworkInstanceAsync(instanceId: String) {
        Log.i(TAG, "stopNetworkInstanceAsync: $instanceId")
        repoScope.launch { stopNetworkInstance(instanceId) }
    }

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
                        } else {
                            // 核心中实例已消失（异常退出）：app 与磁贴同步为停止态
                            Log.w(TAG, "Poll: instance disappeared, marking stopped")
                            _clientRunning.value = false
                            setTileRunning(false)
                            _instances.value = _instances.value.toMutableMap().apply {
                                this[instanceId]?.let { put(instanceId, it.copy(running = false, detail = null)) }
                            }
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
            androidx.core.content.ContextCompat.startForegroundService(context, vpnIntent)
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
            // 先投递停止指令：服务自行 cleanup 并 stopSelf
            // （进程持有 FGS 时不受后台启动限制；SystemUI 持有 binding 时 stopService 无法销毁服务）
            context.startService(
                Intent(context, EasyTierVpnService::class.java).apply {
                    putExtra("stop_vpn", true)
                }
            )
            // 双保险：无绑定连接时立即销毁
            context.stopService(Intent(context, EasyTierVpnService::class.java))
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
                // 核心在实例未就绪时会返回 running=false + error_msg（此时其余字段都是默认值）。
                // 注意 pbjson 会省略值为 false 的布尔键，所以缺键即代表未运行。
                running = detail.running,
                errorMsg = detail.errorMsg ?: "",
                detail = detail,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing network info", e)
            null
        }
    }

    /**
     * 解析一条上游运行事件。
     *
     * 事件 JSON 只有两个键：`time`（chrono 的 RFC3339 字符串）与 `event`
     * （单键对象，键名是 `GlobalCtxEvent` 变体名）。事件级别从未被序列化，
     * 只能由类型名与 payload 推导，所以不能像以前那样去读 `level`/`peer_id`/`timestamp`。
     */
    private fun parseEvent(raw: String): EventInfo {
        return try {
            val obj = JSONObject(raw)
            val eventObj = obj.optJSONObject("event")
            val type = eventObj?.keys()?.asSequence()?.firstOrNull()
                ?: obj.keys().asSequence().firstOrNull { it != "time" && it != "event" }
                ?: ""
            val detail = eventObj?.optJSONObject(type) ?: obj.optJSONObject(type)
            EventInfo(
                level = deriveEventLevel(type, detail),
                type = type,
                timestamp = parseRfc3339Millis(obj.optString("time", "")),
                raw = raw,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse event: $raw", e)
            EventInfo(raw = raw)
        }
    }

    /** 事件等级由 `GlobalCtxEvent` 变体名推导（见 easytier/src/common/global_ctx.rs）。 */
    private fun deriveEventLevel(type: String, detail: JSONObject?): EventLevel {
        val payloadError = detail?.optString("error", "").orEmpty()
        return when {
            type.endsWith("Error") || type.endsWith("Failed") -> EventLevel.ERROR
            payloadError.isNotEmpty() && payloadError != "null" -> EventLevel.ERROR
            type.endsWith("Conflicted") ||
                type.endsWith("Removed") ||
                type.endsWith("Disconnected") -> EventLevel.WARN
            else -> EventLevel.INFO
        }
    }

    /** 解析 RFC3339 时间戳为 epoch 毫秒；解析失败返回 0。 */
    private fun parseRfc3339Millis(value: String): Long {
        if (value.isEmpty()) return 0
        return try {
            java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(value)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                0
            }
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
                // 上游 events 是 repeated string，元素形如
                // {"time":"<RFC3339>","event":{"<GlobalCtxEvent 变体名>":{…}}}
                val raw = eventsArr.optString(i, "")
                if (raw.isEmpty()) continue
                events.add(raw)
                parsedEvents.add(parseEvent(raw))
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

        // Parse interface IPv4s
        val interfaceIpv4s = mutableListOf<Ipv4Addr>()
        ips?.optJSONArray("interface_ipv4s")?.let { arr ->
            for (i in 0 until arr.length()) {
                val addr = arr.optJSONObject(i)?.optInt("addr", 0) ?: 0
                if (addr != 0) interfaceIpv4s.add(Ipv4Addr(addr))
            }
        }

        // Parse public IPv6
        val publicIpv6 = ips?.optJSONObject("public_ipv6")?.let {
            Ipv6Addr(
                part1 = it.optLong("part1", 0),
                part2 = it.optLong("part2", 0),
                part3 = it.optLong("part3", 0),
                part4 = it.optLong("part4", 0),
            )
        }

        // Parse interface IPv6s
        val interfaceIpv6s = mutableListOf<Ipv6Addr>()
        ips?.optJSONArray("interface_ipv6s")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj6 = arr.optJSONObject(i) ?: continue
                interfaceIpv6s.add(Ipv6Addr(
                    part1 = obj6.optLong("part1", 0),
                    part2 = obj6.optLong("part2", 0),
                    part3 = obj6.optLong("part3", 0),
                    part4 = obj6.optLong("part4", 0),
                ))
            }
        }

        val listeners = mutableListOf<Url>()
        val listenersArr = obj.optJSONArray("listeners")
        if (listenersArr != null) {
            for (i in 0 until listenersArr.length()) {
                listenersArr.optJSONObject(i)?.optString("url")?.let { listeners.add(Url(it)) }
            }
        }

        val stunInfo = obj.optJSONObject("stun_info")?.let {
            // udp_nat_type / tcp_nat_type 是枚举名字符串，且 Unknown 时缺键
            StunInfo(
                udpNatType = NatType.fromWire(it.optString("udp_nat_type", null)),
                tcpNatType = NatType.fromWire(it.optString("tcp_nat_type", null)),
                lastUpdateTime = it.optLong("last_update_time", 0),
            )
        } ?: StunInfo()

        return NodeInfo(
            virtualIpv4 = virtualIpv4,
            hostname = hostname,
            version = version,
            publicIpv4 = publicIpv4,
            interfaceIpv4s = interfaceIpv4s,
            publicIpv6 = publicIpv6,
            interfaceIpv6s = interfaceIpv6s,
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
            pathLatencyUs = obj.optLong("path_latency", 0),
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
        val directlyConnected = mutableListOf<String>()
        obj.optJSONArray("directly_connected_conns")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { directlyConnected.add(uuidStringOf(it)) }
            }
        }
        return PeerInfo(
            peerId = obj.optLong("peer_id", 0),
            conns = conns,
            defaultConnId = obj.optJSONObject("default_conn_id")?.let { uuidStringOf(it) } ?: "",
            directlyConnectedConns = directlyConnected.filter { it.isNotEmpty() },
        )
    }

    /**
     * 把 `common.UUID`（part1..part4，各 uint32）渲染成核心用的规范 UUID 字符串。
     *
     * 对齐 easytier-proto 的 `impl From<Uuid> for uuid::Uuid`：
     * `from_u64_pair((part1 << 32) | part2, (part3 << 32) | part4)`，
     * 因此前 8 字节是 (part1,part2) 的大端序，后 8 字节是 (part3,part4) 的大端序。
     * `PeerConnInfo.conn_id` 就是同一套 `to_string()` 结果，两边才能对上。
     */
    private fun uuidStringOf(obj: JSONObject): String {
        val hi = ((obj.optLong("part1", 0) and 0xFFFFFFFFL) shl 32) or
            (obj.optLong("part2", 0) and 0xFFFFFFFFL)
        val lo = ((obj.optLong("part3", 0) and 0xFFFFFFFFL) shl 32) or
            (obj.optLong("part4", 0) and 0xFFFFFFFFL)
        if (hi == 0L && lo == 0L) return ""
        val hex = "%016x%016x".format(hi, lo)
        return buildString {
            append(hex, 0, 8).append('-')
            append(hex, 8, 12).append('-')
            append(hex, 12, 16).append('-')
            append(hex, 16, 20).append('-')
            append(hex, 20, 32)
        }
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
            // pbjson 省略 0.0，缺键即 0，哨兵值没有意义
            lossRate = obj.optDouble("loss_rate", 0.0).toFloat(),
            isClient = obj.optBoolean("is_client", false),
            secureAuthLevel = obj.optString("secure_auth_level", ""),
            peerIdentityType = obj.optString("peer_identity_type", ""),
            networkName = obj.optString("network_name", ""),
            isClosed = obj.optBoolean("is_closed", false),
        )
    }

    private fun generateTomlConfig(config: NetworkConfig): String {
        return buildString {
            // Match the exact format from easytier-gui's gen_config() + dump()
            // 注意：TOML 的顶层键必须写在任何 [table] 头之前。
            // 这里只写 easytier-core/src/config/toml.rs 的 struct Config 里真实存在的键
            // （该结构体未开 deny_unknown_fields，写错的键会被静默忽略；但类型不符或
            //   deny_unknown_fields 的子表会直接让整份配置解析失败）。
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

            // 对外公布的监听地址（NAT 后的映射地址）
            val mappedListeners = config.mappedListeners.filter { it.isNotBlank() }
            if (mappedListeners.isNotEmpty()) {
                appendLine("mapped_listeners = [")
                mappedListeners.forEach { appendLine("    \"$it\",") }
                appendLine("]")
            }

            // 出口节点（上游字段：顶层 exit_nodes，元素为 IP）
            val exitNodes = config.exitNodes.filter { it.isNotBlank() }
            if (exitNodes.isNotEmpty()) {
                appendLine("exit_nodes = [")
                exitNodes.forEach { node -> appendLine("    \"$node\",") }
                appendLine("]")
            }

            // 手动路由（上游字段：顶层 routes，元素为 IPv4 CIDR）。
            // 上游没有 enable_manual_routes 这个 TOML 键，它只是管理 API 的门控，
            // 因此这里只把开关当作 UI 门控使用，不写出该键。
            if (config.enableManualRoutes) {
                val routes = config.routes.filter { it.isNotBlank() }
                if (routes.isNotEmpty()) {
                    appendLine("routes = [")
                    routes.forEach { appendLine("    \"$it\",") }
                    appendLine("]")
                }
            }

            // SOCKS5 代理（上游字段：顶层 socks5_proxy，URL 形式）
            if (config.enableSocks5) {
                appendLine("socks5_proxy = \"socks5://0.0.0.0:${config.socks5Port}\"")
            }

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
            // Android 的 TUN 设备由 VpnService 建立后经 setTunFd 交给核心，核心不再自行创建设备，
            // 因此该值恒为 true。上游 TOML 不允许同一张表里出现重复键，故只在此处写一次。
            flags.add("no_tun = true")
            if (!config.bindDevice) flags.add("bind_device = false")
            if (config.devName.isNotEmpty()) flags.add("dev_name = \"${config.devName}\"")
            config.mtu?.let { flags.add("mtu = $it") }
            if (config.latencyFirst) flags.add("latency_first = true")
            if (config.disableIpv6) flags.add("enable_ipv6 = false")
            if (config.disableP2p) flags.add("disable_p2p = true")
            if (config.p2pOnly) flags.add("p2p_only = true")
            if (config.lazyP2p) flags.add("lazy_p2p = true")
            if (config.needP2p) flags.add("need_p2p = true")
            if (config.enableExitNode) flags.add("enable_exit_node = true")
            if (!config.multiThread) flags.add("multi_thread = false")
            if (config.enableKcpProxy) flags.add("enable_kcp_proxy = true")
            if (config.disableKcpInput) flags.add("disable_kcp_input = true")
            if (config.enableQuicProxy) flags.add("enable_quic_proxy = true")
            if (config.disableQuicInput) flags.add("disable_quic_input = true")
            if (config.disableEncryption) flags.add("enable_encryption = false")
            if (config.disableTcpHolePunching) flags.add("disable_tcp_hole_punching = true")
            if (config.disableUdpHolePunching) flags.add("disable_udp_hole_punching = true")
            if (config.disableSymHolePunching) flags.add("disable_sym_hole_punching = true")
            if (config.disableUpnp) flags.add("disable_upnp = true")
            if (config.relayAllPeerRpc) flags.add("relay_all_peer_rpc = true")
            if (config.enableUdpBroadcastRelay) flags.add("enable_udp_broadcast_relay = true")
            if (config.proxyForwardBySystem) flags.add("proxy_forward_by_system = true")
            if (config.useSmoltcp) flags.add("use_smoltcp = true")
            // 上游类型是单个空格分隔的字符串（默认 "*"），不是数组
            if (config.enableRelayNetworkWhitelist) {
                flags.add("relay_network_whitelist = \"${config.relayNetworkWhitelist}\"")
            }
            // 上游把 u64 序列化成带引号的字符串
            config.instanceRecvBpsLimit?.let { flags.add("instance_recv_bps_limit = \"$it\"") }
            if (config.encryptionAlgorithm != EncryptionAlgorithm.AesGcm) {
                flags.add("encryption_algorithm = \"${config.encryptionAlgorithm.value}\"")
            }
            if (config.dataCompressAlgo != CompressionAlgo.None) {
                flags.add("data_compress_algo = \"${config.dataCompressAlgo.value}\"")
            }
            // Magic DNS 在上游 flags 中对应 accept_dns
            if (config.enableMagicDns) flags.add("accept_dns = true")
            if (config.enablePrivateMode) flags.add("private_mode = true")
            if (flags.isNotEmpty()) {
                appendLine()
                appendLine("[flags]")
                flags.forEach { flag -> appendLine(flag) }
            }

            // VPN Portal：上游 VpnPortalConfig 带 #[serde(deny_unknown_fields)]，
            // 且没有 client_cidr —— 写旧键会让整份配置解析失败。
            // 客户端必须用 [[vpn_portal_config.clients]] 的具名模型；
            // clients 为空时只写 wireguard_listen 也是合法的（enabled 缺省视为启用）。
            if (config.enableVpnPortal) {
                appendLine()
                appendLine("[vpn_portal_config]")
                appendLine("wireguard_listen = \"0.0.0.0:${config.vpnPortalListenPort}\"")
                config.vpnPortalClients
                    .filter { it.name.isNotBlank() && it.virtualIp.isNotBlank() }
                    .forEach { client ->
                        appendLine()
                        appendLine("[[vpn_portal_config.clients]]")
                        appendLine("name = \"${client.name}\"")
                        appendLine("virtual_ip = \"${client.virtualIp}\"")
                    }
            }

            // Proxy CIDRs：上游 ProxyNetworkConfig.allow 是 Option<Vec<String>>，
            // 写 `allow = true` 会因类型不符导致整份配置解析失败；且该字段在核心里
            // 没有任何读取点，所以这里只写 cidr。
            config.proxyCidrs.filter { it.isNotBlank() }.forEach { cidr ->
                appendLine()
                appendLine("[[proxy_network]]")
                appendLine("cidr = \"$cidr\"")
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
