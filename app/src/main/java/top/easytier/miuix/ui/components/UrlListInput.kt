package top.easytier.miuix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UrlListInput(
    label: String,
    urls: List<String>,
    onUrlsChange: (List<String>) -> Unit,
    hint: String = "",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MiuixTheme.textStyles.body1)
            TextButton(
                text = "+ Add",
                onClick = { onUrlsChange(urls + "") },
            )
        }

        Spacer(Modifier.height(4.dp))

        urls.forEachIndexed { index, url ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = url,
                    onValueChange = { newUrl ->
                        val cleaned = newUrl.replace("\n", "").replace("\r", "").trim()
                        onUrlsChange(urls.toMutableList().apply { set(index, cleaned) })
                    },
                    label = hint,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                TextButton(
                    text = "×",
                    onClick = { onUrlsChange(urls.toMutableList().apply { removeAt(index) }) },
                )
            }
        }
    }
}
