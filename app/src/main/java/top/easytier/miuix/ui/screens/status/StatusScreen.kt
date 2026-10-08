package top.easytier.miuix.ui.screens.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import org.json.JSONObject
import top.easytier.miuix.R
import top.easytier.miuix.data.model.EventLevel
import top.easytier.miuix.data.model.NatType
import top.easytier.miuix.data.model.PeerRoutePair
import top.easytier.miuix.ui.components.FilterChip
import top.easytier.miuix.ui.components.PressableLabel
import top.easytier.miuix.ui.components.TrafficChart
import top.easytier.miuix.ui.components.pressScale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TX_COLOR = 0xFF3482FF
private const val RX_COLOR = 0xFF4CAF50

@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    onStartNetwork: () -> Unit = {},
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val instance by viewModel.currentInstance.collectAsState()
    val txRate by viewModel.txRate.collectAsState()
    val rxRate by viewModel.rxRate.collectAsState()
    val txHistory by viewModel.txHistory.collectAsState()
    val rxHistory by viewModel.rxHistory.collectAsState()

    // 事件 / 对端分区切换；事件过滤状态提升到页面级，切 Tab 不丢失
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var showAllEvents by rememberSaveable { mutableStateOf(false) }
    var warnOnly by rememberSaveable { mutableStateOf(false) }
    var clearedAt by rememberSaveable { mutableLongStateOf(0L) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    fun copyToClipboard(value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    if (instance == null || instance?.running != true) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Lan,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.status_no_running),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStartNetwork,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.status_empty_cta))
                }
            }
        }
        return
    }

    val detail = instance?.detail
    val nodeInfo = detail?.myNodeInfo

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = contentBottomPadding),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Node Info Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.status_node_info), style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(12.dp))

                    // Traffic rates
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("TX Rate", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text(txRate, style = MiuixTheme.textStyles.title2)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RX Rate", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text(rxRate, style = MiuixTheme.textStyles.title2)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Traffic chart
                    TrafficChart(
                        txHistory = txHistory,
                        rxHistory = rxHistory,
                    )

                    // 图例 + 当前峰值刻度
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(color = Color(TX_COLOR))
                        Text(
                            stringResource(R.string.peer_tx),
                            style = MiuixTheme.textStyles.body2,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(12.dp))
                        LegendDot(color = Color(RX_COLOR))
                        Text(
                            stringResource(R.string.peer_rx),
                            style = MiuixTheme.textStyles.body2,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.weight(1f))
                        val peak = (txHistory + rxHistory).maxOrNull()
                        Text(
                            text = stringResource(R.string.chart_peak, formatBytes(peak ?: 0) + "/s"),
                            style = MiuixTheme.textStyles.body2,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    if (nodeInfo != null) {
                        Spacer(Modifier.height(12.dp))
                        NodeInfoChips(nodeInfo, onLongPressCopy = { copyToClipboard(it) })
                    }
                }
            }
        }

        // 事件 / 对端：TabRow 左右切换（miuix TabRow；对端在左、事件在右）
        val parsedEvents = detail?.parsedEvents ?: emptyList()
        val peerPairs = viewModel.getPeerRoutePairs()
        item {
            TabRow(
                tabs = listOf(
                    "${stringResource(R.string.status_tab_peers)} (${peerPairs.size})",
                    "${stringResource(R.string.status_events)} (${parsedEvents.size})",
                ),
                selectedTabIndex = selectedSection,
                onTabSelected = { selectedSection = it },
                cornerRadius = 16.dp,
                itemSpacing = 12.dp,
                height = 42.dp,
            )
        }
        item {
            Crossfade(
                targetState = selectedSection,
                animationSpec = tween(durationMillis = 150),
                label = "statusSection",
            ) { section ->
                if (section == 0) {
                    PeersContent(
                        peerPairs = peerPairs,
                        onLongPressCopy = { copyToClipboard(it) },
                    )
                } else {
                    EventsCard(
                        events = parsedEvents,
                        showAllEvents = showAllEvents,
                        warnOnly = warnOnly,
                        clearedAt = clearedAt,
                        onShowAllEventsChange = { showAllEvents = it },
                        onWarnOnlyChange = { warnOnly = it },
                        onClear = {
                            clearedAt = System.currentTimeMillis()
                            showAllEvents = false
                        },
                        onCopy = { copyToClipboard(it) },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(8.dp)
            .drawBehind { drawCircle(color) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeInfoChips(
    nodeInfo: top.easytier.miuix.data.model.NodeInfo,
    onLongPressCopy: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoChip(stringResource(R.string.status_peer_id), nodeInfo.peerId.toString(), onLongPressCopy)
        InfoChip(stringResource(R.string.status_virtual_ip), nodeInfo.virtualIpv4.toString(), onLongPressCopy)
        InfoChip(stringResource(R.string.status_hostname), nodeInfo.hostname, onLongPressCopy)
        InfoChip(stringResource(R.string.status_version), nodeInfo.version, onLongPressCopy)

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoChip(stringResource(R.string.status_public_ip), nodeInfo.publicIpv4.toString(), onLongPressCopy)
                nodeInfo.interfaceIpv4s.firstOrNull()?.let {
                    InfoChip(stringResource(R.string.status_local_ip), it.toString(), onLongPressCopy)
                }
                InfoChip(stringResource(R.string.status_nat_type), natTypeName(nodeInfo.stunInfo.udpNatType))
                // IPv6
                nodeInfo.publicIpv6?.let {
                    InfoChip(stringResource(R.string.status_public_ipv6), it.toString(), onLongPressCopy)
                }
                nodeInfo.interfaceIpv6s.firstOrNull()?.let {
                    InfoChip(stringResource(R.string.status_local_ipv6), it.toString(), onLongPressCopy)
                }
            }
        }
    }

    TextButton(
        text = if (expanded) stringResource(R.string.status_show_less) else stringResource(R.string.status_show_more),
        onClick = { expanded = !expanded },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoChip(label: String, value: String, onLongPressCopy: ((String) -> Unit)? = null) {
    Card {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .then(
                    if (value.isNotBlank() && onLongPressCopy != null) {
                        Modifier.combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onLongClick = { onLongPressCopy(value) },
                            onClick = {},
                        )
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, style = MiuixTheme.textStyles.body2)
        }
    }
}

/** 小圆点指示器（事件等级、图例等） */
@Composable
private fun Dot(color: Color, size: Int = 8) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(size.dp)
            .drawBehind { drawCircle(color) },
    )
}

@Composable
private fun EventsCard(
    events: List<top.easytier.miuix.data.model.EventInfo>,
    showAllEvents: Boolean,
    warnOnly: Boolean,
    clearedAt: Long,
    onShowAllEventsChange: (Boolean) -> Unit,
    onWarnOnlyChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
) {
    // timestamp 为 0 代表时间解析失败：这类事件不能被「清空」永久吞掉，
    // 否则列表会进入无法回退的空状态（连清空按钮都会一起消失）。
    val visibleEvents = events.asSequence()
        .filter { it.timestamp == 0L || it.timestamp >= clearedAt }
        .filter { !warnOnly || detectEventSeverity(it) != EventLevel.INFO }
        .toList()
    val displayEvents = visibleEvents.take(if (showAllEvents) 20 else 5)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 级别过滤 + 展开条数 + 清空（统一 44dp 触控规格）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    label = stringResource(R.string.events_filter_all),
                    selected = !warnOnly,
                    onClick = { onWarnOnlyChange(false) },
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    label = stringResource(R.string.events_filter_warnings),
                    selected = warnOnly,
                    onClick = { onWarnOnlyChange(true) },
                )
                Spacer(Modifier.weight(1f))
                if (visibleEvents.size > 5) {
                    PressableLabel(
                        label = if (showAllEvents) stringResource(R.string.status_show_count, visibleEvents.size)
                        else stringResource(R.string.status_show_count, 20),
                        onClick = { onShowAllEventsChange(!showAllEvents) },
                    )
                }
                if (visibleEvents.isNotEmpty()) {
                    PressableLabel(
                        label = stringResource(R.string.events_clear),
                        onClick = onClear,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (displayEvents.isEmpty()) {
                Text(
                    text = stringResource(R.string.events_cleared_hint),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                // 行距内化到每行的可点击区内（ui-skills: interactive-hit-areas，消除触控死区）
                displayEvents.forEach { event ->
                    EventLogItem(event, onCopy)
                }
            }
        }
    }
}

/** 对端分区内容：摘要行 + 空态/对端卡片 */
@Composable
private fun PeersContent(
    peerPairs: List<PeerRoutePair>,
    onLongPressCopy: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                R.string.status_peers_summary,
                peerPairs.size,
                peerPairs.count { it.route.isRelay },
            ),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (peerPairs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.status_no_peers), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            peerPairs.forEach { pair ->
                PeerCard(pair, onLongPressCopy = onLongPressCopy)
            }
        }
    }
}

/** Small capsule badge showing whether a peer is reached directly (P2P) or via relay. */
@Composable
private fun RouteModeBadge(relay: Boolean) {
    val color = if (relay) Color(0xFFFFA000) else Color(0xFF4CAF50)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(if (relay) R.string.peer_relay else R.string.peer_p2p),
            color = color,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun latencyColor(latencyUs: Long): Color = when {
    latencyUs <= 0 -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    latencyUs < 10_000 -> Color(0xFF4CAF50)
    latencyUs < 50_000 -> Color(0xFFFFA000)
    else -> MiuixTheme.colorScheme.error
}

@Composable
private fun lossColor(rate: Float): Color = when {
    rate <= 0f -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    rate < 0.01f -> Color(0xFF4CAF50)
    rate < 0.05f -> Color(0xFFFFA000)
    else -> MiuixTheme.colorScheme.error
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeerCard(pair: PeerRoutePair, onLongPressCopy: (String) -> Unit) {
    val route = pair.route
    val peer = pair.peer
    // 上游会用 default_conn_id 指定主连接；多路径（UDP + 中继）并存时
    // 直接取 firstOrNull 可能拿到非主连接，导致延迟/丢包/协议都不是用户以为的那条。
    val conn = peer?.primaryConn
    val stats = conn?.stats
    var expanded by remember { mutableStateOf(false) }
    // 中继对端常常没有 conn stats，此时退回端到端的 path_latency，避免永远显示 "- ms"
    val latencyUs = stats?.latencyUs?.takeIf { it > 0 } ?: route.pathLatencyUs
    // loss_rate 缺键就等于 0（pbjson 省略 0.0），只有真的拿到 stats 时这个数字才有意义，
    // 否则会把「没有数据」显示成「0.0%」。
    val lossRate: Float? = if (stats != null) conn?.lossRate else null
    val dotColor = if (latencyUs > 0) latencyColor(latencyUs) else MiuixTheme.colorScheme.onSurfaceVariantSummary

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onLongClick = { route.ipv4Addr?.let(onLongPressCopy) },
                            onClick = { expanded = !expanded },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Connection status dot
                    Box(
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .height(10.dp)
                            .width(10.dp)
                            .then(Modifier.drawBehind { drawCircle(dotColor) }),
                    )
                    Column {
                        Text(
                            text = route.hostname.ifEmpty { route.ipv4Addr ?: "N/A" },
                            style = MiuixTheme.textStyles.title2,
                        )
                        Text(
                            text = route.ipv4Addr ?: "",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = conn?.tunnel?.tunnelType?.uppercase() ?: "-",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (latencyUs > 0) "%.1f ms".format(latencyUs / 1000.0) else "- ms",
                        style = MiuixTheme.textStyles.body2,
                        color = if (latencyUs > 0) latencyColor(latencyUs) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Stats row with color-coded values
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.peer_tx), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 11.sp)
                    Text(formatBytes(stats?.txBytes ?: 0), style = MiuixTheme.textStyles.body2)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.peer_rx), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 11.sp)
                    Text(formatBytes(stats?.rxBytes ?: 0), style = MiuixTheme.textStyles.body2)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.status_loss), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 11.sp)
                    Text(
                        text = lossRate?.let { "%.1f%%".format(it * 100) } ?: "-",
                        style = MiuixTheme.textStyles.body2,
                        color = lossRate?.takeIf { it > 0f }?.let { lossColor(it) }
                            ?: MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(8.dp))
                RouteModeBadge(relay = pair.route.isRelay)
            }

            // Expanded detail
            AnimatedVisibility(visible = expanded) {
                val dividerColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                Column {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .drawBehind { drawRect(dividerColor) }
                    )
                    Spacer(Modifier.height(10.dp))

                    PeerInfoRow(stringResource(R.string.peer_version), route.version.ifEmpty { "-" })
                    PeerInfoRow(stringResource(R.string.peer_id_label), peer?.peerId?.toString() ?: "-")

                    // Tunnel addresses
                    conn?.tunnel?.localAddr?.url?.takeIf { it.isNotEmpty() }?.let {
                        PeerInfoRow(stringResource(R.string.peer_tunnel_local), it)
                    }
                    conn?.tunnel?.remoteAddr?.url?.takeIf { it.isNotEmpty() }?.let {
                        PeerInfoRow(stringResource(R.string.peer_tunnel_remote), it)
                    }

                    if (route.proxyCidrs.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.status_route),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 11.sp,
                        )
                        route.proxyCidrs.forEach { cidr ->
                            Text(
                                cidr,
                                style = MiuixTheme.textStyles.body2,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

private fun unescapeJson(s: String): String {
    var result = s
    // Handle double-escaped sequences first
    result = result.replace("\\\\\"", "\"")
    result = result.replace("\\\\/", "/")
    result = result.replace("\\\\n", "\n")
    result = result.replace("\\\\t", "\t")
    result = result.replace("\\\\r", "\r")
    result = result.replace("\\\\\\\\", "\\\\")
    // Single-escaped sequences
    result = result.replace("\\\"", "\"")
    result = result.replace("\\/", "/")
    result = result.replace("\\n", "\n")
    result = result.replace("\\t", "\t")
    result = result.replace("\\r", "\r")
    result = result.replace("\\\\", "\\")
    return result
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}

/**
 * NAT 类型标签。
 *
 * 取值来自上游 `common.proto` 的 NatType 枚举（JSON 里是枚举名字符串），
 * 不再是「0..3 的序号」那套早已不存在的映射。
 */
@Composable
private fun natTypeName(natType: NatType): String = when (natType) {
    NatType.Unknown -> stringResource(R.string.status_nat_unknown)
    NatType.OpenInternet -> stringResource(R.string.status_nat_open_internet)
    NatType.NoPat -> stringResource(R.string.status_nat_no_pat)
    NatType.FullCone -> stringResource(R.string.status_nat_full_cone)
    NatType.Restricted -> stringResource(R.string.status_nat_restricted)
    NatType.PortRestricted -> stringResource(R.string.status_nat_port_restricted)
    NatType.Symmetric -> stringResource(R.string.status_nat_symmetric)
    NatType.SymUdpFirewall -> stringResource(R.string.status_nat_sym_udp_firewall)
    NatType.SymmetricEasyInc -> stringResource(R.string.status_nat_sym_easy_inc)
    NatType.SymmetricEasyDec -> stringResource(R.string.status_nat_sym_easy_dec)
}

/** 事件级别由解析层从 GlobalCtxEvent 变体名推导（上游从不序列化 level）。 */
private fun detectEventSeverity(event: top.easytier.miuix.data.model.EventInfo): EventLevel = event.level

/** 常见事件的用户可读标题，未映射的事件回退为原始类型名 */
private fun eventTitleRes(eventType: String): Int? = when (eventType) {
    "PeerConnAdded" -> R.string.event_peer_conn_added
    "PeerConnRemoved" -> R.string.event_peer_conn_removed
    "ConnAdded" -> R.string.event_conn_added
    "ConnRemoved" -> R.string.event_conn_removed
    "PeerAdded" -> R.string.event_peer_added
    "PeerRemoved" -> R.string.event_peer_removed
    "ListenerAdded" -> R.string.event_listener_added
    "ListenerRemoved" -> R.string.event_listener_removed
    else -> null
}

/** 摘要行优先展示人类可读字段；conn_id/my_peer_id 等技术标识不出现在摘要里 */
private val eventPreferredSummaryFields =
    listOf("remote_addr", "local_addr", "url", "error", "reason", "msg", "message", "name", "cost")

@Composable
private fun EventLogItem(event: top.easytier.miuix.data.model.EventInfo, onCopy: (String) -> Unit) {
    val severity = detectEventSeverity(event)
    val severityColor = when (severity) {
        EventLevel.ERROR -> MiuixTheme.colorScheme.error
        EventLevel.WARN -> Color(0xFFFFA000)
        EventLevel.INFO -> MiuixTheme.colorScheme.primary
    }
    // 事件类型与时间已由解析层从事件体里取出（事件 JSON 只有 time 与 event 两个键），
    // 这里只负责把 payload 摊平成可读的键值对。
    val eventType = event.type
    val extraFields = remember(event.raw, eventType) {
        try {
            val obj = JSONObject(unescapeJson(event.raw))
            val metaKeys = setOf("time", "timestamp", "ts", "event")
            val detailObj = obj.optJSONObject("event")?.optJSONObject(eventType)
                ?: obj.optJSONObject(eventType)
            val pairs = mutableListOf<Pair<String, String>>()
            if (detailObj != null) {
                detailObj.keys().forEach { key ->
                    val value = unescapeJson(detailObj.optString(key, detailObj.opt(key)?.toString() ?: ""))
                    if (value.isNotBlank() && value != "null") {
                        pairs.add(key to value)
                    }
                }
            } else {
                obj.keys().forEach { key ->
                    if (key !in metaKeys && key != eventType) {
                        val value = unescapeJson(obj.optString(key, obj.opt(key)?.toString() ?: ""))
                        if (value.isNotBlank() && value != "null") {
                            pairs.add(key to value)
                        }
                    }
                }
            }
            pairs
        } catch (_: Exception) {
            emptyList<Pair<String, String>>()
        }
    }
    var showJson by remember { mutableStateOf(false) }
    var capturedJson by remember { mutableStateOf("") }
    var capturedTitle by remember { mutableStateOf("") }
    val formattedJson = remember(event.raw) {
        val cleaned = unescapeJson(event.raw)
        try {
            JSONObject(cleaned).toString(2)
        } catch (_: Exception) {
            cleaned
        }
    }
    val timeStr = if (event.timestamp > 0) {
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(event.timestamp))
    } else ""
    // 摘要行：只取可读字段，避免 my_peer_id/conn_id 之类技术值刷屏
    val inlineSummary = extraFields
        .firstOrNull { it.first in eventPreferredSummaryFields }
        ?.let { "${it.first}: ${it.second}" }
        ?: ""
    val titleText = eventTitleRes(eventType)?.let { stringResource(it) } ?: eventType

    // 行距内化到触控区内（interactive-hit-areas），行高 ≥44dp，并带按压缩放反馈
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    capturedJson = formattedJson
                    capturedTitle = eventType
                    showJson = true
                },
            )
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colored dot
            Dot(color = severityColor)
            // Level badge：大多数事件都是 INFO，仅警告/错误才显示徽标降噪
            if (severity != EventLevel.INFO) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = severityColor.copy(alpha = 0.15f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = severity.name,
                        fontSize = 10.sp,
                        color = severityColor,
                    )
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
            // Event type
            Text(
                text = titleText,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            // Time
            if (timeStr.isNotEmpty()) {
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        // Inline summary line
        if (inlineSummary.isNotEmpty()) {
            Text(
                text = inlineSummary,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }

    // Popup dialog for formatted JSON
    if (showJson) {
        OverlayDialog(
            title = capturedTitle.ifEmpty { stringResource(R.string.event_detail_title) },
            show = true,
            onDismissRequest = { showJson = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = capturedJson,
                    style = MiuixTheme.textStyles.body2.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(
                        text = stringResource(R.string.copy_action),
                        onClick = {
                            onCopy(capturedJson)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.ok),
                        onClick = { showJson = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
