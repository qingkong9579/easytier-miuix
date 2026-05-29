package top.easytier.miuix.ui.screens.status

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import top.easytier.miuix.R
import top.easytier.miuix.data.model.PeerRoutePair
import top.easytier.miuix.ui.components.TrafficChart
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = hiltViewModel(),
) {
    val instance by viewModel.currentInstance.collectAsState()
    val txRate by viewModel.txRate.collectAsState()
    val rxRate by viewModel.rxRate.collectAsState()
    val txHistory by viewModel.txHistory.collectAsState()
    val rxHistory by viewModel.rxHistory.collectAsState()

    if (instance == null || instance?.running != true) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.status_no_running),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        return
    }

    val detail = instance?.detail
    val nodeInfo = detail?.myNodeInfo

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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

                    if (nodeInfo != null) {
                        Spacer(Modifier.height(12.dp))
                        NodeInfoChips(nodeInfo)
                    }
                }
            }
        }

        // Events
        val parsedEvents = detail?.parsedEvents ?: emptyList()
        if (parsedEvents.isNotEmpty()) {
            item {
                var showAllEvents by remember { mutableStateOf(false) }
                val displayEvents = if (showAllEvents) parsedEvents else parsedEvents.takeLast(5)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${stringResource(R.string.status_events)} (${parsedEvents.size})", style = MiuixTheme.textStyles.title2)
                            if (parsedEvents.size > 5) {
                                TextButton(
                                    text = if (showAllEvents) stringResource(R.string.status_show_less) else stringResource(R.string.status_show_more),
                                    onClick = { showAllEvents = !showAllEvents },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        displayEvents.forEachIndexed { index, event ->
                            EventLogItem(event)
                            if (index < displayEvents.lastIndex) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        // Peer Info
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.status_peer_info), style = MiuixTheme.textStyles.title2)
                Text(
                    "${viewModel.getPeerRoutePairs().size} peers",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        val peerPairs = viewModel.getPeerRoutePairs()
        if (peerPairs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.status_no_peers), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        } else {
            items(peerPairs) { pair ->
                PeerCard(pair)
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeInfoChips(nodeInfo: top.easytier.miuix.data.model.NodeInfo) {
    var expanded by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        InfoChip(stringResource(R.string.status_peer_id), nodeInfo.peerId.toString())
        InfoChip(stringResource(R.string.status_virtual_ip), nodeInfo.virtualIpv4.toString())
        InfoChip(stringResource(R.string.status_hostname), nodeInfo.hostname)
        InfoChip(stringResource(R.string.status_version), nodeInfo.version)

        AnimatedVisibility(visible = expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoChip(stringResource(R.string.status_public_ip), nodeInfo.publicIpv4.toString())
                nodeInfo.interfaceIpv4s.firstOrNull()?.let {
                    InfoChip(stringResource(R.string.status_local_ip), it.toString())
                }
                InfoChip(stringResource(R.string.status_nat_type), natTypeName(nodeInfo.stunInfo.udpNatType))
            }
        }
    }

    TextButton(
        text = if (expanded) stringResource(R.string.status_show_less) else stringResource(R.string.status_show_more),
        onClick = { expanded = !expanded },
    )
}

@Composable
private fun InfoChip(label: String, value: String) {
    Card {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, style = MiuixTheme.textStyles.body2)
        }
    }
}

@Composable
private fun PeerCard(pair: PeerRoutePair) {
    val route = pair.route
    val peer = pair.peer
    val conn = peer?.conns?.firstOrNull()
    val stats = conn?.stats

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = conn?.tunnel?.tunnelType ?: "-",
                        style = MiuixTheme.textStyles.body2,
                    )
                    Text(
                        text = "${stats?.latencyUs?.div(1000) ?: "-"} ms",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TX", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(formatBytes(stats?.txBytes ?: 0), style = MiuixTheme.textStyles.body2)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("RX", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(formatBytes(stats?.rxBytes ?: 0), style = MiuixTheme.textStyles.body2)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.status_loss), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("${(conn?.lossRate ?: 0f) * 100}%", style = MiuixTheme.textStyles.body2)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.status_cost), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text("${route.cost}", style = MiuixTheme.textStyles.body2)
                }
            }
        }
    }
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

@Composable
private fun natTypeName(natType: Int): String = when (natType) {
    0 -> stringResource(R.string.status_nat_unknown)
    1 -> stringResource(R.string.status_nat_endpoint_independent)
    2 -> stringResource(R.string.status_nat_endpoint_dependent)
    3 -> stringResource(R.string.status_nat_symmetric)
    else -> stringResource(R.string.status_nat_type_format, natType)
}

private enum class EventSeverity { INFO, WARN, ERROR }

private fun detectEventSeverity(event: top.easytier.miuix.data.model.EventInfo): EventSeverity {
    return when (event.level.lowercase()) {
        "error", "err" -> EventSeverity.ERROR
        "warn", "warning" -> EventSeverity.WARN
        else -> EventSeverity.INFO
    }
}

@Composable
private fun EventLogItem(event: top.easytier.miuix.data.model.EventInfo) {
    val severity = detectEventSeverity(event)
    val severityColor = when (severity) {
        EventSeverity.ERROR -> MiuixTheme.colorScheme.error
        EventSeverity.WARN -> androidx.compose.ui.graphics.Color(0xFFFFA000)
        EventSeverity.INFO -> MiuixTheme.colorScheme.primary
    }
    val timeStr = if (event.timestamp > 0) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(event.timestamp))
    } else ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(8.dp)
                    .width(8.dp)
                    .then(Modifier.drawBehind { drawCircle(severityColor) }),
            )
            // Level badge
            Card(
                modifier = Modifier.padding(end = 8.dp),
                onClick = {},
            ) {
                Text(
                    text = event.level.uppercase(),
                    style = MiuixTheme.textStyles.body2,
                    color = severityColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (event.peerId != 0L) {
                Text(
                    text = "Peer ${event.peerId}",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (timeStr.isNotEmpty()) {
                Text(
                    text = timeStr,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Text(
            text = event.message.ifEmpty { event.raw },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
