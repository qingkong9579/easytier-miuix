package top.easytier.miuix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.easytier.miuix.data.model.PortForwardConfig
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PortForwardEditor(
    portForwards: List<PortForwardConfig>,
    onPortForwardsChange: (List<PortForwardConfig>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        portForwards.forEachIndexed { index, pf ->
            PortForwardRow(
                config = pf,
                onConfigChange = { newPf ->
                    onPortForwardsChange(portForwards.toMutableList().apply { set(index, newPf) })
                },
                onDelete = {
                    onPortForwardsChange(portForwards.toMutableList().apply { removeAt(index) })
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                onPortForwardsChange(
                    portForwards + PortForwardConfig(proto = "tcp", bindPort = 65535, dstPort = 65535)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.config_port_forward_add))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortForwardRow(
    config: PortForwardConfig,
    onConfigChange: (PortForwardConfig) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.config_port_forwards), style = MiuixTheme.textStyles.body1)
                TextButton(text = stringResource(R.string.config_port_forward_delete), onClick = onDelete)
            }

            Spacer(Modifier.height(8.dp))

            // Protocol selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.config_port_forward_protocol), style = MiuixTheme.textStyles.body2)
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = config.proto == "tcp",
                        onClick = { onConfigChange(config.copy(proto = "tcp")) },
                    )
                    Text("TCP", style = MiuixTheme.textStyles.body2)
                }
                Spacer(Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = config.proto == "udp",
                        onClick = { onConfigChange(config.copy(proto = "udp")) },
                    )
                    Text("UDP", style = MiuixTheme.textStyles.body2)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bind address
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = config.bindIp,
                    onValueChange = { onConfigChange(config.copy(bindIp = it)) },
                    label = stringResource(R.string.config_port_forward_bind_ip),
                    modifier = Modifier.weight(1f),
                )
                TextField(
                    value = config.bindPort.toString(),
                    onValueChange = {
                        onConfigChange(config.copy(bindPort = it.toIntOrNull() ?: 0))
                    },
                    label = stringResource(R.string.config_port_forward_bind_port),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Destination address
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = config.dstIp,
                    onValueChange = { onConfigChange(config.copy(dstIp = it)) },
                    label = stringResource(R.string.config_port_forward_dst_ip),
                    modifier = Modifier.weight(1f),
                )
                TextField(
                    value = config.dstPort.toString(),
                    onValueChange = {
                        onConfigChange(config.copy(dstPort = it.toIntOrNull() ?: 0))
                    },
                    label = stringResource(R.string.config_port_forward_dst_port),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
