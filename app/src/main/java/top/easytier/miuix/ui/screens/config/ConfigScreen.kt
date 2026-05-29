package top.easytier.miuix.ui.screens.config

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.easytier.miuix.R
import top.easytier.miuix.ui.components.BoolFlag
import top.easytier.miuix.ui.components.AclManager
import top.easytier.miuix.ui.components.BoolFlagGrid
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConfigScreen(
    instanceId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()

    LaunchedEffect(instanceId) {
        viewModel.loadConfig(instanceId)
    }

    // Local form state synced from config
    var networkName by remember(config.instanceId) { mutableStateOf(config.networkName) }
    var networkSecret by remember(config.instanceId) { mutableStateOf(config.networkSecret) }
    var instanceName by remember(config.instanceId) { mutableStateOf(config.instanceName) }
    var virtualIpv4 by remember(config.instanceId) { mutableStateOf(config.virtualIpv4) }
    var dhcp by remember(config.instanceId) { mutableStateOf(config.dhcp) }
    var peerUrls by remember(config.instanceId) { mutableStateOf(config.peerUrls) }
    var listenerUrls by remember(config.instanceId) { mutableStateOf(config.listenerUrls) }
    var proxyCidrs by remember(config.instanceId) { mutableStateOf(config.proxyCidrs) }
    var hostname by remember(config.instanceId) { mutableStateOf(config.hostname ?: "") }
    var devName by remember(config.instanceId) { mutableStateOf(config.devName) }
    var mtu by remember(config.instanceId) { mutableStateOf(config.mtu?.toString() ?: "") }
    var latencyFirst by remember(config.instanceId) { mutableStateOf(config.latencyFirst) }
    var showAdvanced by remember(config.instanceId) { mutableStateOf(config.advancedSettings) }
    var showPortForwards by remember { mutableStateOf(false) }
    var showAcl by remember { mutableStateOf(false) }
    var showVpnPortal by remember { mutableStateOf(false) }
    var showSocks5 by remember { mutableStateOf(false) }

    // Boolean flags
    var disableIpv6 by remember(config.instanceId) { mutableStateOf(config.disableIpv6) }
    var enableKcpProxy by remember(config.instanceId) { mutableStateOf(config.enableKcpProxy) }
    var disableP2p by remember(config.instanceId) { mutableStateOf(config.disableP2p) }
    var noTun by remember(config.instanceId) { mutableStateOf(config.noTun) }
    var enableExitNode by remember(config.instanceId) { mutableStateOf(config.enableExitNode) }
    var multiThread by remember(config.instanceId) { mutableStateOf(config.multiThread) }
    var enableMagicDns by remember(config.instanceId) { mutableStateOf(config.enableMagicDns) }
    var enablePrivateMode by remember(config.instanceId) { mutableStateOf(config.enablePrivateMode) }
    var disableEncryption by remember(config.instanceId) { mutableStateOf(config.disableEncryption) }
    var disableTcpHolePunching by remember(config.instanceId) { mutableStateOf(config.disableTcpHolePunching) }
    var disableUdpHolePunching by remember(config.instanceId) { mutableStateOf(config.disableUdpHolePunching) }

    // VPN Portal
    var enableVpnPortal by remember(config.instanceId) { mutableStateOf(config.enableVpnPortal) }
    var vpnPortalListenPort by remember(config.instanceId) { mutableStateOf(config.vpnPortalListenPort.toString()) }
    var vpnPortalClientNetworkAddr by remember(config.instanceId) { mutableStateOf(config.vpnPortalClientNetworkAddr) }
    var vpnPortalClientNetworkLen by remember(config.instanceId) { mutableStateOf(config.vpnPortalClientNetworkLen.toString()) }

    // SOCKS5
    var enableSocks5 by remember(config.instanceId) { mutableStateOf(config.enableSocks5) }
    var socks5Port by remember(config.instanceId) { mutableStateOf(config.socks5Port.toString()) }

    // Exit nodes
    var exitNodes by remember(config.instanceId) { mutableStateOf(config.exitNodes) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

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
                TextField(
                    value = networkName,
                    onValueChange = { networkName = it },
                    label = stringResource(R.string.config_network_name),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
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

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

        // Listeners
        Card(modifier = Modifier.fillMaxWidth()) {
            ListenerPicker(
                label = stringResource(R.string.config_listeners),
                urls = listenerUrls,
                onUrlsChange = { listenerUrls = it },
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

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
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

        // ACL
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                ArrowPreference(
                    title = stringResource(R.string.config_acl),
                    summary = if (showAcl) stringResource(R.string.config_advanced_collapse) else stringResource(R.string.config_advanced_expand),
                    onClick = { showAcl = !showAcl },
                )
                AnimatedVisibility(visible = showAcl) {
                    Column {
                        AclManager(
                            aclToml = "",
                            onAclTomlChange = { /* TODO: parse and update ACL */ },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { syncToConfig(); viewModel.saveConfig(); onBack() },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.config_save))
            }
            Button(
                onClick = { syncToConfig(); viewModel.saveAndRun(); onBack() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.config_run_network))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
