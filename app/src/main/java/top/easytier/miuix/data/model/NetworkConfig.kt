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

/**
 * 上游 `[[vpn_portal_config.clients]]` 的具名 WireGuard 客户端。
 *
 * 上游已用该模型取代旧的 `client_cidr`：`VpnPortalConfig` 带
 * `#[serde(deny_unknown_fields)]` 且没有 `client_cidr` 字段，写入旧键会让整份
 * TOML 解析失败（详见 easytier-core/src/config/toml.rs 的回归测试
 * `legacy_vpn_portal_client_cidr_is_rejected_explicitly`）。
 *
 * [virtualIp] 必须自带前缀长，例如 `10.14.14.2/24`（上游类型是 `Ipv4Inet`）。
 */
data class VpnPortalClient(
    val name: String = "",
    val virtualIp: String = "",
)

/** `[flags] encryption_algorithm` 的稳定取值，对齐上游 `config/encryption.rs`。 */
enum class EncryptionAlgorithm(val value: String) {
    AesGcm("aes-gcm"),
    Aes256Gcm("aes-256-gcm"),
    ChaCha20("chacha20"),
    Xor("xor");

    companion object {
        fun fromValue(value: String): EncryptionAlgorithm =
            entries.firstOrNull { it.value == value } ?: AesGcm
    }
}

/** `[flags] data_compress_algo` 的稳定取值，对齐上游 `CompressionAlgoPb`（枚举名，非序号）。 */
enum class CompressionAlgo(val value: String) {
    None("None"),
    Zstd("Zstd");

    companion object {
        fun fromValue(value: String): CompressionAlgo =
            entries.firstOrNull { it.value == value } ?: None
    }
}

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
    val vpnPortalClients: List<VpnPortalClient> = emptyList(),
    val advancedSettings: Boolean = false,
    val listenerUrls: List<String> = listOf("tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011"),
    /** 对外公布的监听地址（NAT 后的映射地址），对应顶层 `mapped_listeners`。 */
    val mappedListeners: List<String> = emptyList(),
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
    /** `[flags] relay_network_whitelist`：单个空格分隔的字符串，核心默认 `"*"`。 */
    val relayNetworkWhitelist: String = "*",
    val enableManualRoutes: Boolean = false,
    val routes: List<String> = emptyList(),
    val exitNodes: List<String> = emptyList(),
    val enableSocks5: Boolean = false,
    val socks5Port: Int = 1080,
    val mtu: Int? = null,
    val instanceRecvBpsLimit: Long? = null,
    val enableMagicDns: Boolean = false,
    val enablePrivateMode: Boolean = false,
    val portForwards: List<PortForwardConfig> = emptyList(),
    val encryptionAlgorithm: EncryptionAlgorithm = EncryptionAlgorithm.AesGcm,
    val dataCompressAlgo: CompressionAlgo = CompressionAlgo.None,
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

// ---------- JSON 持久化辅助 ----------

private fun JSONObject.putStrings(key: String, values: List<String>) {
    put(key, JSONArray().apply { values.forEach { put(it) } })
}

private fun JSONObject.strings(key: String): List<String> =
    optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()

private fun JSONObject.putPortForwards(key: String, forwards: List<PortForwardConfig>) {
    put(
        key,
        JSONArray().apply {
            forwards.forEach { pf ->
                put(
                    JSONObject()
                        .put("bindIp", pf.bindIp)
                        .put("bindPort", pf.bindPort)
                        .put("dstIp", pf.dstIp)
                        .put("dstPort", pf.dstPort)
                        .put("proto", pf.proto)
                )
            }
        },
    )
}

private fun JSONObject.portForwards(key: String): List<PortForwardConfig> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        PortForwardConfig(
            bindIp = obj.optString("bindIp", ""),
            bindPort = obj.optInt("bindPort", 65535),
            dstIp = obj.optString("dstIp", ""),
            dstPort = obj.optInt("dstPort", 65535),
            proto = obj.optString("proto", "tcp"),
        )
    }
}

private fun JSONObject.putVpnPortalClients(key: String, clients: List<VpnPortalClient>) {
    put(
        key,
        JSONArray().apply {
            clients.forEach { client ->
                put(JSONObject().put("name", client.name).put("virtualIp", client.virtualIp))
            }
        },
    )
}

private fun JSONObject.vpnPortalClients(key: String): List<VpnPortalClient> {
    val arr = optJSONArray(key) ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        VpnPortalClient(
            name = obj.optString("name", ""),
            virtualIp = obj.optString("virtualIp", ""),
        )
    }
}

fun NetworkConfig.toJSON(): JSONObject = JSONObject().apply {
    put("instanceId", instanceId)
    put("instanceName", instanceName)
    put("networkName", networkName)
    put("networkSecret", networkSecret)
    put("credentialFile", credentialFile)
    put("dhcp", dhcp)
    put("virtualIpv4", virtualIpv4)
    put("networkLength", networkLength)
    put("hostname", hostname ?: "")
    put("devName", devName)
    mtu?.let { put("mtu", it) }
    instanceRecvBpsLimit?.let { put("instanceRecvBpsLimit", it) }
    putStrings("listenerUrls", listenerUrls)
    putStrings("mappedListeners", mappedListeners)
    putStrings("peerUrls", peerUrls)
    putStrings("proxyCidrs", proxyCidrs)
    putStrings("exitNodes", exitNodes)
    putStrings("routes", routes)
    putStrings("relayNetworkWhitelist", relayNetworkWhitelist.split(' ').filter { it.isNotBlank() })
    put("enableManualRoutes", enableManualRoutes)
    put("enableRelayNetworkWhitelist", enableRelayNetworkWhitelist)

    put("latencyFirst", latencyFirst)
    put("useSmoltcp", useSmoltcp)
    put("disableIpv6", disableIpv6)
    put("ipv6PublicAddrAuto", ipv6PublicAddrAuto)
    put("enableKcpProxy", enableKcpProxy)
    put("disableKcpInput", disableKcpInput)
    put("enableQuicProxy", enableQuicProxy)
    put("disableQuicInput", disableQuicInput)
    put("disableP2p", disableP2p)
    put("p2pOnly", p2pOnly)
    put("lazyP2p", lazyP2p)
    put("bindDevice", bindDevice)
    put("enableExitNode", enableExitNode)
    put("relayAllPeerRpc", relayAllPeerRpc)
    put("needP2p", needP2p)
    put("multiThread", multiThread)
    put("proxyForwardBySystem", proxyForwardBySystem)
    put("enableMagicDns", enableMagicDns)
    put("enablePrivateMode", enablePrivateMode)
    put("disableEncryption", disableEncryption)
    put("disableTcpHolePunching", disableTcpHolePunching)
    put("disableUdpHolePunching", disableUdpHolePunching)
    put("disableUpnp", disableUpnp)
    put("enableUdpBroadcastRelay", enableUdpBroadcastRelay)
    put("disableSymHolePunching", disableSymHolePunching)
    put("advancedSettings", advancedSettings)

    put("encryptionAlgorithm", encryptionAlgorithm.value)
    put("dataCompressAlgo", dataCompressAlgo.value)

    put("enableVpnPortal", enableVpnPortal)
    put("vpnPortalListenPort", vpnPortalListenPort)
    putVpnPortalClients("vpnPortalClients", vpnPortalClients)
    put("enableSocks5", enableSocks5)
    put("socks5Port", socks5Port)
    putPortForwards("portForwards", portForwards)
}

fun JSONObject.toNetworkConfig(): NetworkConfig = NetworkConfig(
    instanceId = optString("instanceId", UUID.randomUUID().toString()),
    instanceName = optString("instanceName", ""),
    networkName = optString("networkName", "easytier"),
    networkSecret = optString("networkSecret", ""),
    credentialFile = optString("credentialFile", ""),
    dhcp = optBoolean("dhcp", true),
    virtualIpv4 = optString("virtualIpv4", "10.144.144.1/24"),
    networkLength = optInt("networkLength", 24),
    hostname = optString("hostname", "").ifEmpty { null },
    devName = optString("devName", ""),
    mtu = if (has("mtu")) optInt("mtu") else null,
    instanceRecvBpsLimit = if (has("instanceRecvBpsLimit")) optLong("instanceRecvBpsLimit") else null,
    listenerUrls = strings("listenerUrls"),
    mappedListeners = strings("mappedListeners"),
    peerUrls = strings("peerUrls"),
    proxyCidrs = strings("proxyCidrs"),
    exitNodes = strings("exitNodes"),
    routes = strings("routes"),
    relayNetworkWhitelist = strings("relayNetworkWhitelist").joinToString(" ").ifEmpty { "*" },
    enableManualRoutes = optBoolean("enableManualRoutes", false),
    enableRelayNetworkWhitelist = optBoolean("enableRelayNetworkWhitelist", false),

    latencyFirst = optBoolean("latencyFirst", false),
    useSmoltcp = optBoolean("useSmoltcp", false),
    disableIpv6 = optBoolean("disableIpv6", false),
    ipv6PublicAddrAuto = optBoolean("ipv6PublicAddrAuto", false),
    enableKcpProxy = optBoolean("enableKcpProxy", false),
    disableKcpInput = optBoolean("disableKcpInput", false),
    enableQuicProxy = optBoolean("enableQuicProxy", false),
    disableQuicInput = optBoolean("disableQuicInput", false),
    disableP2p = optBoolean("disableP2p", false),
    p2pOnly = optBoolean("p2pOnly", false),
    lazyP2p = optBoolean("lazyP2p", false),
    bindDevice = optBoolean("bindDevice", true),
    enableExitNode = optBoolean("enableExitNode", false),
    relayAllPeerRpc = optBoolean("relayAllPeerRpc", false),
    needP2p = optBoolean("needP2p", false),
    multiThread = optBoolean("multiThread", true),
    proxyForwardBySystem = optBoolean("proxyForwardBySystem", false),
    enableMagicDns = optBoolean("enableMagicDns", false),
    enablePrivateMode = optBoolean("enablePrivateMode", false),
    disableEncryption = optBoolean("disableEncryption", false),
    disableTcpHolePunching = optBoolean("disableTcpHolePunching", false),
    disableUdpHolePunching = optBoolean("disableUdpHolePunching", false),
    disableUpnp = optBoolean("disableUpnp", false),
    enableUdpBroadcastRelay = optBoolean("enableUdpBroadcastRelay", false),
    disableSymHolePunching = optBoolean("disableSymHolePunching", false),
    advancedSettings = optBoolean("advancedSettings", false),

    encryptionAlgorithm = EncryptionAlgorithm.fromValue(optString("encryptionAlgorithm", "aes-gcm")),
    dataCompressAlgo = CompressionAlgo.fromValue(optString("dataCompressAlgo", "None")),

    enableVpnPortal = optBoolean("enableVpnPortal", false),
    vpnPortalListenPort = optInt("vpnPortalListenPort", 22022),
    vpnPortalClients = vpnPortalClients("vpnPortalClients"),
    enableSocks5 = optBoolean("enableSocks5", false),
    socks5Port = optInt("socks5Port", 1080),
    portForwards = portForwards("portForwards"),
)
