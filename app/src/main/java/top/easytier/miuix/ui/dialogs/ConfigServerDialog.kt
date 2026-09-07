package top.easytier.miuix.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配置服务器对话框：填写 easytier-web 配置服务器地址并连接。
 * 远程配置由核心在 FFI 层直接应用/删除，这里只负责 URL 管理与连接反馈。
 */
@Composable
fun ConfigServerDialog(
    currentUrl: String?,
    connected: Boolean,
    onConnect: (url: String, onResult: (String?) -> Unit) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl ?: "") }
    var connecting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    OverlayDialog(
        title = stringResource(R.string.config_server_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            TextField(
                value = url,
                onValueChange = {
                    url = it
                    errorText = null
                },
                label = stringResource(R.string.config_server_url_hint),
                enabled = !connecting,
            )
            if (currentUrl != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (connected) R.string.config_server_state_connected
                        else R.string.config_server_state_disconnected
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = if (connected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (errorText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorText ?: "",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !connecting,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = if (connecting) stringResource(R.string.config_server_connecting)
                    else stringResource(R.string.config_server_connect),
                    onClick = {
                        val trimmed = url.trim()
                        if (!isValidConfigServerUrl(trimmed)) {
                            errorText = "URL http(s):// or tcp://"
                            return@TextButton
                        }
                        connecting = true
                        onConnect(trimmed) { error ->
                            connecting = false
                            if (error == null) {
                                onDismiss()
                            } else {
                                errorText = error
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !connecting,
                )
            }
            if (currentUrl != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    text = stringResource(R.string.config_server_clear),
                    onClick = {
                        onDisconnect()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !connecting,
                )
            }
        }
    }
}

private fun isValidConfigServerUrl(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("tcp://")
}
