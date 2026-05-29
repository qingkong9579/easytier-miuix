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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.easytier.miuix.R
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ModeSwitcherDialog(onDismiss: () -> Unit) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var rpcPort by remember { mutableStateOf("15999") }
    var remoteAddress by remember { mutableStateOf("tcp://127.0.0.1:15999") }

    OverlayDialog(
        title = stringResource(R.string.mode_switcher_title),
        show = true,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Mode selection
            val modes = listOf(
                stringResource(R.string.mode_normal),
                stringResource(R.string.mode_service),
                stringResource(R.string.mode_remote),
            )

            modes.forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = selectedMode == index,
                        onClick = { selectedMode = index },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MiuixTheme.textStyles.body1)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mode-specific fields
            when (selectedMode) {
                0 -> {
                    // Normal mode
                    TextField(
                        value = rpcPort,
                        onValueChange = { rpcPort = it },
                        label = "RPC Listen Port",
                    )
                }
                1 -> {
                    // Service mode
                    Text(
                        text = "Service mode manages EasyTier as a system service.",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                2 -> {
                    // Remote mode
                    TextField(
                        value = remoteAddress,
                        onValueChange = { remoteAddress = it },
                        label = "Remote RPC Address",
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.ok),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
