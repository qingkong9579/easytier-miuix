package top.easytier.miuix.ui.screens.config

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.easytier.miuix.R
import top.easytier.miuix.data.model.CompressionAlgo
import top.easytier.miuix.data.model.DnsZone
import top.easytier.miuix.data.model.EncryptionAlgorithm
import top.easytier.miuix.data.model.NetworkConfig
import top.easytier.miuix.data.model.VpnPortalClient
import top.easytier.miuix.ui.components.BoolFlag
import top.easytier.miuix.ui.components.BoolFlagGrid
import top.easytier.miuix.ui.components.DnsRecordsEditor
import top.easytier.miuix.ui.components.ListenerPicker
import top.easytier.miuix.ui.components.PortForwardEditor
import top.easytier.miuix.ui.components.UrlListInput
import top.easytier.miuix.ui.components.VpnPortalClientEditor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 上游 `FEC_MAX_OVERHEAD`（`easytier-core/src/packet/mod.rs`）：
 * 开启 UDP FEC 后核心会从 `mtu` 里扣掉这么多字节留给修复帧头。
 */
private const val FEC_MAX_OVERHEAD = 16L

/** `StandardAeadTail` 的认证尾开销；开加密时同样从 `mtu` 里扣掉。 */
private const val AEAD_OVERHEAD = 16L

/**
 * 一个「配置项 ↔ 模型字段」的映射描述，供 [BoolFlagGrid] 使用。
 *
 * 这些开关此前只存在于模型里、从不写进 TOML，等于界面上的空壳；
 * 用描述表把「渲染」和「读写」绑在一起，新增一项只需加一行。
 */
private data class FlagSpec(
    val key: String,
    val labelRes: Int,
    val get: (NetworkConfig) -> Boolean,
    val set: (NetworkConfig, Boolean) -> NetworkConfig,
)

/** 之前未接线的旗标：全部对应上游 `[flags]` 里真实存在的键。 */
private val advancedFlagSpecs = listOf(
    FlagSpec("p2pOnly", R.string.config_p2p_only, { it.p2pOnly }, { c, v -> c.copy(p2pOnly = v) }),
    FlagSpec("lazyP2p", R.string.config_lazy_p2p, { it.lazyP2p }, { c, v -> c.copy(lazyP2p = v) }),
    FlagSpec("needP2p", R.string.config_need_p2p, { it.needP2p }, { c, v -> c.copy(needP2p = v) }),
    FlagSpec("relayAllPeerRpc", R.string.config_relay_all_peer_rpc, { it.relayAllPeerRpc }, { c, v -> c.copy(relayAllPeerRpc = v) }),
    FlagSpec("disableKcpInput", R.string.config_disable_kcp_input, { it.disableKcpInput }, { c, v -> c.copy(disableKcpInput = v) }),
    FlagSpec("enableQuicProxy", R.string.config_enable_quic_proxy, { it.enableQuicProxy }, { c, v -> c.copy(enableQuicProxy = v) }),
    FlagSpec("disableQuicInput", R.string.config_disable_quic_input, { it.disableQuicInput }, { c, v -> c.copy(disableQuicInput = v) }),
    FlagSpec("disableUpnp", R.string.config_disable_upnp, { it.disableUpnp }, { c, v -> c.copy(disableUpnp = v) }),
    FlagSpec("enableUdpBroadcastRelay", R.string.config_udp_broadcast_relay, { it.enableUdpBroadcastRelay }, { c, v -> c.copy(enableUdpBroadcastRelay = v) }),
    FlagSpec("disableSymHolePunching", R.string.config_disable_sym_hole_punching, { it.disableSymHolePunching }, { c, v -> c.copy(disableSymHolePunching = v) }),
    FlagSpec("proxyForwardBySystem", R.string.config_proxy_forward_by_system, { it.proxyForwardBySystem }, { c, v -> c.copy(proxyForwardBySystem = v) }),
    FlagSpec("useSmoltcp", R.string.config_use_smoltcp, { it.useSmoltcp }, { c, v -> c.copy(useSmoltcp = v) }),
    FlagSpec("bindDevice", R.string.config_bind_device, { it.bindDevice }, { c, v -> c.copy(bindDevice = v) }),
)

/** 表单全部本地字段快照，用于脏状态检测 */private data class ConfigFormState(
    val networkName: String,
    val networkSecret: String,
    val instanceName: String,
    val virtualIpv4: String,
    val dhcp: Boolean,
    val peerUrls: List<String>,
    val listenerUrls: List<String>,
    val mappedListeners: List<String>,
    val proxyCidrs: List<String>,
    val exitNodes: List<String>,
    val routes: List<String>,
    val enableManualRoutes: Boolean,
    val relayNetworkWhitelist: String,
    val hostname: String,
    val devName: String,
    val mtu: String,
    val instanceRecvBpsLimit: String,
    val latencyFirst: Boolean,
    val showAdvanced: Boolean,
    val encryptionAlgorithm: EncryptionAlgorithm,
    val dataCompressAlgo: CompressionAlgo,
    val advancedFlags: Map<String, Boolean>,
    val disableIpv6: Boolean,
    val enableKcpProxy: Boolean,
    val disableP2p: Boolean,
    val enableExitNode: Boolean,
    val multiThread: Boolean,
    val enableMagicDns: Boolean,
    val enablePrivateMode: Boolean,
    val disableEncryption: Boolean,
    val disableTcpHolePunching: Boolean,
    val disableUdpHolePunching: Boolean,
    val enableVpnPortal: Boolean,
    val vpnPortalListenPort: String,
    val vpnPortalClients: List<VpnPortalClient>,
    val enableSocks5: Boolean,
    val socks5Port: String,
    val enableUdpFec: Boolean,
    val enableUdpFecKcp: Boolean,
    val enableUdpFecQuic: Boolean,
    val dnsDomain: String,
    val dnsZones: List<DnsZone>,
)

@Composable
fun ConfigScreen(
    instanceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val saveRunState by viewModel.saveRun.collectAsStateWithLifecycle()

    // 等待配置加载完成再渲染表单，避免首帧用空/旧数据初始化本地字段
    var loadGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(instanceId) {
        viewModel.loadConfig(instanceId)
        loadGeneration++
    }
    if (loadGeneration == 0) {
        Column(modifier = modifier.fillMaxSize()) {}
        return
    }

    // Local form state synced from config
    var networkName by remember(instanceId, loadGeneration) { mutableStateOf(config.networkName) }
    var networkSecret by remember(instanceId, loadGeneration) { mutableStateOf(config.networkSecret) }
    var instanceName by remember(instanceId, loadGeneration) { mutableStateOf(config.instanceName) }
    var virtualIpv4 by remember(instanceId, loadGeneration) { mutableStateOf(config.virtualIpv4) }
    var dhcp by remember(instanceId, loadGeneration) { mutableStateOf(config.dhcp) }
    var peerUrls by remember(instanceId, loadGeneration) { mutableStateOf(config.peerUrls) }
    var listenerUrls by remember(instanceId, loadGeneration) { mutableStateOf(config.listenerUrls) }
    var mappedListeners by remember(instanceId, loadGeneration) { mutableStateOf(config.mappedListeners) }
    var proxyCidrs by remember(instanceId, loadGeneration) { mutableStateOf(config.proxyCidrs) }
    var routes by remember(instanceId, loadGeneration) { mutableStateOf(config.routes) }
    var enableManualRoutes by remember(instanceId, loadGeneration) { mutableStateOf(config.enableManualRoutes) }
    var enableRelayWhitelist by remember(instanceId, loadGeneration) { mutableStateOf(config.enableRelayNetworkWhitelist) }
    var relayWhitelist by remember(instanceId, loadGeneration) { mutableStateOf(config.relayNetworkWhitelist) }
    var hostname by remember(instanceId, loadGeneration) { mutableStateOf(config.hostname ?: "") }
    var devName by remember(instanceId, loadGeneration) { mutableStateOf(config.devName) }
    var mtu by remember(instanceId, loadGeneration) { mutableStateOf(config.mtu?.toString() ?: "") }
    var recvBpsLimit by remember(instanceId, loadGeneration) { mutableStateOf(config.instanceRecvBpsLimit?.toString() ?: "") }
    var latencyFirst by remember(instanceId, loadGeneration) { mutableStateOf(config.latencyFirst) }
    var showAdvanced by remember(instanceId, loadGeneration) { mutableStateOf(config.advancedSettings) }
    var encryptionAlgorithm by remember(instanceId, loadGeneration) { mutableStateOf(config.encryptionAlgorithm) }
    var dataCompressAlgo by remember(instanceId, loadGeneration) { mutableStateOf(config.dataCompressAlgo) }
    var showPortForwards by remember { mutableStateOf(false) }
    var showVpnPortal by remember { mutableStateOf(false) }
    var showSocks5 by remember { mutableStateOf(false) }

    // 之前只存在于模型、从不写入 TOML 的旗标，现在统一由 BoolFlagGrid 驱动
    var advancedFlags by remember(instanceId, loadGeneration) {
        mutableStateOf(advancedFlagSpecs.associate { it.key to it.get(config) })
    }

    // Boolean flags
    var disableIpv6 by remember(instanceId, loadGeneration) { mutableStateOf(config.disableIpv6) }
    var enableKcpProxy by remember(instanceId, loadGeneration) { mutableStateOf(config.enableKcpProxy) }
    var disableP2p by remember(instanceId, loadGeneration) { mutableStateOf(config.disableP2p) }
    var enableExitNode by remember(instanceId, loadGeneration) { mutableStateOf(config.enableExitNode) }
    var multiThread by remember(instanceId, loadGeneration) { mutableStateOf(config.multiThread) }
    var enableMagicDns by remember(instanceId, loadGeneration) { mutableStateOf(config.enableMagicDns) }
    var enablePrivateMode by remember(instanceId, loadGeneration) { mutableStateOf(config.enablePrivateMode) }
    var disableEncryption by remember(instanceId, loadGeneration) { mutableStateOf(config.disableEncryption) }
    var disableTcpHolePunching by remember(instanceId, loadGeneration) { mutableStateOf(config.disableTcpHolePunching) }
    var disableUdpHolePunching by remember(instanceId, loadGeneration) { mutableStateOf(config.disableUdpHolePunching) }

    // VPN Portal
    var enableVpnPortal by remember(instanceId, loadGeneration) { mutableStateOf(config.enableVpnPortal) }
    var vpnPortalListenPort by remember(instanceId, loadGeneration) { mutableStateOf(config.vpnPortalListenPort.toString()) }
    var vpnPortalClients by remember(instanceId, loadGeneration) { mutableStateOf(config.vpnPortalClients) }

    // SOCKS5
    var enableSocks5 by remember(instanceId, loadGeneration) { mutableStateOf(config.enableSocks5) }
    var socks5Port by remember(instanceId, loadGeneration) { mutableStateOf(config.socks5Port.toString()) }

    // Exit nodes
    var exitNodes by remember(instanceId, loadGeneration) { mutableStateOf(config.exitNodes) }

    // 上游 2.7.0 之后：UDP FEC / Magic DNS 静态记录
    var enableUdpFec by remember(instanceId, loadGeneration) { mutableStateOf(config.enableUdpFec) }
    var enableUdpFecKcp by remember(instanceId, loadGeneration) { mutableStateOf(config.enableUdpFecKcp) }
    var enableUdpFecQuic by remember(instanceId, loadGeneration) { mutableStateOf(config.enableUdpFecQuic) }
    var showMagicDns by remember(instanceId, loadGeneration) { mutableStateOf(false) }
    var dnsDomain by remember(instanceId, loadGeneration) { mutableStateOf(config.dnsDomain) }
    var dnsZones by remember(instanceId, loadGeneration) { mutableStateOf(config.dnsZones) }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun collectForm() = ConfigFormState(
        networkName = networkName,
        networkSecret = networkSecret,
        instanceName = instanceName,
        virtualIpv4 = virtualIpv4,
        dhcp = dhcp,
        peerUrls = peerUrls,
        listenerUrls = listenerUrls,
        mappedListeners = mappedListeners,
        proxyCidrs = proxyCidrs,
        exitNodes = exitNodes,
        routes = routes,
        enableManualRoutes = enableManualRoutes,
        relayNetworkWhitelist = relayWhitelist,
        hostname = hostname,
        devName = devName,
        mtu = mtu,
        instanceRecvBpsLimit = recvBpsLimit,
        latencyFirst = latencyFirst,
        showAdvanced = showAdvanced,
        encryptionAlgorithm = encryptionAlgorithm,
        dataCompressAlgo = dataCompressAlgo,
        advancedFlags = advancedFlags,
        disableIpv6 = disableIpv6,
        enableKcpProxy = enableKcpProxy,
        disableP2p = disableP2p,
        enableExitNode = enableExitNode,
        multiThread = multiThread,
        enableMagicDns = enableMagicDns,
        enablePrivateMode = enablePrivateMode,
        disableEncryption = disableEncryption,
        disableTcpHolePunching = disableTcpHolePunching,
        disableUdpHolePunching = disableUdpHolePunching,
        enableVpnPortal = enableVpnPortal,
        vpnPortalListenPort = vpnPortalListenPort,
        vpnPortalClients = vpnPortalClients,
        enableSocks5 = enableSocks5,
        socks5Port = socks5Port,
        enableUdpFec = enableUdpFec,
        enableUdpFecKcp = enableUdpFecKcp,
        enableUdpFecQuic = enableUdpFecQuic,
        dnsDomain = dnsDomain,
        dnsZones = dnsZones,
    )

    val initialForm = remember(instanceId, loadGeneration) { collectForm() }
    val initialPortForwards = remember(instanceId, loadGeneration) { config.portForwards }
    val dirty = collectForm() != initialForm || config.portForwards != initialPortForwards

    // Sync form state back to viewModel config
    fun syncToConfig() {
        viewModel.updateConfig { c ->
            // 先把 BoolFlagGrid 驱动的旗标逐项落回模型
            var next = c
            advancedFlagSpecs.forEach { spec ->
                next = spec.set(next, advancedFlags[spec.key] ?: spec.get(c))
            }
            next.copy(
                networkName = networkName,
                networkSecret = networkSecret,
                instanceName = instanceName,
                virtualIpv4 = virtualIpv4,
                dhcp = dhcp,
                peerUrls = peerUrls,
                listenerUrls = listenerUrls,
                mappedListeners = mappedListeners,
                proxyCidrs = proxyCidrs,
                routes = routes,
                enableManualRoutes = enableManualRoutes,
                enableRelayNetworkWhitelist = enableRelayWhitelist,
                relayNetworkWhitelist = relayWhitelist.ifBlank { "*" },
                hostname = hostname.ifEmpty { null },
                devName = devName,
                mtu = mtu.toIntOrNull(),
                instanceRecvBpsLimit = recvBpsLimit.toLongOrNull(),
                latencyFirst = latencyFirst,
                // 之前漏了这一行：只展开/收起高级区就会被判成「已修改」，
                // 而且展开状态永远存不下来。
                advancedSettings = showAdvanced,
                encryptionAlgorithm = encryptionAlgorithm,
                dataCompressAlgo = dataCompressAlgo,
                disableIpv6 = disableIpv6,
                enableKcpProxy = enableKcpProxy,
                disableP2p = disableP2p,
                enableExitNode = enableExitNode,
                multiThread = multiThread,
                enableMagicDns = enableMagicDns,
                enablePrivateMode = enablePrivateMode,
                disableEncryption = disableEncryption,
                disableTcpHolePunching = disableTcpHolePunching,
                disableUdpHolePunching = disableUdpHolePunching,
                enableVpnPortal = enableVpnPortal,
                vpnPortalListenPort = vpnPortalListenPort.toIntOrNull() ?: 22022,
                vpnPortalClients = vpnPortalClients,
                enableSocks5 = enableSocks5,
                socks5Port = socks5Port.toIntOrNull() ?: 1080,
                exitNodes = exitNodes,
                enableUdpFec = enableUdpFec,
                enableUdpFecKcp = enableUdpFecKcp,
                enableUdpFecQuic = enableUdpFecQuic,
                dnsDomain = dnsDomain.trim(),
                dnsZones = dnsZones,
            )
        }
    }

    // ---------- 校验 ----------
    var attemptedSave by remember(instanceId, loadGeneration) { mutableStateOf(false) }

    /** 逐条校验带前缀长的 IPv4 CIDR 列表，返回第一个非法项（合法则 null）。 */
    fun firstInvalidCidr(values: List<String>): String? =
        values.map { it.trim() }.firstOrNull { it.isNotBlank() && !isValidIpv4Cidr(it) }

    /** 逐条校验裸 IPv4（上游 exit_nodes 是 Vec<IpAddr>，不接受 CIDR）。 */
    fun firstInvalidIp(values: List<String>): String? =
        values.map { it.trim() }.firstOrNull { it.isNotBlank() && !isValidIpv4(it) }

    fun validate(): Map<String, String> {
        val errs = mutableMapOf<String, String>()
        if (networkName.isBlank()) {
            errs["name"] = context.getString(R.string.err_network_name_required)
        }
        if (!dhcp && !isValidIpv4Cidr(virtualIpv4)) {
            errs["ipv4"] = context.getString(R.string.err_invalid_ipv4_cidr)
        }
        val mtuVal = mtu.toLongOrNull()
        if (mtu.isNotBlank() && (mtuVal == null || mtuVal !in 1280..65535)) {
            errs["mtu"] = context.getString(R.string.err_invalid_mtu)
        }
        // 上游按 cidr::Ipv4Cidr 解析这些值，格式不对会让整份 TOML 解析失败，
        // 所以必须在保存前拦下，而不是等到启动时报 serde 错误。
        if (firstInvalidCidr(proxyCidrs) != null) {
            errs["proxyCidrs"] = context.getString(R.string.err_invalid_ipv4_cidr)
        }
        if (enableManualRoutes && firstInvalidCidr(routes) != null) {
            errs["routes"] = context.getString(R.string.err_invalid_ipv4_cidr)
        }
        if (firstInvalidIp(exitNodes) != null) {
            errs["exitNodes"] = context.getString(R.string.err_invalid_ipv4)
        }
        if (recvBpsLimit.isNotBlank() && (recvBpsLimit.toLongOrNull() ?: -1L) < 0L) {
            errs["recvBpsLimit"] = context.getString(R.string.err_invalid_bps_limit)
        }
        if (enableVpnPortal) {
            val port = vpnPortalListenPort.toIntOrNull()
            if (port == null || port !in 1..65535) {
                errs["portalPort"] = context.getString(R.string.err_invalid_port)
            }
            // 上游 VpnPortalClientConfig 同时要求 name 与带前缀长的 virtual_ip
            vpnPortalClients.forEachIndexed { index, client ->
                val named = client.name.isNotBlank()
                val validIp = isValidIpv4Cidr(client.virtualIp)
                if ((named || client.virtualIp.isNotBlank()) && !(named && validIp)) {
                    errs["portalClient$index"] = context.getString(R.string.err_invalid_vpn_client)
                }
            }
        }
        if (enableSocks5) {
            val port = socks5Port.toIntOrNull()
            if (port == null || port !in 1..65535) {
                errs["socksPort"] = context.getString(R.string.err_invalid_port)
            }
        }
        // 开 FEC 后核心在 virtual_nic_config() 里做 checked_sub(FEC_MAX_OVERHEAD)，
        // 下溢会直接 panic（easytier/src/instance/virtual_nic.rs）。真实约束只是
        // mtu > 加密尾 + FEC 头，而上面的 1280 下限早已覆盖；这里仍显式拦一次，
        // 避免将来放宽下限时漏掉这个耦合。
        if (enableUdpFec) {
            val effectiveMtu = mtuVal ?: 1380L
            val overhead = FEC_MAX_OVERHEAD + if (disableEncryption) 0L else AEAD_OVERHEAD
            if (effectiveMtu <= overhead) {
                errs["fecMtu"] = context.getString(
                    R.string.err_fec_mtu_too_small,
                    (overhead + 1).toInt(),
                )
            }
        }
        // 静态 DNS 记录写错会让整份 TOML 解析失败（核心在解析阶段就 compile_dns），
        // 所以这里先拦一遍明显不完整的：有记录必须有 zone origin，记录必须有 name 和 value。
        dnsZones.firstOrNull()?.let { zone ->
            if (zone.records.isNotEmpty()) {
                if (zone.origin.isBlank()) {
                    errs["dns"] = context.getString(R.string.err_dns_origin_required)
                } else if (zone.records.any { it.name.isBlank() || it.value.isBlank() }) {
                    errs["dns"] = context.getString(R.string.err_dns_record_incomplete)
                }
            }
        }
        return errs
    }

    val errors = if (attemptedSave) validate() else emptyMap()

    // ---------- 保存并运行结果反馈 ----------
    LaunchedEffect(saveRunState) {
        if (saveRunState.finished && saveRunState.error == null) {
            onBack()
        }
    }
    if (saveRunState.finished && saveRunState.error != null) {
        OverlayDialog(
            title = stringResource(R.string.error),
            show = true,
            onDismissRequest = { viewModel.consumeSaveRun() },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(saveRunState.error ?: "", style = MiuixTheme.textStyles.body1)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.consumeSaveRun() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }

    // ---------- 返回键脏状态保护 ----------
    var showDiscardDialog by remember { mutableStateOf(false) }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher, dirty) {
        if (backDispatcher != null) {
            val callback = object : OnBackPressedCallback(dirty) {
                override fun handleOnBackPressed() {
                    showDiscardDialog = true
                }
            }
            backDispatcher.addCallback(callback)
            onDispose { callback.remove() }
        } else {
            onDispose { }
        }
    }
    if (showDiscardDialog) {
        OverlayDialog(
            title = stringResource(R.string.discard_changes_title),
            show = true,
            onDismissRequest = { showDiscardDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.discard_changes_msg), style = MiuixTheme.textStyles.body1)
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showDiscardDialog = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.keep_editing))
                    }
                    Button(
                        onClick = {
                            showDiscardDialog = false
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.discard))
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // miuix 滚动骨架：滚到边界时给触感反馈，越界用 MIUI 风格回弹。
            .scrollEndHaptic()
            .overScrollVertical(),
    ) {
        item { Spacer(Modifier.height(12.dp)) }

        // Basic Settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.config_dhcp),
                        summary = if (dhcp) stringResource(R.string.config_dhcp_enabled) else stringResource(R.string.config_dhcp_disabled),
                        checked = dhcp,
                        onCheckedChange = { dhcp = it },
                    )
                    TextField(
                        value = if (dhcp) "" else virtualIpv4,
                        onValueChange = { if (!dhcp) virtualIpv4 = it },
                        label = if (dhcp) stringResource(R.string.config_virtual_ipv4_dhcp) else stringResource(R.string.config_virtual_ipv4),
                        enabled = !dhcp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    FieldError(errors["ipv4"])
                    TextField(
                        value = networkName,
                        onValueChange = { networkName = it },
                        label = stringResource(R.string.config_network_name),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    FieldError(errors["name"])
                    TextField(
                        value = networkSecret,
                        onValueChange = { networkSecret = it },
                        label = stringResource(R.string.config_network_secret),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    TextField(
                        value = instanceName,
                        onValueChange = { instanceName = it },
                        label = stringResource(R.string.config_instance_name),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Initial Peers
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                UrlListInput(
                    label = stringResource(R.string.config_initial_peers),
                    urls = peerUrls,
                    onUrlsChange = { peerUrls = it },
                    hint = stringResource(R.string.config_peer_url_hint),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Listeners
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListenerPicker(
                    label = stringResource(R.string.config_listeners),
                    urls = listenerUrls,
                    onUrlsChange = { listenerUrls = it },
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Proxy CIDRs
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    UrlListInput(
                        label = stringResource(R.string.config_proxy_cidrs),
                        urls = proxyCidrs,
                        onUrlsChange = { proxyCidrs = it },
                        hint = stringResource(R.string.config_proxy_cidrs_hint),
                        modifier = Modifier.padding(16.dp),
                    )
                    FieldError(errors["proxyCidrs"])
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Exit Nodes
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    UrlListInput(
                        label = stringResource(R.string.config_exit_nodes),
                        urls = exitNodes,
                        onUrlsChange = { exitNodes = it },
                        hint = stringResource(R.string.config_exit_nodes_hint),
                        modifier = Modifier.padding(16.dp),
                    )
                    FieldError(errors["exitNodes"])
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Mapped Listeners：NAT 之后对外公布的地址
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                UrlListInput(
                    label = stringResource(R.string.config_mapped_listeners),
                    urls = mappedListeners,
                    onUrlsChange = { mappedListeners = it },
                    hint = stringResource(R.string.config_mapped_listeners_hint),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Manual Routes
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SwitchPreference(
                        title = stringResource(R.string.config_manual_routes),
                        summary = stringResource(R.string.config_manual_routes_summary),
                        checked = enableManualRoutes,
                        onCheckedChange = { enableManualRoutes = it },
                    )
                    if (enableManualRoutes) {
                        UrlListInput(
                            label = stringResource(R.string.config_routes_hint),
                            urls = routes,
                            onUrlsChange = { routes = it },
                            modifier = Modifier.padding(16.dp),
                        )
                        FieldError(errors["routes"])
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Advanced Settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.config_advanced_settings),
                        summary = if (showAdvanced) stringResource(R.string.config_advanced_collapse) else stringResource(R.string.config_advanced_expand),
                        onClick = { showAdvanced = !showAdvanced },
                    )
                    AnimatedVisibility(visible = showAdvanced) {
                        Column {
                            TextField(
                                value = hostname,
                                onValueChange = { hostname = it },
                                label = stringResource(R.string.config_hostname),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            TextField(
                                value = devName,
                                onValueChange = { devName = it },
                                label = stringResource(R.string.config_dev_name),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            TextField(
                                value = mtu,
                                onValueChange = { mtu = it },
                                label = stringResource(R.string.config_mtu),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            FieldError(errors["mtu"])
                            SwitchPreference(
                                title = stringResource(R.string.config_latency_first),
                                summary = stringResource(R.string.config_latency_first_summary),
                                checked = latencyFirst,
                                onCheckedChange = { latencyFirst = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_disable_ipv6),
                                summary = stringResource(R.string.config_disable_ipv6_summary),
                                checked = disableIpv6,
                                onCheckedChange = { disableIpv6 = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_enable_kcp_proxy),
                                summary = stringResource(R.string.config_enable_kcp_proxy_summary),
                                checked = enableKcpProxy,
                                onCheckedChange = { enableKcpProxy = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_disable_p2p),
                                summary = stringResource(R.string.config_disable_p2p_summary),
                                checked = disableP2p,
                                onCheckedChange = { disableP2p = it },
                            )
                            // 注意：这里不再提供 "No TUN" 开关。Android 的 TUN 由 VpnService
                            // 建立并交给核心，配置里恒为 no_tun = true；旧开关既无作用，
                            // 打开时还会写出重复键让整份配置解析失败。
                            SwitchPreference(
                                title = stringResource(R.string.config_enable_exit_node),
                                summary = stringResource(R.string.config_enable_exit_node_summary),
                                checked = enableExitNode,
                                onCheckedChange = { enableExitNode = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_multi_thread),
                                summary = stringResource(R.string.config_multi_thread_summary),
                                checked = multiThread,
                                onCheckedChange = { multiThread = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_enable_magic_dns),
                                summary = stringResource(R.string.config_enable_magic_dns_summary),
                                checked = enableMagicDns,
                                onCheckedChange = { enableMagicDns = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_private_mode),
                                summary = stringResource(R.string.config_private_mode_summary),
                                checked = enablePrivateMode,
                                onCheckedChange = { enablePrivateMode = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_disable_encryption),
                                summary = stringResource(R.string.config_disable_encryption_summary),
                                checked = disableEncryption,
                                onCheckedChange = { disableEncryption = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_disable_tcp_hole_punching),
                                summary = stringResource(R.string.config_disable_tcp_hole_punching_summary),
                                checked = disableTcpHolePunching,
                                onCheckedChange = { disableTcpHolePunching = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_disable_udp_hole_punching),
                                summary = stringResource(R.string.config_disable_udp_hole_punching_summary),
                                checked = disableUdpHolePunching,
                                onCheckedChange = { disableUdpHolePunching = it },
                            )

                            // 加密算法 / 压缩：移动端常把 chacha20 与 zstd 当作省电省流选项
                            OverlayDropdownPreference(
                                title = stringResource(R.string.config_encryption_algorithm),
                                items = EncryptionAlgorithm.entries.map { it.value },
                                selectedIndex = EncryptionAlgorithm.entries.indexOf(encryptionAlgorithm)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    EncryptionAlgorithm.entries.getOrNull(index)?.let { encryptionAlgorithm = it }
                                },
                            )
                            OverlayDropdownPreference(
                                title = stringResource(R.string.config_data_compress),
                                items = CompressionAlgo.entries.map { it.value },
                                selectedIndex = CompressionAlgo.entries.indexOf(dataCompressAlgo)
                                    .coerceAtLeast(0),
                                onSelectedIndexChange = { index ->
                                    CompressionAlgo.entries.getOrNull(index)?.let { dataCompressAlgo = it }
                                },
                            )

                            TextField(
                                value = recvBpsLimit,
                                onValueChange = { recvBpsLimit = it },
                                label = stringResource(R.string.config_recv_bps_limit),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            FieldError(errors["recvBpsLimit"])

                            SwitchPreference(
                                title = stringResource(R.string.config_relay_whitelist),
                                summary = stringResource(R.string.config_relay_whitelist_summary),
                                checked = enableRelayWhitelist,
                                onCheckedChange = { enableRelayWhitelist = it },
                            )
                            if (enableRelayWhitelist) {
                                TextField(
                                    value = relayWhitelist,
                                    onValueChange = { relayWhitelist = it },
                                    label = stringResource(R.string.config_relay_whitelist_hint),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }

                            // 上游 #2684 的 UDP 前向纠错。子项（KCP/QUIC）在主开关关闭时
                            // 单独开启无效，因此禁用而不是隐藏——让从属关系可见。
                            SwitchPreference(
                                title = stringResource(R.string.config_udp_fec),
                                summary = stringResource(R.string.config_udp_fec_summary),
                                checked = enableUdpFec,
                                onCheckedChange = {
                                    enableUdpFec = it
                                    if (!it) {
                                        enableUdpFecKcp = false
                                        enableUdpFecQuic = false
                                    }
                                },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_udp_fec_kcp),
                                summary = stringResource(R.string.config_udp_fec_kcp_summary),
                                checked = enableUdpFecKcp,
                                enabled = enableUdpFec,
                                onCheckedChange = { enableUdpFecKcp = it },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.config_udp_fec_quic),
                                summary = stringResource(R.string.config_udp_fec_quic_summary),
                                checked = enableUdpFecQuic,
                                enabled = enableUdpFec,
                                onCheckedChange = { enableUdpFecQuic = it },
                            )
                            FieldError(errors["fecMtu"])

                            // 这些旗标以前只存在于模型里、从不写进 TOML，等于界面上的空壳
                            Text(
                                text = stringResource(R.string.config_expert_flags),
                                style = MiuixTheme.textStyles.body1,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                            BoolFlagGrid(
                                flags = advancedFlagSpecs.map { spec ->
                                    BoolFlag(
                                        key = spec.key,
                                        label = stringResource(spec.labelRes),
                                        value = advancedFlags[spec.key] ?: false,
                                    )
                                },
                                onFlagChange = { key, value ->
                                    advancedFlags = advancedFlags.toMutableMap().apply { put(key, value) }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // VPN Portal
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.config_vpn_portal),
                        summary = if (showVpnPortal) stringResource(R.string.config_advanced_collapse) else stringResource(R.string.config_advanced_expand),
                        onClick = { showVpnPortal = !showVpnPortal },
                    )
                    AnimatedVisibility(visible = showVpnPortal) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.config_vpn_portal_enable),
                                summary = stringResource(R.string.config_vpn_portal_enable_summary),
                                checked = enableVpnPortal,
                                onCheckedChange = { enableVpnPortal = it },
                            )
                            if (enableVpnPortal) {
                                TextField(
                                    value = vpnPortalListenPort,
                                    onValueChange = { vpnPortalListenPort = it },
                                    label = stringResource(R.string.config_vpn_portal_port),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                FieldError(errors["portalPort"])
                                // 上游已用 [[vpn_portal_config.clients]] 取代旧的全局 client_cidr
                                Text(
                                    text = stringResource(R.string.config_vpn_portal_clients),
                                    style = MiuixTheme.textStyles.body1,
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                )
                                VpnPortalClientEditor(
                                    clients = vpnPortalClients,
                                    onClientsChange = { vpnPortalClients = it },
                                    errorFor = { index -> errors["portalClient$index"] },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Magic DNS 静态记录（上游 2.7.0 之后新增的 [dns] 段）
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.config_magic_dns_section),
                        summary = if (showMagicDns) {
                            stringResource(R.string.config_advanced_collapse)
                        } else {
                            stringResource(R.string.config_magic_dns_summary)
                        },
                        onClick = { showMagicDns = !showMagicDns },
                    )
                    AnimatedVisibility(visible = showMagicDns) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 静态记录由核心的 Magic DNS 提供，未开 accept_dns 时这些记录不生效。
                            if (!enableMagicDns) {
                                Text(
                                    text = stringResource(R.string.config_dns_magic_dns_off),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            DnsRecordsEditor(
                                domain = dnsDomain,
                                onDomainChange = { dnsDomain = it },
                                zones = dnsZones,
                                onZonesChange = { dnsZones = it },
                            )
                            FieldError(errors["dns"])
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // SOCKS5 Proxy
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.config_socks5_proxy),
                        summary = if (showSocks5) stringResource(R.string.config_advanced_collapse) else stringResource(R.string.config_advanced_expand),
                        onClick = { showSocks5 = !showSocks5 },
                    )
                    AnimatedVisibility(visible = showSocks5) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.config_socks5_enable),
                                summary = stringResource(R.string.config_socks5_enable_summary),
                                checked = enableSocks5,
                                onCheckedChange = { enableSocks5 = it },
                            )
                            if (enableSocks5) {
                                TextField(
                                    value = socks5Port,
                                    onValueChange = { socks5Port = it },
                                    label = stringResource(R.string.config_socks5_port),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                                FieldError(errors["socksPort"])
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }

        // Port Forwards
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ArrowPreference(
                        title = stringResource(R.string.config_port_forwards),
                        summary = if (showPortForwards) stringResource(R.string.config_advanced_collapse) else stringResource(R.string.config_advanced_expand),
                        onClick = { showPortForwards = !showPortForwards },
                    )
                    AnimatedVisibility(visible = showPortForwards) {
                        Column {
                            PortForwardEditor(
                                portForwards = config.portForwards,
                                onPortForwardsChange = { viewModel.updateConfig { c -> c.copy(portForwards = it) } },
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        // Action buttons
        val saving = saveRunState.saving
        fun onSave(run: Boolean) {
            attemptedSave = true
            if (validate().isNotEmpty()) return
            syncToConfig()
            if (run) {
                viewModel.saveAndRun()
            } else {
                viewModel.saveConfig { onBack() }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onSave(run = false) },
                    modifier = Modifier.weight(1f),
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.config_save))
                }
                Button(
                    onClick = { onSave(run = true) },
                    modifier = Modifier.weight(1f),
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        if (saving) stringResource(R.string.config_starting)
                        else stringResource(R.string.config_run_network)
                    )
                }
            }
        }

        item {
            Spacer(
                Modifier
                    .height(24.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun FieldError(message: String?) {
    if (!message.isNullOrEmpty()) {
        Text(
            text = message,
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

private val ipv4CidrRegex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})$""")
private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

private fun isValidIpv4(value: String): Boolean {
    val match = ipv4Regex.matchEntire(value.trim()) ?: return false
    val (a, b, c, d) = match.destructured
    return listOf(a, b, c, d).all { octet ->
        val n = octet.toIntOrNull() ?: return false
        n in 0..255
    }
}

private fun isValidIpv4Cidr(value: String): Boolean {
    val match = ipv4CidrRegex.matchEntire(value.trim()) ?: return false
    val (a, b, c, d, prefix) = match.destructured
    val octets = listOf(a, b, c, d).map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    val prefixVal = prefix.toIntOrNull() ?: return false
    return prefixVal in 0..32
}
