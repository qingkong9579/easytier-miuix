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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import top.easytier.miuix.ui.components.ListenerPicker
import top.easytier.miuix.ui.components.PortForwardEditor
import top.easytier.miuix.ui.components.UrlListInput
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 表单全部本地字段快照，用于脏状态检测 */
private data class ConfigFormState(
    val networkName: String,
    val networkSecret: String,
    val instanceName: String,
    val virtualIpv4: String,
    val dhcp: Boolean,
    val peerUrls: List<String>,
    val listenerUrls: List<String>,
    val proxyCidrs: List<String>,
    val exitNodes: List<String>,
    val hostname: String,
    val devName: String,
    val mtu: String,
    val latencyFirst: Boolean,
    val showAdvanced: Boolean,
    val disableIpv6: Boolean,
    val enableKcpProxy: Boolean,
    val disableP2p: Boolean,
    val noTun: Boolean,
    val enableExitNode: Boolean,
    val multiThread: Boolean,
    val enableMagicDns: Boolean,
    val enablePrivateMode: Boolean,
    val disableEncryption: Boolean,
    val disableTcpHolePunching: Boolean,
    val disableUdpHolePunching: Boolean,
    val enableVpnPortal: Boolean,
    val vpnPortalListenPort: String,
    val vpnPortalClientNetworkAddr: String,
    val vpnPortalClientNetworkLen: String,
    val enableSocks5: Boolean,
    val socks5Port: String,
)

@Composable
fun ConfigScreen(
    instanceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val saveRunState by viewModel.saveRun.collectAsState()

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
    var proxyCidrs by remember(instanceId, loadGeneration) { mutableStateOf(config.proxyCidrs) }
    var hostname by remember(instanceId, loadGeneration) { mutableStateOf(config.hostname ?: "") }
    var devName by remember(instanceId, loadGeneration) { mutableStateOf(config.devName) }
    var mtu by remember(instanceId, loadGeneration) { mutableStateOf(config.mtu?.toString() ?: "") }
    var latencyFirst by remember(instanceId, loadGeneration) { mutableStateOf(config.latencyFirst) }
    var showAdvanced by remember(instanceId, loadGeneration) { mutableStateOf(config.advancedSettings) }
    var showPortForwards by remember { mutableStateOf(false) }
    var showVpnPortal by remember { mutableStateOf(false) }
    var showSocks5 by remember { mutableStateOf(false) }

    // Boolean flags
    var disableIpv6 by remember(instanceId, loadGeneration) { mutableStateOf(config.disableIpv6) }
    var enableKcpProxy by remember(instanceId, loadGeneration) { mutableStateOf(config.enableKcpProxy) }
    var disableP2p by remember(instanceId, loadGeneration) { mutableStateOf(config.disableP2p) }
    var noTun by remember(instanceId, loadGeneration) { mutableStateOf(config.noTun) }
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
    var vpnPortalClientNetworkAddr by remember(instanceId, loadGeneration) { mutableStateOf(config.vpnPortalClientNetworkAddr) }
    var vpnPortalClientNetworkLen by remember(instanceId, loadGeneration) { mutableStateOf(config.vpnPortalClientNetworkLen.toString()) }

    // SOCKS5
    var enableSocks5 by remember(instanceId, loadGeneration) { mutableStateOf(config.enableSocks5) }
    var socks5Port by remember(instanceId, loadGeneration) { mutableStateOf(config.socks5Port.toString()) }

    // Exit nodes
    var exitNodes by remember(instanceId, loadGeneration) { mutableStateOf(config.exitNodes) }

    val context = androidx.compose.ui.platform.LocalContext.current

    fun collectForm() = ConfigFormState(
        networkName = networkName,
        networkSecret = networkSecret,
        instanceName = instanceName,
        virtualIpv4 = virtualIpv4,
        dhcp = dhcp,
        peerUrls = peerUrls,
        listenerUrls = listenerUrls,
        proxyCidrs = proxyCidrs,
        exitNodes = exitNodes,
        hostname = hostname,
        devName = devName,
        mtu = mtu,
        latencyFirst = latencyFirst,
        showAdvanced = showAdvanced,
        disableIpv6 = disableIpv6,
        enableKcpProxy = enableKcpProxy,
        disableP2p = disableP2p,
        noTun = noTun,
        enableExitNode = enableExitNode,
        multiThread = multiThread,
        enableMagicDns = enableMagicDns,
        enablePrivateMode = enablePrivateMode,
        disableEncryption = disableEncryption,
        disableTcpHolePunching = disableTcpHolePunching,
        disableUdpHolePunching = disableUdpHolePunching,
        enableVpnPortal = enableVpnPortal,
        vpnPortalListenPort = vpnPortalListenPort,
        vpnPortalClientNetworkAddr = vpnPortalClientNetworkAddr,
        vpnPortalClientNetworkLen = vpnPortalClientNetworkLen,
        enableSocks5 = enableSocks5,
        socks5Port = socks5Port,
    )

    val initialForm = remember(instanceId, loadGeneration) { collectForm() }
    val initialPortForwards = remember(instanceId, loadGeneration) { config.portForwards }
    val dirty = collectForm() != initialForm || config.portForwards != initialPortForwards

    // Sync form state back to viewModel config
    fun syncToConfig() {
        viewModel.updateConfig { c -> c.copy(
            networkName = networkName,
            networkSecret = networkSecret,
            instanceName = instanceName,
            virtualIpv4 = virtualIpv4,
            dhcp = dhcp,
            peerUrls = peerUrls,
            listenerUrls = listenerUrls,
            proxyCidrs = proxyCidrs,
            hostname = hostname.ifEmpty { null },
            devName = devName,
            mtu = mtu.toIntOrNull(),
            latencyFirst = latencyFirst,
            disableIpv6 = disableIpv6,
            enableKcpProxy = enableKcpProxy,
            disableP2p = disableP2p,
            noTun = noTun,
            enableExitNode = enableExitNode,
            multiThread = multiThread,
            enableMagicDns = enableMagicDns,
            enablePrivateMode = enablePrivateMode,
            disableEncryption = disableEncryption,
            disableTcpHolePunching = disableTcpHolePunching,
            disableUdpHolePunching = disableUdpHolePunching,
            enableVpnPortal = enableVpnPortal,
            vpnPortalListenPort = vpnPortalListenPort.toIntOrNull() ?: 22022,
            vpnPortalClientNetworkAddr = vpnPortalClientNetworkAddr,
            vpnPortalClientNetworkLen = vpnPortalClientNetworkLen.toIntOrNull() ?: 24,
            enableSocks5 = enableSocks5,
            socks5Port = socks5Port.toIntOrNull() ?: 1080,
            exitNodes = exitNodes,
        ) }
    }

    // ---------- 校验 ----------
    var attemptedSave by remember(instanceId, loadGeneration) { mutableStateOf(false) }

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
        if (enableVpnPortal) {
            val port = vpnPortalListenPort.toIntOrNull()
            if (port == null || port !in 1..65535) {
                errs["portalPort"] = context.getString(R.string.err_invalid_port)
            }
        }
        if (enableSocks5) {
            val port = socks5Port.toIntOrNull()
            if (port == null || port !in 1..65535) {
                errs["socksPort"] = context.getString(R.string.err_invalid_port)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))

        // Basic Settings
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

        Spacer(Modifier.height(12.dp))

        // Initial Peers
        Card(modifier = Modifier.fillMaxWidth()) {
            UrlListInput(
                label = stringResource(R.string.config_initial_peers),
                urls = peerUrls,
                onUrlsChange = { peerUrls = it },
                hint = stringResource(R.string.config_peer_url_hint),
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Listeners
        Card(modifier = Modifier.fillMaxWidth()) {
            ListenerPicker(
                label = stringResource(R.string.config_listeners),
                urls = listenerUrls,
                onUrlsChange = { listenerUrls = it },
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Proxy CIDRs
        Card(modifier = Modifier.fillMaxWidth()) {
            UrlListInput(
                label = stringResource(R.string.config_proxy_cidrs),
                urls = proxyCidrs,
                onUrlsChange = { proxyCidrs = it },
                hint = stringResource(R.string.config_proxy_cidrs_hint),
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Exit Nodes
        Card(modifier = Modifier.fillMaxWidth()) {
            UrlListInput(
                label = stringResource(R.string.config_exit_nodes),
                urls = exitNodes,
                onUrlsChange = { exitNodes = it },
                hint = stringResource(R.string.config_exit_nodes_hint),
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        // Advanced Settings
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
                        SwitchPreference(
                            title = stringResource(R.string.config_no_tun),
                            summary = stringResource(R.string.config_no_tun_summary),
                            checked = noTun,
                            onCheckedChange = { noTun = it },
                        )
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
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // VPN Portal
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
                            TextField(
                                value = vpnPortalClientNetworkAddr,
                                onValueChange = { vpnPortalClientNetworkAddr = it },
                                label = stringResource(R.string.config_vpn_portal_network),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            TextField(
                                value = vpnPortalClientNetworkLen,
                                onValueChange = { vpnPortalClientNetworkLen = it },
                                label = stringResource(R.string.config_vpn_portal_network_len),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // SOCKS5 Proxy
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

        Spacer(Modifier.height(12.dp))

        // Port Forwards
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

        Spacer(Modifier.height(24.dp))

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
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(16.dp))
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

private fun isValidIpv4Cidr(value: String): Boolean {
    val match = ipv4CidrRegex.matchEntire(value.trim()) ?: return false
    val (a, b, c, d, prefix) = match.destructured
    val octets = listOf(a, b, c, d).map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    val prefixVal = prefix.toIntOrNull() ?: return false
    return prefixVal in 0..32
}
