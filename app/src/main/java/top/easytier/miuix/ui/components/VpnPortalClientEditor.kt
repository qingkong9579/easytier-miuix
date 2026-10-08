package top.easytier.miuix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.easytier.miuix.data.model.VpnPortalClient
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `[[vpn_portal_config.clients]]` 编辑器。
 *
 * 上游已用「具名客户端」取代旧的 `client_cidr` 全局网段，所以这里收集名称与
 * 带前缀长的虚拟 IP（例如 `10.14.14.2/24`）。列表为空时只写 `wireguard_listen`
 * 也是合法的——门户仍会启动，只是需要客户端自行配置。
 */
@Composable
fun VpnPortalClientEditor(
    clients: List<VpnPortalClient>,
    onClientsChange: (List<VpnPortalClient>) -> Unit,
    modifier: Modifier = Modifier,
    errorFor: (Int) -> String? = { null },
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (clients.isEmpty()) {
            Text(
                text = stringResource(R.string.config_vpn_portal_clients_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
        }

        clients.forEachIndexed { index, client ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = client.name.ifEmpty {
                                stringResource(R.string.config_vpn_portal_client, index + 1)
                            },
                            style = MiuixTheme.textStyles.body1,
                        )
                        TextButton(
                            text = stringResource(R.string.config_port_forward_delete),
                            onClick = {
                                onClientsChange(clients.toMutableList().apply { removeAt(index) })
                            },
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    TextField(
                        value = client.name,
                        onValueChange = { newName ->
                            onClientsChange(
                                clients.toMutableList().apply {
                                    set(index, client.copy(name = newName))
                                }
                            )
                        },
                        label = stringResource(R.string.config_vpn_portal_client_name),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = client.virtualIp,
                        onValueChange = { newIp ->
                            onClientsChange(
                                clients.toMutableList().apply {
                                    set(index, client.copy(virtualIp = newIp))
                                }
                            )
                        },
                        label = stringResource(R.string.config_vpn_portal_client_ip),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorFor(index)?.let { message ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = message,
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { onClientsChange(clients + VpnPortalClient()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.config_vpn_portal_client_add))
        }
    }
}
