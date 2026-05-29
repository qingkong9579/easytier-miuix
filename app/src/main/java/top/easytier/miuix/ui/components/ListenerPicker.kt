package top.easytier.miuix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LISTENER_PRESETS = listOf(
    "tcp://0.0.0.0:11010",
    "udp://0.0.0.0:11010",
    "wg://0.0.0.0:11011",
    "ws://0.0.0.0:11010",
    "wss://0.0.0.0:11010",
)

@Composable
fun ListenerPicker(
    label: String,
    urls: List<String>,
    onUrlsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Preset dropdown — add with single tap
        val available = remember(urls) { LISTENER_PRESETS.filter { it !in urls } }
        if (available.isNotEmpty()) {
            var selectedIndex by remember { mutableIntStateOf(0) }
            OverlayDropdownPreference(
                title = label,
                items = available,
                selectedIndex = selectedIndex.coerceIn(0, available.lastIndex),
                onSelectedIndexChange = { index ->
                    if (index in available.indices) {
                        onUrlsChange(urls + available[index])
                        selectedIndex = 0
                    }
                },
            )
        }

        // Show added listeners below
        if (urls.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            urls.forEachIndexed { index, url ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp),
                    ) {
                        Text(
                            text = url,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    TextButton(
                        text = "×",
                        onClick = { onUrlsChange(urls.toMutableList().apply { removeAt(index) }) },
                    )
                }
            }
        }
    }
}
