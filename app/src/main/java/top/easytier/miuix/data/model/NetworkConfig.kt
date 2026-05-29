package top.easytier.miuix.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class NetworkingMethod(val value: Int) {
    PublicServer(0),
    Manual(1),
    Standalone(2);
}

data class PortForwardConfig(
    val bindIp: String = "",
    val bindPort: Int = 65535,
    val dstIp: String = "",
    val dstPort: Int = 65535,
    val proto: String = "tcp",
)

data class NetworkConfig(
    val instanceId: String = UUID.randomUUID().toString(),
    val instanceName: String = "",
    val dhcp: Boolean = true,
    val virtualIpv4: String = "10.144.144.1/24",
    val networkLength: Int = 24,
    val hostname: String? = null,
    val networkName: String = "easytier",
    val networkSecret: String = "",
    val credentialFile: String = "",
    val networkingMethod: NetworkingMethod = NetworkingMethod.Manual,
    val publicServerUrl: String = "",
    val peerUrls: List<String> = emptyList(),
    val proxyCidrs: List<String> = emptyList(),
    val enableVpnPortal: Boolean = false,
    val vpnPortalListenPort: Int = 22022,
    val vpnPortalClientNetworkAddr: String = "",
    val vpnPortalClientNetworkLen: Int = 24,
    val advancedSettings: Boolean = false,
    val listenerUrls: List<String> = listOf("tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011"),
    val latencyFirst: Boolean = false,
    val devName: String = "",

    // Boolean flags
    val useSmoltcp: Boolean = false,
    val disableIpv6: Boolean = false,
    val ipv6PublicAddrAuto: Boolean = false,
    val enableKcpProxy: Boolean = false,
    val disableKcpInput: Boolean = false,
    val enableQuicProxy: Boolean = false,
    val disableQuicInput: Boolean = false,
    val disableP2p: Boolean = false,
    val p2pOnly: Boolean = false,
    val lazyP2p: Boolean = false,
    val bindDevice: Boolean = true,
    val noTun: Boolean = false,
    val enableExitNode: Boolean = false,
    val relayAllPeerRpc: Boolean = false,
    val needP2p: Boolean = false,
    val multiThread: Boolean = true,
    val proxyForwardBySystem: Boolean = false,
    val disableEncryption: Boolean = false,
    val disableTcpHolePunching: Boolean = false,
    val disableUdpHolePunching: Boolean = false,
    val disableUpnp: Boolean = false,
    val enableUdpBroadcastRelay: Boolean = false,
    val disableSymHolePunching: Boolean = false,
    val enableRelayNetworkWhitelist: Boolean = false,
    val relayNetworkWhitelist: List<String> = emptyList(),
    val enableManualRoutes: Boolean = false,
    val routes: List<String> = emptyList(),
    val exitNodes: List<String> = emptyList(),
    val enableSocks5: Boolean = false,
    val socks5Port: Int = 1080,
    val mtu: Int? = null,
    val instanceRecvBpsLimit: Long? = null,
    val mappedListeners: List<String> = emptyList(),
    val enableMagicDns: Boolean = false,
    val enablePrivateMode: Boolean = false,
    val portForwards: List<PortForwardConfig> = emptyList(),
)

fun normalizeNetworkConfig(config: NetworkConfig): NetworkConfig {
    val cleanedPeerUrls = config.peerUrls.map { it.trim() }.filter { it.isNotBlank() }
    val cleanedListenerUrls = config.listenerUrls.map { it.trim() }.filter { it.isNotBlank() }
    return config.copy(
        peerUrls = cleanedPeerUrls,
        listenerUrls = cleanedListenerUrls,
        networkingMethod = NetworkingMethod.Manual,
        publicServerUrl = "",
    )
}

fun NetworkConfig.toJSON(): JSONObject = JSONObject().apply {
    put("instanceId", instanceId)
    put("instanceName", instanceName)
    put("networkName", networkName)
    put("networkSecret", networkSecret)
    put("dhcp", dhcp)
    put("virtualIpv4", virtualIpv4)
    put("hostname", hostname ?: "")
    put("devName", devName)
    mtu?.let { put("mtu", it) }
    put("listenerUrls", JSONArray(listenerUrls))
    put("peerUrls", JSONArray(peerUrls))
    put("proxyCidrs", JSONArray(proxyCidrs))
    put("exitNodes", JSONArray(exitNodes))
    put("noTun", noTun)
    put("latencyFirst", latencyFirst)
    put("disableIpv6", disableIpv6)
    put("enableKcpProxy", enableKcpProxy)
    put("disableP2p", disableP2p)
    put("enableExitNode", enableExitNode)
    put("multiThread", multiThread)
    put("enableMagicDns", enableMagicDns)
    put("enablePrivateMode", enablePrivateMode)
    put("disableEncryption", disableEncryption)
    put("disableTcpHolePunching", disableTcpHolePunching)
    put("disableUdpHolePunching", disableUdpHolePunching)
    put("enableVpnPortal", enableVpnPortal)
    put("vpnPortalListenPort", vpnPortalListenPort)
    put("vpnPortalClientNetworkAddr", vpnPortalClientNetworkAddr)
    put("vpnPortalClientNetworkLen", vpnPortalClientNetworkLen)
    put("enableSocks5", enableSocks5)
    put("socks5Port", socks5Port)
}

fun JSONObject.toNetworkConfig(): NetworkConfig = NetworkConfig(
    instanceId = optString("instanceId", UUID.randomUUID().toString()),
    instanceName = optString("instanceName", ""),
    networkName = optString("networkName", "easytier"),
    networkSecret = optString("networkSecret", ""),
    dhcp = optBoolean("dhcp", true),
    virtualIpv4 = optString("virtualIpv4", "10.144.144.1/24"),
    hostname = optString("hostname", "").ifEmpty { null },
    devName = optString("devName", ""),
    mtu = if (has("mtu")) optInt("mtu") else null,
    listenerUrls = optJSONArray("listenerUrls")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList(),
    peerUrls = optJSONArray("peerUrls")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList(),
    proxyCidrs = optJSONArray("proxyCidrs")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList(),
    exitNodes = optJSONArray("exitNodes")?.let { arr ->
        (0 until arr.length()).map { arr.getString(it) }
    } ?: emptyList(),
    noTun = optBoolean("noTun", false),
    latencyFirst = optBoolean("latencyFirst", false),
    disableIpv6 = optBoolean("disableIpv6", false),
    enableKcpProxy = optBoolean("enableKcpProxy", false),
    disableP2p = optBoolean("disableP2p", false),
    enableExitNode = optBoolean("enableExitNode", false),
    multiThread = optBoolean("multiThread", true),
    enableMagicDns = optBoolean("enableMagicDns", false),
    enablePrivateMode = optBoolean("enablePrivateMode", false),
    disableEncryption = optBoolean("disableEncryption", false),
    disableTcpHolePunching = optBoolean("disableTcpHolePunching", false),
    disableUdpHolePunching = optBoolean("disableUdpHolePunching", false),
    enableVpnPortal = optBoolean("enableVpnPortal", false),
    vpnPortalListenPort = optInt("vpnPortalListenPort", 22022),
    vpnPortalClientNetworkAddr = optString("vpnPortalClientNetworkAddr", ""),
    vpnPortalClientNetworkLen = optInt("vpnPortalClientNetworkLen", 24),
    enableSocks5 = optBoolean("enableSocks5", false),
    socks5Port = optInt("socks5Port", 1080),
)
