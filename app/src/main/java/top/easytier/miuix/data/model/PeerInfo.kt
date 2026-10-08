package top.easytier.miuix.data.model

data class Ipv4Addr(val addr: Int = 0) {
    override fun toString(): String {
        val a = (addr shr 24) and 0xFF
        val b = (addr shr 16) and 0xFF
        val c = (addr shr 8) and 0xFF
        val d = addr and 0xFF
        return "$a.$b.$c.$d"
    }
}

data class Ipv4Inet(
    val address: Ipv4Addr = Ipv4Addr(),
    val networkLength: Int = 0,
) {
    override fun toString(): String = "$address/$networkLength"
}

data class Ipv6Addr(
    val part1: Long = 0,
    val part2: Long = 0,
    val part3: Long = 0,
    val part4: Long = 0,
) {
    override fun toString(): String {
        val hextets = listOf(
            ((part1 shr 16) and 0xFFFF).toInt(),
            (part1 and 0xFFFF).toInt(),
            ((part2 shr 16) and 0xFFFF).toInt(),
            (part2 and 0xFFFF).toInt(),
            ((part3 shr 16) and 0xFFFF).toInt(),
            (part3 and 0xFFFF).toInt(),
            ((part4 shr 16) and 0xFFFF).toInt(),
            (part4 and 0xFFFF).toInt(),
        )
        // Find longest contiguous run of zeros for :: compression (RFC 5952)
        var bestStart = -1
        var bestLen = 0
        var curStart = -1
        for (i in hextets.indices) {
            if (hextets[i] == 0) {
                if (curStart == -1) curStart = i
                val len = i - curStart + 1
                if (len > bestLen) {
                    bestStart = curStart
                    bestLen = len
                }
            } else {
                curStart = -1
            }
        }
        // Only compress runs of 2+ zeros
        if (bestLen < 2) bestStart = -1

        return hextets.mapIndexed { i, h ->
            if (bestStart >= 0 && i == bestStart) ":" // start of compression
            else if (bestStart >= 0 && i in bestStart until bestStart + bestLen) null // skip compressed
            else String.format("%x", h)
        }.filterNotNull().joinToString(":").replace(":::", "::")
    }
}

/**
 * 上游 `common.proto` 的 NatType。
 *
 * JSON 侧是**枚举名字符串**（pbjson 的 `serializer.serialize_str(variant)`），
 * 且值为 `Unknown(0)` 时整个键会被省略 —— 所以「缺键」就等于 Unknown，
 * 不能像以前那样用 `optInt` 去读。
 */
enum class NatType(val wireName: String) {
    Unknown("Unknown"),
    OpenInternet("OpenInternet"),
    NoPat("NoPAT"),
    FullCone("FullCone"),
    Restricted("Restricted"),
    PortRestricted("PortRestricted"),
    Symmetric("Symmetric"),
    SymUdpFirewall("SymUdpFirewall"),
    SymmetricEasyInc("SymmetricEasyInc"),
    SymmetricEasyDec("SymmetricEasyDec");

    companion object {
        fun fromWire(value: String?): NatType =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: Unknown
    }
}

data class StunInfo(
    val udpNatType: NatType = NatType.Unknown,
    val tcpNatType: NatType = NatType.Unknown,
    val lastUpdateTime: Long = 0,
)

data class Url(val url: String = "")

data class NodeInfo(
    val virtualIpv4: Ipv4Inet = Ipv4Inet(),
    val hostname: String = "",
    val version: String = "",
    val publicIpv4: Ipv4Addr = Ipv4Addr(),
    val interfaceIpv4s: List<Ipv4Addr> = emptyList(),
    val publicIpv6: Ipv6Addr? = null,
    val interfaceIpv6s: List<Ipv6Addr> = emptyList(),
    val listeners: List<Url> = emptyList(),
    val stunInfo: StunInfo = StunInfo(),
    val vpnPortalCfg: String? = null,
    val peerId: Long = 0,
)

data class Route(
    val peerId: Long = 0,
    val ipv4Addr: String? = null,
    val nextHopPeerId: Long = 0,
    val cost: Int = 0,
    val proxyCidrs: List<String> = emptyList(),
    val hostname: String = "",
    val stunInfo: StunInfo? = null,
    val instId: String = "",
    val version: String = "",
    /** 端到端路径时延（微秒）；中继对端常常拿不到 conn stats，这是可用的兜底。 */
    val pathLatencyUs: Long = 0,
) {
    /**
     * True when traffic to this peer is relayed through another node (not direct P2P).
     * Mirrors the EasyTier CLI: cost == 1 means DIRECT; a non-self next hop also means relay.
     */
    val isRelay: Boolean
        get() = cost > 1 || (nextHopPeerId != 0L && nextHopPeerId != peerId)
}

data class TunnelInfo(
    val tunnelType: String = "",
    val localAddr: Url = Url(),
    val remoteAddr: Url = Url(),
)

data class PeerConnStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val rxPackets: Long = 0,
    val txPackets: Long = 0,
    val latencyUs: Long = 0,
)

data class PeerConnInfo(
    val connId: String = "",
    val myPeerId: Long = 0,
    val isClient: Boolean = false,
    val peerId: Long = 0,
    val features: List<String> = emptyList(),
    val tunnel: TunnelInfo? = null,
    val stats: PeerConnStats? = null,
    val lossRate: Float = 0f,
    /** 加密/认证等级（枚举名，None 时缺键）：PeerVerified / NetworkSecretConfirmed 等。 */
    val secureAuthLevel: String = "",
    /** 对端身份类型（枚举名，Admin 时缺键）：Credential / SharedNode。 */
    val peerIdentityType: String = "",
    val networkName: String = "",
    val isClosed: Boolean = false,
)

data class PeerInfo(
    val peerId: Long = 0,
    val conns: List<PeerConnInfo> = emptyList(),
    /** 上游指定的主连接；多路径并存时用它挑默认展示的那条。 */
    val defaultConnId: String = "",
    val directlyConnectedConns: List<String> = emptyList(),
) {
    /** 优先取上游指定的主连接，找不到时退回第一条。 */
    val primaryConn: PeerConnInfo?
        get() = conns.firstOrNull { it.connId == defaultConnId } ?: conns.firstOrNull()
}

data class PeerRoutePair(
    val route: Route = Route(),
    val peer: PeerInfo? = null,
)

data class NetworkInstanceRunningInfo(
    val devName: String = "",
    val myNodeInfo: NodeInfo = NodeInfo(),
    val events: List<String> = emptyList(),
    val parsedEvents: List<EventInfo> = emptyList(),
    val routes: List<Route> = emptyList(),
    val peers: List<PeerInfo> = emptyList(),
    val peerRoutePairs: List<PeerRoutePair> = emptyList(),
    val running: Boolean = false,
    val errorMsg: String? = null,
)

enum class EventLevel { INFO, WARN, ERROR }

/**
 * 一条运行事件。
 *
 * 上游 `events` 是 `repeated string`，每个元素是
 * `{"time":"<RFC3339>","event":{"<GlobalCtxEvent 变体名>":{…}}}`。
 * 事件里**没有** level / peer_id / timestamp 这些键，等级必须由事件类型推导，
 * 时间要解析 RFC3339 字符串。
 */
data class EventInfo(
    val level: EventLevel = EventLevel.INFO,
    /** GlobalCtxEvent 变体名，例如 PeerConnAdded / ConnectError。 */
    val type: String = "",
    val message: String = "",
    /** 事件时间（epoch 毫秒）；解析失败时为 0。 */
    val timestamp: Long = 0,
    val raw: String = "",
)

data class NetworkInstance(
    val instanceId: String = "",
    val name: String = "",
    val running: Boolean = false,
    val errorMsg: String = "",
    val detail: NetworkInstanceRunningInfo? = null,
)
