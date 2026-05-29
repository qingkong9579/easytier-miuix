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
)

data class StunInfo(
    val udpNatType: Int = 0,
    val tcpNatType: Int = 0,
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
)

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
)

data class PeerInfo(
    val peerId: Long = 0,
    val conns: List<PeerConnInfo> = emptyList(),
)

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

data class EventInfo(
    val level: String = "info",
    val message: String = "",
    val peerId: Long = 0,
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
